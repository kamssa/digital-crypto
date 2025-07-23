package com.bnpparibas.certis.api.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Classe utilitaire GÉNÉRIQUE et réutilisable qui agit comme un conteneur
 * pour stocker l'état et les résultats d'un traitement par lot. Elle ne
 * requiert aucune modification des DTOs qu'elle traite.
 *
 * @param <T> Le type de l'objet principal traité par la tâche.
 */
public class ProcessingContext<T> {

    // --- Champs ---
    private final AtomicInteger successCounter = new AtomicInteger(0);
    private final AtomicInteger errorCounter = new AtomicInteger(0);
    
    private final List<Map<String, Object>> successReportItems = new ArrayList<>();
    private final List<Map<String, Object>> errorReportItems = new ArrayList<>();
    
    private final List<T> itemsWithValidationError = new ArrayList<>();

    private final Function<T, Map<String, Object>> reportDataMapper;

    /**
     * Constructeur qui accepte une fonction de mapping.
     * @param reportDataMapper une fonction qui prend un objet de type T et retourne
     *                         une Map<String, Object> pour les rapports.
     */
    public ProcessingContext(Function<T, Map<String, Object>> reportDataMapper) {
        if (reportDataMapper == null) {
            throw new IllegalArgumentException("Report data mapper function cannot be null.");
        }
        this.reportDataMapper = reportDataMapper;
    }

    // --- Méthodes Publiques ---

    public boolean hasItemsForFinalReport() {
        return successCounter.get() > 0 || errorCounter.get() > 0;
    }

    public void recordSuccess(String actionMessage, T item, Map<String, Object> additionalData) {
        successCounter.incrementAndGet();
        
        Map<String, Object> reportData = new HashMap<>(this.reportDataMapper.apply(item));
        reportData.put("actionMessage", actionMessage);
        
        if (additionalData != null) {
            reportData.putAll(additionalData);
        }
        successReportItems.add(reportData);
    }

    public void recordError(String actionMessage, String errorMessage, T item, Map<String, Object> additionalData) {
        errorCounter.incrementAndGet();
        
        Map<String, Object> reportData = (item != null) ? new HashMap<>(this.reportDataMapper.apply(item)) : new HashMap<>();
        reportData.put("actionMessage", actionMessage);
        reportData.put("errorMessage", errorMessage);
        
        if (additionalData != null) {
            reportData.putAll(additionalData);
        }
        errorReportItems.add(reportData);
    }
    
    public void recordValidationError(T item) {
        itemsWithValidationError.add(item);
    }

    // --- Getters ---
    public AtomicInteger getSuccessCounter() { return successCounter; }
    public AtomicInteger getErrorCounter() { return errorCounter; }
    public List<Map<String, Object>> getSuccessReportItems() { return successReportItems; }
    public List<Map<String, Object>> getErrorReportItems() { return errorReportItems; }
    public List<T> getItemsWithValidationError() { return itemsWithValidationError; }
}
Use code with caution.
Java
2. Extraits Clés de IncidentAutoEnrollTask.java
Ce code montre comment initialiser et utiliser le contexte.
Generated java
// Dans IncidentAutoEnrollTask.java

@Component
public class IncidentAutoEnrollTask {
    // ... (dépendances)

    @Scheduled(cron = "${cron.task.incidentAutoEnroll:0 30 9 * * *}")
    public void processExpireCertificates() {
        LOGGER.info("Démarrage de la tâche de traitement de l'expiration des certificats.");
        Instant startTime = Instant.now();
        
        // Définition de la fonction de mapping pour les certificats
        Function<AutomationHubCertificateLightDto, Map<String, Object>> certificateMapper = cert -> {
            Map<String, Object> data = new HashMap<>();
            if (cert != null) {
                data.put("automationHubId", cert.getAutomationHubId());
                data.put("commonName", cert.getCommonName());
                data.put("expiryDate", formatDate(cert.getExpiryDate())); // Utilise une méthode helper
                data.put("codeAp", getLabelByKey(cert, "APCode")); // Utilise une méthode helper
            }
            return data;
        };
        
        ProcessingContext<AutomationHubCertificateLightDto> context = new ProcessingContext<>(certificateMapper);

        // ... (try-catch, boucle, etc.)
        
        finally {
            this.sendSummaryReports(context);
            // ... (log de la durée)
        }
    }

