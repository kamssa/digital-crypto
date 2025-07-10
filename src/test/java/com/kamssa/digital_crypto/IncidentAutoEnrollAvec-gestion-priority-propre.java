Absolument. Voici une version complète et propre du fichier IncidentAutoEnrollTask.java, intégrant toutes les bonnes pratiques discutées : refactoring, méthodes auxiliaires, constantes, logging amélioré, et la logique de gestion des priorités.
Ce code est une version reconstruite et améliorée basée sur tous les extraits que vous avez fournis. Il est prêt à être utilisé comme un exemple de code propre et maintenable.
Generated java
package com.bnpparibas.certis.api.tasks;

import //... Toutes vos importations nécessaires (DTOs, Services, etc.)

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class IncidentAutoEnrollTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentAutoEnrollTask.class);

    // --- Constantes pour la clarté et la maintenance ---
    private static final Integer PRIORITY_STANDARD = 3;
    private static final Integer PRIORITY_URGENT = 2;

    // Constantes pour les clés des templates d'email
    private static final String TEMPLATE_KEY_REPORT = "report";
    private static final String TEMPLATE_KEY_ERROR_REPORT = "errorReport";
    private static final String TEMPLATE_KEY_NO_SUPPORT_REPORT = "noSuppReport";
    private static final String TEMPLATE_KEY_CERTIFICATES = "automationHubCertificate";

    // --- Configuration injectée ---
    @Value("${autoenroll.trigger.days}")
    private int triggerDays;

    @Value("${autoenroll.trigger.daysplustrois}")
    private int triggerDaysPlusTrois; // Supposons que c'est pour la priorité 2

    @Value("${certis.mail.ipkiTeam}")
    private String ipkiTeam;

    // --- Services injectés ---
    @Autowired private AutomationHubService automationHubService;
    @Autowired private CertificateOwnerService certificateOwnerService;
    @Autowired private ReferenceRefiService referenceRefiService;
    @Autowired private SnowService snowService;
    @Autowired private ItemTaskService itemTaskService;
    @Autowired private SendMailUtils sendMailUtils;

    @Scheduled(cron = "${cron.task.incidentAutoEnroll}") // Utiliser une propriété pour le cron est une bonne pratique
    public void processExpiringCertificates() {
        LOGGER.info("Démarrage de la tâche de traitement de l'expiration des certificats.");
        Instant startTime = Instant.now();

        List<AutomationHubCertificateLightDto> expiringCertificates = automationHubService.searchAutoEnrollExpiring(triggerDays);
        LOGGER.info("Trouvé {} certificat(s) à traiter.", expiringCertificates != null ? expiringCertificates.size() : 0);

        if (expiringCertificates == null || expiringCertificates.isEmpty()) {
            LOGGER.info("Aucun certificat à traiter. Fin de la tâche.");
            return;
        }

        // Initialisation des compteurs et des listes de rapport
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger errorCounter = new AtomicInteger(0);
        List<String> report = new ArrayList<>();
        List<String> errorReport = new ArrayList<>();
        List<String> noSuppReport = new ArrayList<>();
        List<AutomationHubCertificateLightDto> certsSansCodeApp = new ArrayList<>();

        for (AutomationHubCertificateLightDto certificate : expiringCertificates) {
            try {
                // Étape 1 : Validation des données préliminaires
                ReferenceRefiDto referenceRefiDto = referenceRefiService.findReferenceByCodeAp(certificate.getCodeAp());
                if (referenceRefiDto == null) {
                    LOGGER.warn("Ignoré : Code AP manquant pour le certificat {}.", certificate.getCommonName());
                    certsSansCodeApp.add(certificate);
                    continue;
                }

                // Étape 2 : Détermination de la priorité
                Integer priority = isExpiringUrgently(certificate) ? PRIORITY_URGENT : PRIORITY_STANDARD;

                // Étape 3 : Création/Mise à jour de l'incident
                createOrUpdateIncident(certificate, referenceRefiDto, priority, successCounter, errorCounter, report, errorReport, noSuppReport);

            } catch (Exception e) {
                LOGGER.error("Erreur inattendue lors du traitement du certificat {} : {}", certificate.getCommonName(), e.getMessage(), e);
                errorCounter.incrementAndGet();
                errorReport.add("Erreur système pour " + certificate.getCommonName() + ": " + e.getMessage());
            }
        }

        // Étape 4 : Envoi des rapports par email
        sendFinalReport(successCounter.get(), errorCounter.get(), report, errorReport, noSuppReport);
        sendReportForCertsWithoutCodeApp(certsSansCodeApp);

        Duration duration = Duration.between(startTime, Instant.now());
        LOGGER.info("FIN de la tâche. Durée d'exécution totale : {} ms. [Succès: {}, Erreurs: {}]", 
                    duration.toMillis(), successCounter.get(), errorCounter.get());
    }

    private void createOrUpdateIncident(AutomationHubCertificateLightDto certificate, ReferenceRefiDto referenceRefiDto, Integer priority,
                                        AtomicInteger successCounter, AtomicInteger errorCounter,
                                        List<String> report, List<String> errorReport, List<String> noSuppReport) {

        String summary = String.format("AutoEnroll pour %s (ID: %s)", certificate.getCommonName(), certificate.getAutomationHubId());
        
        try {
            SnowIncidentDto existingIncident = snowService.getSnowIncidentBySysId(certificate.getAutomationHubId());
            AutoItsmTaskDto itsmTaskDto = null;

            if (existingIncident == null) {
                LOGGER.info("Aucun incident existant. Création d'un nouvel incident avec priorité {}.", priority);
                itsmTaskDto = itemTaskService.createIncidentAutoEnroll(certificate, referenceRefiDto, priority, IncidentTypeEnum.AUTOENROLL, null);
            
            } else {
                LOGGER.info("Incident précédent trouvé : {} (État: {}, Priorité: {})", existingIncident.getNumber(), existingIncident.getState(), existingIncident.getPriority());

                if (isIncidentClosedOrResolved(existingIncident)) {
                    LOGGER.info("L'incident {} est clos. Recréation avec priorité {}.", existingIncident.getNumber(), priority);
                    itsmTaskDto = itemTaskService.createIncidentAutoEnroll(certificate, referenceRefiDto, priority, IncidentTypeEnum.AUTOENROLL, null); // ou une méthode recreate si elle existe

                } else if (priority.equals(PRIORITY_URGENT) && isIncidentHighPriority(existingIncident, PRIORITY_URGENT)) {
                    LOGGER.warn("L'incident {} est déjà en haute priorité ({}). Aucune action pour la demande P2.", existingIncident.getNumber(), existingIncident.getPriority());
                    report.add(summary + " - Aucune action (incident " + existingIncident.getNumber() + " déjà P" + existingIncident.getPriority() + ")");
                
                } else {
                    LOGGER.info("Mise à jour de l'incident {} vers la priorité {}.", existingIncident.getNumber(), priority);
                    itsmTaskDto = itemTaskService.updateIncident(existingIncident, certificate, priority); // Supposons qu'une telle méthode existe
                }
            }

            if (itsmTaskDto != null) {
                successCounter.incrementAndGet();
                report.add(summary + " -> Incident " + itsmTaskDto.getIncidentId() + " créé/mis à jour.");
            }

        } catch (NoSupportGroupException e) {
            LOGGER.error("Aucun groupe de support trouvé pour l'automationHubId: {}. {}", certificate.getAutomationHubId(), e.getMessage());
            errorCounter.incrementAndGet();
            noSuppReport.add(summary);
        } catch (Exception e) {
            LOGGER.error("Échec du traitement de l'incident pour {}: {}", summary, e.getMessage(), e);
            errorCounter.incrementAndGet();
            errorReport.add(summary + " - Échec création/màj : " + e.getMessage());
        }
    }

    // --- Méthodes Auxiliaires ---

    private boolean isExpiringUrgently(AutomationHubCertificateLightDto certificate) {
        // La logique exacte dépend de votre besoin, ceci est un exemple.
        // On suppose que `triggerDaysPlusTrois` est pour les cas urgents.
        return certificate.getExpiryDate().compareTo(DateUtils.addDays(new Date(), triggerDaysPlusTrois)) <= 0;
    }

    private boolean isIncidentClosedOrResolved(SnowIncidentDto incident) {
        if (incident == null || incident.getState() == null) return false;
        try {
            int state = Integer.parseInt(incident.getState());
            // Supposons que SnowIncStateEnum.RESOLVED.getValue() est la valeur numérique de l'état résolu (ex: 6)
            return state >= SnowIncStateEnum.RESOLVED.getValue(); 
        } catch (NumberFormatException e) {
            LOGGER.error("État de l'incident non numérique : {}", incident.getState());
            return false;
        }
    }

    private boolean isIncidentHighPriority(SnowIncidentDto incident, int priorityThreshold) {
        if (incident == null || incident.getPriority() == null) return false;
        try {
            int priority = Integer.parseInt(incident.getPriority());
            return priority <= priorityThreshold;
        } catch (NumberFormatException e) {
            LOGGER.error("Priorité de l'incident non numérique : {}", incident.getPriority());
            return false;
        }
    }

    // --- Méthodes d'envoi d'e-mails ---

    private void sendFinalReport(int successCount, int errorCount, List<String> report, List<String> errorReport, List<String> noSuppReport) {
        if (successCount == 0 && errorCount == 0) {
            LOGGER.info("Traitement terminé. Aucune action à rapporter.");
            return;
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put(TEMPLATE_KEY_REPORT, report);
        data.put(TEMPLATE_KEY_ERROR_REPORT, errorReport);
        data.put(TEMPLATE_KEY_NO_SUPPORT_REPORT, noSuppReport);

        try {
            List<String> toList = new ArrayList<>();
            toList.add(ipkiTeam);
            sendMailUtils.sendEmail("/template/report-incident-p3.vm", data, toList, "Report | Incident P3"); // Nom du template à adapter
            LOGGER.info("Rapport final envoyé à l'équipe IPKI.");
        } catch (Exception e) {
            LOGGER.error("Échec de l'envoi de l'e-mail de synthèse.", e);
        }
    }

    private void sendReportForCertsWithoutCodeApp(List<AutomationHubCertificateLightDto> certs) {
        if (certs == null || certs.isEmpty()) {
            LOGGER.info("Aucun certificat orphelin trouvé, l'email d'alerte ne sera pas envoyé.");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put(TEMPLATE_KEY_CERTIFICATES, certs);
        
        try {
            List<String> toList = new ArrayList<>();
            toList.add(ipkiTeam);
            sendMailUtils.sendEmail("/template/report-incident-sans-codeap.vm", data, toList, "Alerte | Certificats sans CodeAP"); // Template à créer/adapter
            LOGGER.info("Rapport pour les certificats sans CodeAP envoyé.");
        } catch (Exception e) {
            LOGGER.error("Échec de l'envoi de l'e-mail pour les certificats sans CodeAP.", e);
        }
    }
}
Use code with caution.
Java
Points Clés de cette Version Propre :
Structure Claire : La méthode principale processExpiringCertificates est un chef d'orchestre. Elle récupère les données, puis délègue le traitement de chaque certificat à createOrUpdateIncident.
Constantes : L'utilisation de constantes (PRIORITY_URGENT, TEMPLATE_KEY_REPORT) évite les "valeurs magiques" et facilite la maintenance.
Paramètres de Priorité : La priorité est déterminée une seule fois et passée en paramètre, ce qui rend la logique de création/mise à jour flexible et réutilisable.
Méthodes Auxiliaires Spécifiques : Les méthodes comme isIncidentClosedOrResolved ont un seul rôle et un nom explicite. Elles rendent le code principal beaucoup plus lisible.
Gestion des Erreurs Robuste : Chaque certificat est traité dans un bloc try-catch. Une erreur sur un certificat ne fait pas planter toute la tâche. La gestion de la NoSupportGroupException est spécifique.
Rapports par Email Séparés : Les méthodes d'envoi d'email sont isolées et appelées à la fin, ce qui clarifie le flux.
Logging Précis : Les messages de log sont paramétrés et fournissent un contexte utile pour le débogage.
Ce code représente une base solide et professionnelle pour votre tâche. Vous devrez peut-être ajuster les noms des méthodes de service (itemTaskService.updateIncident, par exemple) pour qu'ils correspondent exactement à votre code existant.