
Absolument. Voici le code complet et propre du fichier IncidentAutoEnrollTask.java qui intègre le "feature flag" pour activer ou désactiver la création d'incidents.
Ce code est une version finale, consolidant toutes nos discussions précédentes :
Structure claire et refactorisée.
Utilisation de constantes.
Logique de gestion des priorités P2/P3.
Méthodes auxiliaires pour la lisibilité.
Et maintenant, le drapeau de fonctionnalité isIncidentCreationActive.
Fichier : src/main/resources/application.properties
Assurez-vous que cette ligne est présente dans votre fichier de configuration :
Generated properties
# Active ou désactive la création/mise à jour d'incidents.
# Si false, la tâche s'exécutera en mode "dry run" (simulation).
incident.autoenroll.creation.active=true
Use code with caution.
Properties
Fichier : src/main/java/com/bnpparibas/certis/api/tasks/IncidentAutoEnrollTask.java
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
import java.util.Date;
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

    private static final String TEMPLATE_KEY_REPORT = "report";
    private static final String TEMPLATE_KEY_ERROR_REPORT = "errorReport";
    private static final String TEMPLATE_KEY_NO_SUPPORT_REPORT = "noSuppReport";
    private static final String TEMPLATE_KEY_CERTIFICATES = "automationHubCertificate";

    // --- Configuration injectée ---
    @Value("${autoenroll.trigger.days}")
    private int triggerDays;
    @Value("${autoenroll.trigger.daysplustrois}")
    private int triggerDaysPlusTrois;
    @Value("${certis.mail.ipkiTeam}")
    private String ipkiTeam;
    @Value("${cron.task.incidentAutoEnroll}")
    private String cronExpression;

    // --- FEATURE FLAG INJECTÉ ---
    @Value("${incident.autoenroll.creation.active}")
    private boolean isIncidentCreationActive;

    // --- Services injectés ---
    @Autowired private AutomationHubService automationHubService;
    @Autowired private CertificateOwnerService certificateOwnerService;
    @Autowired private ReferenceRefiService referenceRefiService;
    @Autowired private SnowService snowService;
    @Autowired private ItemTaskService itemTaskService;
    @Autowired private SendMailUtils sendMailUtils;

    @Scheduled(cron = "${cron.task.incidentAutoEnroll}")
    public void processExpiringCertificates() {
        LOGGER.info("Démarrage de la tâche de traitement de l'expiration des certificats. [Création d'incidents active: {}]", isIncidentCreationActive);
        Instant startTime = Instant.now();

        List<AutomationHubCertificateLightDto> expiringCertificates = automationHubService.searchAutoEnrollExpiring(triggerDays);
        LOGGER.info("Trouvé {} certificat(s) à traiter.", expiringCertificates != null ? expiringCertificates.size() : 0);

        if (expiringCertificates == null || expiringCertificates.isEmpty()) {
            LOGGER.info("Aucun certificat à traiter. Fin de la tâche.");
            return;
        }

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger errorCounter = new AtomicInteger(0);
        List<String> report = new ArrayList<>();
        List<String> errorReport = new ArrayList<>();
        List<String> noSuppReport = new ArrayList<>();
        List<AutomationHubCertificateLightDto> certsSansCodeApp = new ArrayList<>();

        for (AutomationHubCertificateLightDto certificate : expiringCertificates) {
            try {
                ReferenceRefiDto referenceRefiDto = referenceRefiService.findReferenceByCodeAp(certificate.getCodeAp());
                if (referenceRefiDto == null) {
                    LOGGER.warn("Ignoré : Code AP manquant pour le certificat {}.", certificate.getCommonName());
                    certsSansCodeApp.add(certificate);
                    continue;
                }

                Integer priority = isExpiringUrgently(certificate) ? PRIORITY_URGENT : PRIORITY_STANDARD;

                createOrUpdateIncident(certificate, referenceRefiDto, priority, successCounter, errorCounter, report, errorReport, noSuppReport);

            } catch (Exception e) {
                LOGGER.error("Erreur inattendue lors du traitement du certificat {} : {}", certificate.getCommonName(), e.getMessage(), e);
                errorCounter.incrementAndGet();
                errorReport.add("Erreur système pour " + certificate.getCommonName() + ": " + e.getMessage());
            }
        }

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

            if (!isIncidentCreationActive) {
                LOGGER.warn("[DRY RUN] La création d'incidents est désactivée. Simulation pour le certificat {}.", certificate.getCommonName());
                handleDryRun(summary, existingIncident, priority, report);
                return;
            }

            AutoItsmTaskDto itsmTaskDto = null;
            if (existingIncident == null) {
                LOGGER.info("Aucun incident existant. Création d'un nouvel incident avec priorité {}.", priority);
                itsmTaskDto = itemTaskService.createIncidentAutoEnroll(certificate, referenceRefiDto, priority, IncidentTypeEnum.AUTOENROLL, null);
            } else {
                LOGGER.info("Incident précédent trouvé : {} (État: {}, Priorité: {})", existingIncident.getNumber(), existingIncident.getState(), existingIncident.getPriority());
                if (isIncidentClosedOrResolved(existingIncident)) {
                    LOGGER.info("L'incident {} est clos. Recréation avec priorité {}.", existingIncident.getNumber(), priority);
                    itsmTaskDto = itemTaskService.createIncidentAutoEnroll(certificate, referenceRefiDto, priority, IncidentTypeEnum.AUTOENROLL, null);
                } else if (priority.equals(PRIORITY_URGENT) && isIncidentHighPriority(existingIncident, PRIORITY_URGENT)) {
                    LOGGER.warn("L'incident {} est déjà en haute priorité ({}). Aucune action pour la demande P2.", existingIncident.getNumber(), existingIncident.getPriority());
                    report.add(summary + " - Aucune action (incident " + existingIncident.getNumber() + " déjà P" + existingIncident.getPriority() + ")");
                } else {
                    LOGGER.info("Mise à jour de l'incident {} vers la priorité {}.", existingIncident.getNumber(), priority);
                    itsmTaskDto = itemTaskService.updateIncident(existingIncident, certificate, priority);
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

    private void handleDryRun(String summary, SnowIncidentDto existingIncident, Integer priority, List<String> report) {
        String simulationMessage;
        if (existingIncident == null) {
            simulationMessage = "Création d'un nouvel incident avec priorité " + priority;
        } else if (isIncidentClosedOrResolved(existingIncident)) {
            simulationMessage = "Recréation de l'incident " + existingIncident.getNumber() + " avec priorité " + priority;
        } else if (priority.equals(PRIORITY_URGENT) && isIncidentHighPriority(existingIncident, PRIORITY_URGENT)) {
            simulationMessage = "Aucune action (incident " + existingIncident.getNumber() + " déjà P" + existingIncident.getPriority() + ")";
        } else {
            simulationMessage = "Mise à jour de l'incident " + existingIncident.getNumber() + " vers priorité " + priority;
        }
        report.add(summary + " - [SIMULATION] " + simulationMessage);
    }

    private boolean isExpiringUrgently(AutomationHubCertificateLightDto certificate) {
        return certificate.getExpiryDate().compareTo(DateUtils.addDays(new Date(), triggerDaysPlusTrois)) <= 0;
    }

    private boolean isIncidentClosedOrResolved(SnowIncidentDto incident) {
        if (incident == null || incident.getState() == null) return false;
        try {
            int state = Integer.parseInt(incident.getState());
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

    // --- Méthodes d'envoi d'e-mails (identiques à la version précédente) ---

    private void sendFinalReport(int successCount, int errorCount, List<String> report, List<String> errorReport, List<String> noSuppReport) {
        if (successCount == 0 && errorCount == 0 && report.isEmpty()) {
            LOGGER.info("Traitement terminé. Aucune action à rapporter.");
            return;
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put(TEMPLATE_KEY_REPORT, report);
        data.put(TEMPLATE_KEY_ERROR_REPORT, errorReport);
        data.put(TEMPLATE_KEY_NO_SUPPORT_REPORT, noSuppReport);
        data.put("isDryRun", !isIncidentCreationActive); // Ajout d'une variable pour le template email

        try {
            List<String> toList = new ArrayList<>();
            toList.add(ipkiTeam);
            sendMailUtils.sendEmail("/template/report-incident-autoenroll.vm", data, toList, buildReportSubject());
            LOGGER.info("Rapport final envoyé à l'équipe IPKI.");
        } catch (Exception e) {
            LOGGER.error("Échec de l'envoi de l'e-mail de synthèse.", e);
        }
    }
    
    private String buildReportSubject() {
        String subject = "Rapport | AutoEnroll Incidents Certificats";
        if (!isIncidentCreationActive) {
            subject = "[DRY RUN] " + subject;
        }
        return subject;
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
            sendMailUtils.sendEmail("/template/report-incident-sans-codeap.vm", data, toList, "Alerte | Certificats sans CodeAP");
            LOGGER.info("Rapport pour les certificats sans CodeAP envoyé.");
        } catch (Exception e) {
            LOGGER.error("Échec de l'envoi de l'e-mail pour les certificats sans CodeAP.", e);
        }
    }
}
Use code with caution.
Java
