package com.bnpparibas.certis.api.tasks;

import // ... (les imports nécessaires)

/**
 * Tâche planifiée pour la gestion automatique des incidents (création/mise à jour)
 * pour les certificats en cours d'expiration (Auto-Enroll).
 */
@Component
public class IncidentAutoEnrollTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentAutoEnrollTask.class);

    // --- Dépendances injectées par Spring ---
    @Autowired
    private ItsmTaskService itsmTaskService;
    @Autowired
    private CertificateOwnerService certificateOwnerService;
    @Autowired
    private SendMailUtils sendMailUtils;
    @Autowired
    private AutomationHubService automationHubService;
    @Autowired
    private ReferenceRefiService referenceRefiService; // (hypothèse basée sur l'usage)
    @Autowired
    private SnowService snowService;

    // --- Configuration externe ---
    @Value("${autoenroll.trigger.days:15}")
    private int triggerDays;

    @Value("${autoenroll.trigger.daysplustrois}")
    private int triggerDaysplustrois; // Nom de variable à améliorer

    @Value("${snoe.incident.upgrade.trigger:3}")
    private int snowIncidentUpgradeTrigger; // Nom de variable à améliorer

    @Value("${certis.mail.ipkiTeam}")
    private String ipkiTeam;

    // --- Constantes (à améliorer avec un Enum) ---
    private static Integer PRIORITY3 = 3;
    private static Integer PRIORITY2 = 2;

    /**
     * Point d'entrée de la tâche, déclenchée par une expression CRON.
     * S'exécute tous les jours à 9h30 du matin.
     */
    @Scheduled(cron = "0 30 9 * * *")
    public void processExpireCertificates() {
        LOGGER.info("Démarrage de la tâche de traitement de l'expiration des certificats.");
        Instant startTime = Instant.now();
        IncidentProcessingContext context = new IncidentProcessingContext();

        try {
            List<AutomationHubCertificateLightDto> certificates = automationHubService.searchAutoEnrollExpiring(triggerDays);

            if (certificates == null || certificates.isEmpty()) {
                LOGGER.info("Aucun certificat à traiter pour cette exécution.");
            } else {
                LOGGER.info("{} certificat(s) trouvé(s) à traiter.", certificates.size());
                this.processAllCertificates(certificates, context);
            }
        } catch (Exception e) {
            LOGGER.error("La tâche de traitement a échoué de manière inattendue. Impossible de continuer.", e);
            throw new RuntimeException("Echec critique de la tâche planifiée processExpireCertificates", e);
        } finally {
            this.sendSummaryReports(context);
            Duration duration = Duration.between(startTime, Instant.now());
            LOGGER.info("Fin de la tâche de traitement. Temps d'exécution total : {} ms.", duration.toMillis());
        }
    }

    private void processAllCertificates(List<AutomationHubCertificateLightDto> certificates, IncidentProcessingContext context) {
        for (AutomationHubCertificateLightDto certificate : certificates) {
            try {
                this.processSingleCertificate(certificate, context);
            } catch (Exception e) {
                LOGGER.error("Erreur non gérée lors du traitement du certificat ID: {}. {}", certificate.getAutomationHubId(), e.getMessage());
                context.recordError("Erreur système inattendue", null, certificate);
            }
        }
    }

    private void processSingleCertificate(AutomationHubCertificateLightDto certificate, IncidentProcessingContext context) throws Exception { // (throws déduit)
        String environment = getLabelByKey(certificate, "ENVIRONMENT");
        String codeAp = this.getLabelByKey(certificate, LabelEnum.AUD_GETVALUE.toString());

        if (environment != null && !environment.trim().isEmpty() && !"PROD".equalsIgnoreCase(environment.trim())) {
            return; // On ne traite que la PROD
        }

        OwnerAndReferenceRefiResult ownerAndReferenceRefiResult = certificateOwnerService.findBestAvailableCertificateOwner(certificate, codeAp);

        if (ownerAndReferenceRefiResult == null || ownerAndReferenceRefiResult.getOwnerDTO() == null) {
            LOGGER.warn("Le traitement du certificat (ID: {}) est annulé car son codeAp '{}' n'a pas pu être validé.", certificate.getAutomationHubId(), codeAp);
            context.getCertsWithoutCodeAp().add(certificate);
            return;
        }
        
        ReferenceRefiDto referenceRefiDto = ownerAndReferenceRefiResult.getReferenceRefiDto();
        boolean isExpiringUrgently = certificate.getExpiryDate().compareTo(DateUtils.addDays(new Date(), triggerDaysplustrois)) <= 0;
        IncidentPriority priority = isExpiringUrgently ? IncidentPriority.URGENT : IncidentPriority.STANDARD;
        
        createOrUpdateIncident(certificate, referenceRefiDto, priority, context);
    }
    
    private void createOrUpdateIncident(AutomationHubCertificateLightDto dto, ReferenceRefiDto referenceRefiDto, IncidentPriority priority, IncidentProcessingContext context) {
        // Recherche d'un incident existant
        List<AutoItsmTaskDtoImpl> existingTasks = itsmTaskService.findByAutomationHubIdAndStatusAndTypeAndCreationDate(dto.getAutomationHubId(), null, InciTypeEnum.AUTOENROLL, null);
        AutoItsmTaskDtoImpl existingTask = itsmTaskService.getIncNumberByAutoItsmTaskList(existingTasks);
        SnowIncidentReadResponseDto snowIncident = existingTask == null ? null : snowService.getIncidentBySysId(existingTask.getItsmId());

        String summary = dto.getAutomationHubId() + " : " + (existingTask == null ? "None" : existingTask.getItsmId()) + " : " + dto.getCommonName();
        String warningInfo = dto.getAutomationHubId() + " : " + dto.getCommonName();
        ArrayList<String> noUpdateReport = new ArrayList<>();

        // Logique de décision principale
        if (snowIncident == null) {
            String actionMessage = "Création INC " + priority.name();
            LOGGER.info("Aucun INC actif pour {}. {}.", dto.getAutomationHubId(), actionMessage);
            this.createNewInc(dto, referenceRefiDto, priority, summary, warningInfo, actionMessage, context);
        } else if (isIncidentResolved(snowIncident)) {
            String actionMessage = "Recréation INC " + priority.name();
            LOGGER.info("L'INC précédent {} est résolu. {}.", snowIncident.getNumber(), actionMessage);
            this.recreateInc(dto, referenceRefiDto, priority, summary, warningInfo, actionMessage, context, existingTask);
        } else if (Integer.parseInt(snowIncident.getPriority()) <= 2) {
            noUpdateReport.add(summary);
            LOGGER.info("Previous INC {} for this request is already at least P2 ({}). NOTHING TO DO...", snowIncident.getNumber(), snowIncident.getPriority());
        } else {
            String actionMessage = "Mise à jour INC vers " + priority.name();
            LOGGER.info("L'INC existant {} est ouvert. {}.", snowIncident.getNumber(), actionMessage);
            this.updateInc(priority, actionMessage, context, existingTask);
        }
    }

    private AutoItsmTaskDto createNewInc(AutomationHubCertificateLightDto dto, ReferenceRefiDto refiDto, IncidentPriority priority, String summary, String warningInfo, String actionMessage, IncidentProcessingContext context) {
        AutoItsmTaskDto autoItsmTaskDto = null;
        try {
            autoItsmTaskDto = itsmTaskService.createIncidentAutoEnroll(dto, refiDto, priority, summary, warningInfo, InciTypeEnum.AUTOENROLL, null);
            String successDetails = "..."; // (détails du succès)
            context.recordSuccess(actionMessage, successDetails, dto, null);
        } catch (NoSupportGroupException e) {
            String errorMessage = "Support group not found for certificate";
            context.recordError(actionMessage, errorMessage, dto, null);
            LOGGER.error("Support group not found for certificate {}. {}", dto.getAutomationHubId(), e.getMessage());
            return null;
        } catch (CreateIncidentException e) {
            String errorMessage = "Incident création returned null.";
            context.recordError(actionMessage, errorMessage, dto, null);
            LOGGER.error("Incident null for {}: {}. {}", dto.getAutomationHubId(), errorMessage, e.getMessage());
            return null;
        }
        return autoItsmTaskDto;
    }
    
    // La méthode recreateInc est très similaire à createNewInc
    private AutoItsmTaskDto recreateInc(AutomationHubCertificateLightDto dto, ReferenceRefiDto refiDto, IncidentPriority priority, String summary, String warningInfo, String actionMessage, IncidentProcessingContext context, AutoItsmTaskDtoImpl relatedAutoItsmTaskDto) {
        return createInc(dto, refiDto, priority, summary, warningInfo, actionMessage, context, relatedAutoItsmTaskDto);
    }
    
    private AutoItsmTaskDto updateInc(IncidentPriority priority, String actionMessage, IncidentProcessingContext context, AutoItsmTaskDtoImpl lastItsmTaskDto) {
        AutoItsmTaskDto autoItsmTaskDto = null;
        try {
            autoItsmTaskDto = itsmTaskService.upgradeIncidentAutoEnroll(priority.getValue(), lastItsmTaskDto);
            String successDetails = String.format("Incident %s mis à jour avec succès vers la priorité %s.", lastItsmTaskDto.getItsmId(), priority.name());
            context.recordSuccess(actionMessage, successDetails, null, lastItsmTaskDto);
        } catch (UpdateIncidentException e) {
            String errorMessage = String.format("Failed to update incident to priority %s.", priority.name());
            context.recordError(actionMessage, errorMessage, null, lastItsmTaskDto);
            LOGGER.error("Failed to update INC. Incident not upgraded. {}", lastItsmTaskDto.getAutomationHubId());
            return null;
        }
        return autoItsmTaskDto;
    }
    
    private void sendSummaryReports(IncidentProcessingContext context) {
        sendFinalReport(context);
        sendAutoEnrollCertificateNoCodeApReport(context);
    }

    private void sendFinalReport(IncidentProcessingContext context) {
        if (context.hasNoItemsForFinalReport()) {
            LOGGER.info("Rapport final non envoyé : aucune action de création ou d'erreur à signaler.");
            return;
        }
        List<String> toList = new ArrayList<>();
        toList.add(ipkiTeam);
        Map<String, Object> data = new HashMap<>();
        data.put("report", context.getSuccessReport());
        data.put("errorReport", context.getErrorReport());
        data.put("date", new Date());
        try {
            sendMailUtils.sendEmail("template/report-incident-p3.vm", data, toList, "Rapport");
            LOGGER.info("Rapport final envoyé avec {} succès et {} erreurs.", context.getSuccessCounter().get(), context.getErrorCounter().get());
        } catch (Exception e) {
            LOGGER.error("Echec de l'envoi de l'e-mail de synthèse.", e);
        }
    }

    private void sendAutoEnrollCertificateNoCodeApReport(IncidentProcessingContext context) {
        if (context.getCertsWithoutCodeAp().isEmpty() && context.getNoSuppReport().isEmpty()) {
            return;
        }
        List<String> toList = new ArrayList<>();
        toList.add(ipkiTeam);
        Map<String, Object> data = new HashMap<>();
        data.put("digsSansCodeApp", context.getCertsWithoutCodeAp());
        data.put("noSuppReport", context.getNoSuppReport());
        data.put("date", new Date());
        try {
            sendMailUtils.sendEmail("template/report-incident-p3.vm", data, toList, "Report | Certificats n'ayant pas de codeAP valide");
            LOGGER.info("Rapport pour certificats sans codeAp envoyé.");
        } catch (Exception e) {
            LOGGER.error("Echec de l'envoi de l'e-mail pour les certificats sans codeAp.", e);
        }
    }
    
    private String getLabelByKey(AutomationHubCertificateLightDto dto, String key) {
        if (dto == null || dto.getLabels() == null || dto.getLabels().isEmpty()) return null;
        for (CertificateLabelDto label : dto.getLabels()) {
            if (key.equalsIgnoreCase(label.getKey())) return label.getValue();
        }
        return null;
    }

    private boolean isIncidentResolved(SnowIncidentReadResponseDto snowIncident) {
        return snowIncident != null && Integer.parseInt(snowIncident.getState()) == SnowIncStateEnum.RESOLVED.getValue();
    }
}
///////// explication detaillée /////////////////////////////////////////////////
Oui, absolument. Le code est conçu pour décider de créer un incident de priorité STANDARD dans des conditions bien précises.
Pour qu'un incident STANDARD soit créé, deux conditions principales doivent être remplies simultanément :
Le certificat est en cours d'expiration mais n'a pas encore atteint le seuil d'urgence.
Il n'existe aucun incident actif déjà ouvert pour ce certificat.
Voyons le parcours complet dans le code qui mène à cette décision.
Le Parcours Logique pour Créer un Incident STANDARD
Étape 1 : Détermination de la Priorité (dans processSingleCertificate)
Tout d'abord, le système calcule si la situation est urgente ou non. Cette logique se trouve juste avant l'appel à createOrUpdateIncident.
Generated java
// On vérifie si la date d'expiration est DANS le seuil d'urgence (ex: moins de 10 jours).
boolean isExpiringUrgently = certificate.getExpiryDate().compareTo(DateUtils.addDays(new Date(), triggerdaysplustrois)) <= 0;