    // --- Exemple d'utilisation dans une méthode d'action ---
    
    private AutoItsmTaskDto createNewInc(AutomationHubCertificateLightDto dto, ReferenceRefiDto referenceRefiDto, IncidentPriority priority, ..., ProcessingContext<AutomationHubCertificateLightDto> context) {
        try {
            AutoItsmTaskDto autoItsmTaskDto = itsmTaskService.createIncidentAutoEnroll(...);
            
            // Enregistrement du succès
            Map<String, Object> successDetails = new HashMap<>();
            successDetails.put("supportGroup", referenceRefiDto.getGroupSupportDto().getName());
            successDetails.put("priority", priority.name());
            successDetails.put("incidentNumber", autoItsmTaskDto.getItsmId());
            context.recordSuccess("Création INC", dto, successDetails);
            
            return autoItsmTaskDto;

        } catch (NoSupportGroupException e) {
            // Enregistrement de l'échec
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("supportGroup", "N/A");
            errorDetails.put("priority", priority.name());
            context.recordError("Création INC", e.getMessage(), dto, errorDetails);
            
            LOGGER.error(...);
            return null;
        }
    }

    // --- Méthode d'envoi de l'e-mail ---
    
    private void sendFinalReport(ProcessingContext<AutomationHubCertificateLightDto> context) {
        if (!context.hasItemsForFinalReport()) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("successItems", context.getSuccessReportItems());
        data.put("errorItems", context.getErrorReportItems());
        data.put("date", new Date());
        
        try {
            sendMailUtils.sendEmail("template/report-incident-p3.vm", data, ...);
            LOGGER.info(...);
        } catch (Exception e) {
            LOGGER.error(...);
        }
    }

    // --- Méthodes d'aide pour le mapping (helpers) ---

    private String getLabelByKey(AutomationHubCertificateLightDto dto, String key) {
        if (dto == null || dto.getLabels() == null || key == null) return "N/A";
        return dto.getLabels().stream()
                  .filter(label -> key.equalsIgnoreCase(label.getKey()))
                  .map(CertificateLabelDto::getValue)
                  .findFirst()
                  .orElse("N/A");
    }

    private String formatDate(Date date) {
        if (date == null) return "N/A";
        return date.toInstant()
                   .atZone(ZoneId.systemDefault())
                   .toLocalDate()
                   .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
/////////////////////////////////////////////
private void sendAutoEnrollCertificateNoCodeApReport(ProcessingContext<AutomationHubCertificateLightDto> context) {
    
    // On récupère la liste des certificats qui ont échoué à l'étape de validation du propriétaire.
    List<AutomationHubCertificateLightDto> certsInError = context.getItemsWithValidationError();

    // Condition de garde : s'il n'y a aucun certificat dans cette liste, on ne fait rien.
    if (certsInError == null || certsInError.isEmpty()) {
        LOGGER.info("Rapport 'sans codeAp' non envoyé : aucune erreur de validation à signaler.");
        return;
    }

    LOGGER.info("Préparation du rapport pour {} certificat(s) nécessitant une action manuelle.", certsInError.size());

    // Préparation des destinataires et des données pour le template
    List<String> toList = new ArrayList<>();
    toList.add(ipkiTeam);
    
    Map<String, Object> data = new HashMap<>();
    data.put("certsWithoutCodeAp", certsInError);
    data.put("date", new Date());

    // Envoi de l'e-mail
    try {
        sendMailUtils.sendEmail(
            "template/report-no-codeap.vm", // Template dédié pour ce rapport
            data, 
            toList, 
            "ALERTE : Action Manuelle Requise - Certificats sans Propriétaire Valide" // Sujet clair et alarmant
        );
        LOGGER.info("Rapport pour les certificats sans propriétaire valide envoyé avec succès.");
    } catch (Exception e) {
        LOGGER.error("Échec critique de l'envoi de l'e-mail pour les certificats sans propriétaire valide.", e);
    }
}