// On détermine la priorité en fonction du résultat.
IncidentPriority priority = isExpiringUrgently ? IncidentPriority.URGENT : IncidentPriority.STANDARD;
Use code with caution.
Java
Pour que nous décidions de créer un incident STANDARD, il faut que la condition isExpiringUrgently soit FAUSSE.
Cela signifie que la date d'expiration du certificat est plus lointaine que le seuil d'urgence.
Exemple :
Le seuil d'urgence (triggerdaysplustrois) est de 10 jours.
Le certificat expire dans 25 jours.
isExpiringUrgently sera false.
La variable priority sera donc initialisée à IncidentPriority.STANDARD.
Étape 2 : Décision de Création (dans createOrUpdateIncident)
Maintenant que nous avons notre priorité (STANDARD), nous entrons dans la méthode createOrUpdateIncident. La première chose que fait cette méthode est de chercher un incident existant.
Generated java
SnowIncidentReadResponseDto snowIncident = ... // Recherche d'un incident existant
Use code with caution.
Java
Ensuite, la logique de décision commence. Le tout premier bloc if est celui qui nous intéresse :
Generated java
if (snowIncident == null) {
    // ---- ON ENTRE ICI ----

    // Le message d'action sera "Création INC STANDARD"
    String actionMessage = "Création INC " + priority.name(); 
    LOGGER.info("Aucun INC actif pour {}. {}.", dto.getAutomationHubId(), actionMessage);
    
    // On appelle la méthode de création en lui passant la priorité STANDARD
    this.createNewInc(dto, referenceRefiDto, priority, summary, warningInfo, actionMessage, context);
} 
// ... les autres else if ne seront pas exécutés.
Use code with caution.
Java
Pour créer un nouvel incident, la condition snowIncident == null doit être VRAIE. Cela signifie que l'appel au service ITSM n'a retourné aucun incident actif pour ce certificat.
Résumé de la Décision
Le système décide de créer un nouvel incident STANDARD lorsque la séquence suivante se produit :
La date d'expiration du certificat est vérifiée.
Elle est jugée "non urgente" (plus lointaine que le seuil critique).
La priorité du traitement est donc définie à STANDARD.
Le système vérifie s'il existe déjà un incident pour ce certificat.
Il n'en trouve aucun (snowIncident est null).
Action : Le système appelle createNewInc avec la priorité STANDARD pour créer un tout nouvel incident de suivi.
Cette logique est très saine : elle garantit que chaque certificat entrant dans la fenêtre de surveillance est immédiatement tracé par un incident, avec une priorité qui reflète correctement son niveau d'urgence actuel.