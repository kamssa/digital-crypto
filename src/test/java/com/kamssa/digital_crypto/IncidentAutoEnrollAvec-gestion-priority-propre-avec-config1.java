package com.bnpparibas.certis.api.tasks;

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
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Importez toutes vos classes DTO, services, exceptions, etc.
// import com.bnpparibas.certis.api.dto.*;
// import com.bnpparibas.certis.api.services.*;
// import com.bnpparibas.certis.api.exceptions.NoSupportGroupException;


@Component
public class IncidentAutoEnrollTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentAutoEnrollTask.class);

    // Énumération pour gérer les priorités de manière claire et sécurisée
    private enum IncidentPriority {
        URGENT(2),    // Ancien PRIORITY2
        STANDARD(3);  // Ancien PRIORITY3

        private final int value;

        IncidentPriority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // --- Dépendances Injectées ---
    @Autowired private ItsmTaskService itsmTaskService;
    @Autowired private CertificateOwnerService certificateOwnerService;
    @Autowired private SendMailUtils sendMailUtils;
    @Autowired private AutomationHubService automationHubService;
    @Autowired private ReferenceRefiService referenceRefiService;
    @Autowired private SnowService snowService;

    // --- Paramètres de Configuration ---
    @Value("${autoEnroll.trigger.days}")
    private int triggerDays;
    @Value("${autoEnroll.trigger.daysplustrois}")
    private int triggerDaysPlusTrois;
    @Value("${certis.mail.ipkiTeam}")
    private String ipkiTeam;
    @Value("${task.incidentAutoEnroll.disable:false}")
    private boolean isTaskDisabled;

    /**
     * Tâche planifiée principale qui s'exécute pour traiter les certificats en cours d'expiration.
     */
    @Scheduled(cron = "${cron.task.incidentAutoEnroll:0 10 * * * ?}")
    public void processExpiredCertificates() {
        if (isTaskDisabled) {
            LOGGER.info("La tâche de création d'incidents (IncidentAutoEnrollTask) est désactivée via la configuration.");
            return;
        }
        LOGGER.info("Démarrage de la tâche de traitement de l'expiration des certificats.");
        Instant startTime = Instant.now();
        ProcessingContext context = new ProcessingContext();

        try {
            List<AutomationHubCertificateLightDto> certificates = automationHubService.searchAutoEnrollExpiring(triggerDays);
            if (certificates == null || certificates.isEmpty()) {
                LOGGER.info("Aucun certificat à traiter pour cette exécution.");
            } else {
                LOGGER.info("{} certificat(s) trouvé(s) à traiter.", certificates.size());
                processAllCertificates(certificates, context);
            }
        } catch (Exception e) {
            LOGGER.error("La tâche de traitement a échoué de manière inattendue. Impossible de continuer.", e);
            throw new RuntimeException("Échec critique de la tâche planifiée processExpiredCertificates", e);
        } finally {
            sendSummaryReports(context);
            Duration duration = Duration.between(startTime, Instant.now());
            LOGGER.info("Fin de la tâche de traitement. Temps d'exécution total : {} ms.", duration.toMillis());
        }
    }

    /**
     * Itère sur tous les certificats et traite chacun individuellement.
     */
    private void processAllCertificates(List<AutomationHubCertificateLightDto> certificates, ProcessingContext context) {
        for (AutomationHubCertificateLightDto certificate : certificates) {
            try {
                processSingleCertificate(certificate, context);
            } catch (Exception e) {
                // Sécurité : attrape les erreurs inattendues pour un seul certificat afin que le batch continue.
                LOGGER.error("Erreur non gérée lors du traitement du certificat ID: {}", certificate.getAutomationHubId(), e);
                context.recordError("Erreur système inattendue", certificate);
            }
        }
    }

    /**
     * Contient toute la logique métier pour un seul certificat.
     * Filtrage, enrichissement, validation et création/mise à jour de l'incident.
     */
    private void processSingleCertificate(AutomationHubCertificateLightDto certificate, ProcessingContext context) {
        // Étape 1: Filtrage (ex: ne traiter que l'environnement de production)
        String environment = getLabelByKey(certificate, "ENVIRONNEMENT"); // Remplacez par votre LabelEnum
        if (environment != null && !environment.trim().isEmpty() && !"PROD".equalsIgnoreCase(environment.trim())) {
            return; // On ignore silencieusement les certificats non-PROD
        }

        // Étape 2: Enrichissement des données et validation (clauses de sauvegarde)
        CertificateOwnerDTO owner = certificateOwnerService.getBestAvailableCertificateOwner(certificate);
        if (owner == null || owner.getCodeAp() == null || owner.getCodeAp().isEmpty()) {
            LOGGER.warn("Certificat ignoré car le 'codeAp' est manquant. ID: {}", certificate.getAutomationHubId());
            context.certsWithoutCodeApp.add(certificate);
            return;
        }

        ReferenceRefDto reference = referenceRefiService.findReferenceByCodeAp(owner.getCodeAp());
        if (reference == null) {
            LOGGER.error("Certificat ignoré, codeAp '{}' introuvable dans le référentiel. ID: {}", owner.getCodeAp(), certificate.getAutomationHubId());
            context.certsWithoutCodeApp.add(certificate);
            return;
        }

        // Étape 3: Détermination de la logique métier (créer un incident standard ou urgent)
        boolean isExpiringUrgently = certificate.getExpiryDate().compareTo(DateUtils.addDays(new Date(), triggerDays)) < 0;
        IncidentPriority priority = isExpiringUrgently ? IncidentPriority.URGENT : IncidentPriority.STANDARD;
        
        createOrUpdateIncident(certificate, reference, priority, context);
    }

    /**
     * Gère la création, la recréation ou la mise à jour d'un incident ITSM.
     * Cette méthode remplace les anciennes `createIncidentAndLogResult` et `createIncidentAndLogResultP2`.
     */
    private void createOrUpdateIncident(AutomationHubCertificateLightDto dto, ReferenceRefDto reference, IncidentPriority priority, ProcessingContext context) {
        try {
            // Recherche d'un incident existant
            AutoItsmTaskDtoImpl existingTask = itsmTaskService.findActiveIncidentForHubId(dto.getAutomationHubId()); // Suppose une méthode simplifiée
            SnowIncidentReadResponseDto snowIncident = (existingTask == null) ? null : snowService.getIncidentBySysId(existingTask.getSysId());

            if (snowIncident == null) {
                LOGGER.info("Aucun INC actif pour {}. Création d'un nouvel incident avec priorité {}.", dto.getAutomationHubId(), priority.name());
                itsmTaskService.createIncidentAutoEnroll(priority.getValue(), dto, reference);
                context.recordSuccess("Création INC " + priority.name(), dto, priority == IncidentPriority.URGENT);
            } else if (isIncidentResolved(snowIncident)) {
                LOGGER.info("L'INC précédent {} est résolu. Création d'un nouvel incident avec priorité {}.", snowIncident.getNumber(), priority.name());
                itsmTaskService.recreateIncidentAutoEnroll(priority.getValue(), dto, reference, snowIncident); // Méthode à créer/adapter
                context.recordSuccess("Recréation INC " + priority.name(), dto, priority == IncidentPriority.URGENT);
            } else if (priority == IncidentPriority.URGENT && snowIncident.getPriority() > priority.getValue()) {
                LOGGER.info("L'INC existant {} est ouvert. Mise à jour de la priorité vers {}.", snowIncident.getNumber(), priority.name());
                itsmTaskService.upgradeIncidentAutoEnroll(priority.getValue(), existingTask);
                context.recordSuccess("Mise à jour INC vers " + priority.name(), dto, true);
            } else {
                LOGGER.info("L'INC existant {} est déjà ouvert avec une priorité adéquate. Aucune action.", snowIncident.getNumber());
            }
        } catch (NoSupportGroupException e) {
            LOGGER.warn("Aucun groupe de support trouvé pour {}: {}", dto.getAutomationHubId(), e.getMessage());
            context.noSuppReport.add("Certificat " + dto.getAutomationHubId() + " (" + dto.getCommonName() + ") : " + e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Erreur lors de l'interaction ITSM pour {}", dto.getAutomationHubId(), e);
            context.recordError("Échec de la communication avec l'outil d'incidents", dto);
        }
    }

    /**
     * Orchestre l'envoi des différents e-mails de rapport à la fin du traitement.
     */
    private void sendSummaryReports(ProcessingContext context) {
        sendFinalReport(context);
        sendAutoEnrollCertificateNoCodeAppReport(context);
    }
    
    private void sendFinalReport(ProcessingContext context) {
        if (!context.hasItemsForFinalReport()) {
            LOGGER.info("Rapport final non envoyé : aucune action de création ou d'erreur à signaler.");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("report", context.report);
        data.put("errorReport", context.errorReport);
        data.put("date", new Date());
        
        try {
            List<String> toList = new ArrayList<>();
            toList.add(ipkiTeam);
            sendMailUtils.sendEmail("template/report-incident-p3.vm", data, toList, "Report | Incident P3");
            LOGGER.info("Rapport final envoyé avec {} succès et {} erreurs.", context.successCounter.get(), context.errorCounter.get());
        } catch(Exception e) {
            LOGGER.error("Échec de l'envoi de l'e-mail de synthèse.", e);
        }
    }
    
    private void sendAutoEnrollCertificateNoCodeAppReport(ProcessingContext context) {
        if (context.certsWithoutCodeApp.isEmpty() && context.noSuppReport.isEmpty()) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("dtosSansCodeApp", context.certsWithoutCodeApp);
        data.put("noSuppReport", context.noSuppReport);
        data.put("date", new Date());
        
        try {
            List<String> toList = new ArrayList<>();
            toList.add(ipkiTeam);
            sendMailUtils.sendEmail("template/report-incident-sans-codeap.vm", data, toList, "Report | Certificats n'ayant pas de codeAP valide");
            LOGGER.info("Rapport pour certificats sans codeApp envoyé.");
        } catch (Exception e) {
            LOGGER.error("Échec de l'envoi de l'e-mail pour les certificats sans codeApp.", e);
        }
    }

    // --- Méthodes utilitaires ---

    private boolean isIncidentResolved(SnowIncidentReadResponseDto snowIncident) {
        // Votre logique pour déterminer si un incident est résolu/clos
        // Exemple : return snowIncident.getState() >= SnowIncidentStateEnum.RESOLVED.getValue();
        return false; // À implémenter
    }

    private String getLabelByKey(AutomationHubCertificateLightDto dto, String key) {
        if (dto == null || dto.getLabels() == null || dto.getLabels().isEmpty()) {
            return null;
        }
        for (CertificateLabelDto label : dto.getLabels()) {
            if (key.equalsIgnoreCase(label.getKey())) {
                return label.getValue();
            }
        }
        return null;
    }

    /**
     * Classe interne statique pour contenir l'état et les compteurs d'une seule exécution de la tâche.
     * Cela évite de passer de nombreux paramètres entre les méthodes.
     */
    private static class ProcessingContext {
        final AtomicInteger successCounter = new AtomicInteger(0);
        final AtomicInteger errorCounter = new AtomicInteger(0);
        final List<String> report = new ArrayList<>();
        final List<String> errorReport = new ArrayList<>();
        final List<String> noSuppReport = new ArrayList<>();
        final List<AutomationHubCertificateLightDto> certsWithoutCodeApp = new ArrayList<>();

        public boolean hasItemsForFinalReport() {
            return successCounter.get() > 0 || errorCounter.get() > 0;
        }

        public void recordError(String message, AutomationHubCertificateLightDto dto) {
            errorCounter.incrementAndGet();
            String formattedMessage = String.format("Certificat ID %s (%s) : %s",
                dto.getAutomationHubId(), dto.getCommonName(), message);
            errorReport.add(formattedMessage);
        }

        public void recordSuccess(String message, AutomationHubCertificateLightDto dto, boolean isUrgent) {
            successCounter.incrementAndGet();
            String formattedMessage = String.format("Certificat ID %s (%s) : %s",
                dto.getAutomationHubId(), dto.getCommonName(), message);
            report.add(formattedMessage);
        }
    }
}
///////////////////////////////////////////////////////////////// solution 2 /////////////////////////////////////

@Component
public class IncidentAutoEnrollTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentAutoEnrollTask.class);

    private enum IncidentPriority {
        URGENT(2),
        STANDARD(3);

        private final int value;
        IncidentPriority(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    // --- Dépendances Injectées ---
    @Autowired private ItsmTaskService itsmTaskService;
    @Autowired private CertificateOwnerService certificateOwnerService;
    @Autowired private SendMailUtils sendMailUtils;
    @Autowired private AutomationHubService automationHubService;
    @Autowired private ReferenceRefiService referenceRefiService;
    @Autowired private SnowService snowService;

    // --- Paramètres de Configuration ---
    @Value("${autoEnroll.trigger.days}")
    private int triggerDays;
    @Value("${autoEnroll.trigger.daysplustrois}")
    private int triggerDaysPlusTrois;
    @Value("${certis.mail.ipkiTeam}")
    private String ipkiTeam;
    @Value("${task.incidentAutoEnroll.disable:false}")
    private boolean isTaskDisabled;

    /**
     * Tâche planifiée principale qui s'exécute pour traiter les certificats en cours d'expiration.
     */
    @Scheduled(cron = "${cron.task.incidentAutoEnroll:0 10 * * * ?}")
    public void processExpiredCertificates() {
        if (isTaskDisabled) {
            LOGGER.info("La tâche de création d'incidents (IncidentAutoEnrollTask) est désactivée.");
            return;
        }
        LOGGER.info("Démarrage de la tâche de traitement de l'expiration des certificats.");
        Instant startTime = Instant.now();
        ProcessingContext context = new ProcessingContext();

        try {
            List<AutomationHubCertificateLightDto> certificates = automationHubService.searchAutoEnrollExpiring(triggerDays);
            if (certificates == null || certificates.isEmpty()) {
                LOGGER.info("Aucun certificat à traiter pour cette exécution.");
            } else {
                LOGGER.info("{} certificat(s) trouvé(s) à traiter.", certificates.size());
                processAllCertificates(certificates, context);
            }
        } catch (Exception e) {
            LOGGER.error("La tâche de traitement a échoué de manière inattendue. Impossible de continuer.", e);
            throw new RuntimeException("Échec critique de la tâche planifiée processExpiredCertificates", e);
        } finally {
            sendSummaryReports(context);
            Duration duration = Duration.between(startTime, Instant.now());
            LOGGER.info("Fin de la tâche de traitement. Temps d'exécution total : {} ms.", duration.toMillis());
        }
    }

    private void processAllCertificates(List<AutomationHubCertificateLightDto> certificates, ProcessingContext context) {
        for (AutomationHubCertificateLightDto certificate : certificates) {
            try {
                processSingleCertificate(certificate, context);
            } catch (Exception e) {
                LOGGER.error("Erreur non gérée lors du traitement du certificat ID: {}", certificate.getAutomationHubId(), e);
                context.recordError("Erreur système inattendue", certificate);
            }
        }
    }

    private void processSingleCertificate(AutomationHubCertificateLightDto certificate, ProcessingContext context) {
        String environment = getLabelByKey(certificate, "ENVIRONNEMENT");
        if (environment != null && !environment.trim().isEmpty() && !"PROD".equalsIgnoreCase(environment.trim())) {
            return;
        }

        CertificateOwnerDTO owner = certificateOwnerService.getBestAvailableCertificateOwner(certificate);
        if (owner == null || owner.getCodeAp() == null || owner.getCodeAp().isEmpty()) {
            LOGGER.warn("Certificat ignoré car le 'codeAp' est manquant. ID: {}", certificate.getAutomationHubId());
            context.certsWithoutCodeApp.add(certificate);
            return;
        }

        ReferenceRefDto reference = referenceRefiService.findReferenceByCodeAp(owner.getCodeAp());
        if (reference == null) {
            LOGGER.error("Certificat ignoré, codeAp '{}' introuvable. ID: {}", owner.getCodeAp(), certificate.getAutomationHubId());
            context.certsWithoutCodeApp.add(certificate);
            return;
        }

        boolean isExpiringUrgently = certificate.getExpiryDate().compareTo(DateUtils.addDays(new Date(), triggerDays)) < 0;
        IncidentPriority priority = isExpiringUrgently ? IncidentPriority.URGENT : IncidentPriority.STANDARD;
        
        createOrUpdateIncident(certificate, reference, priority, context);
    }

    private void createOrUpdateIncident(AutomationHubCertificateLightDto dto, ReferenceRefDto reference, IncidentPriority priority, ProcessingContext context) {
        try {
            AutoItsmTaskDtoImpl existingTask = itsmTaskService.findActiveIncidentForHubId(dto.getAutomationHubId());
            SnowIncidentReadResponseDto snowIncident = (existingTask == null) ? null : snowService.getIncidentBySysId(existingTask.getSysId());
            
            String actionMessage;
            
            if (snowIncident == null) {
                actionMessage = "Création INC " + priority.name();
                LOGGER.info("Aucun INC actif pour {}. {}.", dto.getAutomationHubId(), actionMessage);
                itsmTaskService.createIncidentAutoEnroll(priority.getValue(), dto, reference);
            } else if (isIncidentResolved(snowIncident)) {
                actionMessage = "Recréation INC " + priority.name();
                LOGGER.info("L'INC précédent {} est résolu. {}.", snowIncident.getNumber(), actionMessage);
                itsmTaskService.recreateIncidentAutoEnroll(priority.getValue(), dto, reference, snowIncident);
            } else if (priority == IncidentPriority.URGENT && snowIncident.getPriority() > priority.getValue()) {
                actionMessage = "Mise à jour INC vers " + priority.name();
                LOGGER.info("L'INC existant {} est ouvert. {}.", snowIncident.getNumber(), actionMessage);
                itsmTaskService.upgradeIncidentAutoEnroll(priority.getValue(), existingTask);
            } else {
                LOGGER.info("L'INC existant {} est déjà ouvert avec une priorité adéquate. Aucune action.", snowIncident.getNumber());
                return;
            }
            
            context.recordSuccess(actionMessage, dto, reference, priority);

        } catch (NoSupportGroupException e) {
            LOGGER.warn("Aucun groupe de support trouvé pour {}: {}", dto.getAutomationHubId(), e.getMessage());
            context.noSuppReport.add("Certificat " + dto.getAutomationHubId() + " (" + dto.getCommonName() + ") : " + e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Erreur lors de l'interaction ITSM pour {}", dto.getAutomationHubId(), e);
            context.recordError("Échec de la communication avec l'outil d'incidents", dto);
        }
    }

    private void sendSummaryReports(ProcessingContext context) {
        sendFinalReport(context);
        sendAutoEnrollCertificateNoCodeAppReport(context);
    }
    
    private void sendFinalReport(ProcessingContext context) {
        if (!context.hasItemsForFinalReport()) {
            LOGGER.info("Rapport final non envoyé : aucune action de création ou d'erreur à signaler.");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("report", context.successReport);
        data.put("errorReport", context.errorReport);
        data.put("date", new Date());
        
        try {
            sendMailUtils.sendEmail("template/report-incident-p3.vm", data, List.of(ipkiTeam), "Report | Incident P3");
            LOGGER.info("Rapport final envoyé avec {} succès et {} erreurs.", context.successCounter.get(), context.errorCounter.get());
        } catch(Exception e) {
            LOGGER.error("Échec de l'envoi de l'e-mail de synthèse.", e);
        }
    }
    
    private void sendAutoEnrollCertificateNoCodeAppReport(ProcessingContext context) {
        if (context.certsWithoutCodeApp.isEmpty() && context.noSuppReport.isEmpty()) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("dtosSansCodeApp", context.certsWithoutCodeApp);
        data.put("noSuppReport", context.noSuppReport);
        data.put("date", new Date());
        
        try {
            sendMailUtils.sendEmail("template/report-incident-sans-codeap.vm", data, List.of(ipkiTeam), "Report | Certificats n'ayant pas de codeAP valide");
            LOGGER.info("Rapport pour certificats sans codeApp envoyé.");
        } catch (Exception e) {
            LOGGER.error("Échec de l'envoi de l'e-mail pour les certificats sans codeApp.", e);
        }
    }

    private boolean isIncidentResolved(SnowIncidentReadResponseDto snowIncident) {
        // Implémentez votre logique ici. Exemple :
        // return snowIncident != null && snowIncident.getState() >= SnowIncidentStateEnum.RESOLVED.getValue();
        return false;
    }

    private String getLabelByKey(AutomationHubCertificateLightDto dto, String key) {
        if (dto == null || dto.getLabels() == null || dto.getLabels().isEmpty()) return null;
        for (CertificateLabelDto label : dto.getLabels()) {
            if (key.equalsIgnoreCase(label.getKey())) return label.getValue();
        }
        return null;
    }

    /**
     * Classe interne statique pour contenir l'état et les compteurs d'une seule exécution de la tâche.
     */
    private static class ProcessingContext {
        
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        final AtomicInteger successCounter = new AtomicInteger(0);
        final AtomicInteger errorCounter = new AtomicInteger(0);
        final List<String> successReport = new ArrayList<>();
        final List<String> errorReport = new ArrayList<>();
        final List<String> noSuppReport = new ArrayList<>();
        final List<AutomationHubCertificateLightDto> certsWithoutCodeApp = new ArrayList<>();

        public boolean hasItemsForFinalReport() {
            return successCounter.get() > 0 || errorCounter.get() > 0;
        }

        public void recordError(String message, AutomationHubCertificateLightDto dto) {
            errorCounter.incrementAndGet();
            String formattedMessage = String.format("Certificat ID %s (%s) : %s",
                dto.getAutomationHubId(), dto.getCommonName(), message);
            errorReport.add(formattedMessage);
        }

        public void recordSuccess(String actionMessage, AutomationHubCertificateLightDto dto, ReferenceRefDto reference, IncidentPriority priority) {
            successCounter.incrementAndGet();
            
            String expiryDateStr = "N/A";
            Date expiryDate = dto.getExpiryDate();
            if (expiryDate != null) {
                expiryDateStr = expiryDate.toInstant()
                                          .atZone(ZoneId.systemDefault())
                                          .toLocalDate()
                                          .format(DATE_FORMATTER);
            }

            String formattedMessage = String.format(
                "Action: %-25s | AutomationHub ID: %-15s | Common Name: %-30s | Code AP: %-15s | Support Group: %-20s | Expiration: %-12s | Priorité: %s",
                actionMessage,
                dto.getAutomationHubId(),
                dto.getCommonName(),
                reference.getCodeAp(),
                reference.getSupportGroup(),
                expiryDateStr,
                priority.name()
            );

            successReport.add(formattedMessage);
        }
    }
}
//////////////////////////////////test//////////////////////////////////////////////////////////////////
package com.bnpparibas.certis.api.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.*;

// Importez les DTO et Services nécessaires pour le test
// import com.bnpparibas.certis.api.dto.*;
// import com.bnpparibas.certis.api.services.*;

@ExtendWith(MockitoExtension.class) // Active l'utilisation de Mockito avec JUnit 5
class IncidentAutoEnrollTaskTest {

    // --- Mocks : Versions factices des dépendances ---
    @Mock private ItsmTaskService itsmTaskService;
    @Mock private CertificateOwnerService certificateOwnerService;
    @Mock private SendMailUtils sendMailUtils;
    @Mock private AutomationHubService automationHubService;
    @Mock private ReferenceRefiService referenceRefiService;
    @Mock private SnowService snowService;

    // --- Classe sous test ---
    // Mockito va créer une instance de IncidentAutoEnrollTask et y injecter tous les @Mock ci-dessus.
    @InjectMocks
    private IncidentAutoEnrollTask incidentAutoEnrollTask;

    @BeforeEach
    void setUp() {
        // Injection des valeurs de configuration (@Value) avant chaque test
        // ReflectionTestUtils est un utilitaire de Spring pour manipuler des champs privés.
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "isTaskDisabled", false);
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "triggerDays", 15);
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "ipkiTeam", "test-team@example.com");
    }

    @Test
    @DisplayName("Quand un certificat urgent est trouvé sans incident existant, un nouvel incident est créé.")
    void whenUrgentCertificateFound_andNoExistingIncident_thenNewIncidentIsCreated() {
        // --- ARRANGE (Préparation) ---
        // 1. Créer des données de test factices
        AutomationHubCertificateLightDto mockCertificate = new AutomationHubCertificateLightDto();
        mockCertificate.setAutomationHubId("HUB123");
        mockCertificate.setCommonName("test.example.com");
        // Mettre une date d'expiration proche pour déclencher la logique "urgente"
        mockCertificate.setExpiryDate(DateUtils.addDays(new Date(), 10)); 

        CertificateOwnerDTO mockOwner = new CertificateOwnerDTO();
        mockOwner.setCodeAp("APP001");

        ReferenceRefDto mockReference = new ReferenceRefDto();
        mockReference.setCodeAp("APP001");
        mockReference.setSupportGroup("SUPPORT_GROUP_A");

        // 2. Définir le comportement des mocks (ce qu'ils doivent retourner quand on les appelle)
        when(automationHubService.searchAutoEnrollExpiring(15)).thenReturn(List.of(mockCertificate));
        when(certificateOwnerService.getBestAvailableCertificateOwner(mockCertificate)).thenReturn(mockOwner);
        when(referenceRefiService.findReferenceByCodeAp("APP001")).thenReturn(mockReference);
        // Simuler qu'aucun incident n'existe pour ce certificat
        when(itsmTaskService.findActiveIncidentForHubId("HUB123")).thenReturn(null);

        // --- ACT (Action) ---
        // Appeler la méthode publique que l'on veut tester
        incidentAutoEnrollTask.processExpiredCertificates();

        // --- ASSERT (Vérification) ---
        // Vérifier que les méthodes attendues ont bien été appelées avec les bons paramètres.
        
        // On vérifie que le service de création d'incident a été appelé 1 seule fois.
        verify(itsmTaskService, times(1)).createIncidentAutoEnroll(
            eq(2), // Priorité URGENT
            eq(mockCertificate),
            eq(mockReference)
        );

        // On vérifie qu'AUCUNE autre méthode de modification d'incident n'a été appelée.
        verify(itsmTaskService, never()).recreateIncidentAutoEnroll(anyInt(), any(), any(), any());
        verify(itsmTaskService, never()).upgradeIncidentAutoEnroll(anyInt(), any());

        // On vérifie que l'e-mail de rapport final a bien été envoyé.
        // `any()` est utilisé car le contenu exact du Map peut être complexe à recréer.
        verify(sendMailUtils, times(1)).sendEmail(
            eq("template/report-incident-p3.vm"),
            any(Map.class),
            any(List.class),
            anyString()
        );
    }
    
    @Test
    @DisplayName("Quand la tâche est désactivée, aucun service de traitement n'est appelé.")
    void whenTaskIsDisabled_thenNoProcessingServiceIsCalled() {
        // --- ARRANGE ---
        // Écraser la configuration de setUp pour ce test spécifique
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "isTaskDisabled", true);

        // --- ACT ---
        incidentAutoEnrollTask.processExpiredCertificates();

        // --- ASSERT ---
        // Vérifier qu'AUCUN appel n'a été fait au service qui récupère les certificats.
        // Si ce service n'est pas appelé, on sait que le reste du traitement n'a pas eu lieu.
        verify(automationHubService, never()).searchAutoEnrollExpiring(anyInt());
    }

    // Vous pouvez ajouter d'autres tests pour couvrir plus de cas :
    // - Un certificat qui a déjà un incident ouvert.
    // - Un certificat qui a un incident résolu (doit en recréer un).
    // - Un certificat sans codeAp (doit générer le rapport adéquat).
    // - Le cas où automationHubService ne retourne aucun certificat.
}
//////////////////////// test 1 //////////////////////////////////////////////////
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor; // Outil puissant pour inspecter les arguments

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals; // Pour vérifier les valeurs
import static org.mockito.Mockito.*;

// ... (le reste de la classe de test reste identique)

class IncidentAutoEnrollTaskTest {

    // ... (tous les @Mock, @InjectMocks et la méthode setUp restent les mêmes)

    @Test
    @DisplayName("Quand un certificat est valide et sans incident existant, un nouvel incident est créé.")
    void quandUnCertificatEstValide_etSansIncidentExistant_unNouvelIncidentEstCree() {

        // ====================================================================================
        //  1. PRÉPARATION (Arrange)
        //  Nous configurons nos mocks pour simuler un scénario précis.
        // ====================================================================================

        // a) Créer des données de test factices
        // On crée un certificat qui n'est pas encore "urgent" pour tester la priorité STANDARD (3)
        Date futureDate = Date.from(Instant.now().plus(20, ChronoUnit.DAYS));
        AutomationHubCertificateLightDto mockCertificate = new AutomationHubCertificateLightDto();
        mockCertificate.setAutomationHubId("HUB456");
        mockCertificate.setCommonName("app.prod.example.com");
        mockCertificate.setExpiryDate(futureDate);

        CertificateOwnerDTO mockOwner = new CertificateOwnerDTO();
        mockOwner.setCodeAp("APP002");

        ReferenceRefDto mockReference = new ReferenceRefDto();
        mockReference.setCodeAp("APP002");
        mockReference.setSupportGroup("SUPPORT_GROUP_B");

        // b) Programmer le comportement des mocks
        // Quand le service AutomationHub est appelé, il retourne notre certificat factice.
        when(automationHubService.searchAutoEnrollExpiring(15)).thenReturn(List.of(mockCertificate));
        
        // Quand on cherche le propriétaire du certificat, on retourne notre propriétaire factice.
        when(certificateOwnerService.getBestAvailableCertificateOwner(mockCertificate)).thenReturn(mockOwner);
        
        // Quand on cherche la référence via le code AP, on retourne notre référence factice.
        when(referenceRefiService.findReferenceByCodeAp("APP002")).thenReturn(mockReference);
        
        // ---> C'EST LA CONDITION CLÉ POUR CE TEST <---
        // Quand on demande s'il y a un incident existant, on retourne `null` pour forcer la création.
        when(itsmTaskService.findActiveIncidentForHubId("HUB456")).thenReturn(null);

        // ====================================================================================
        //  2. ACTION (Act)
        //  On exécute la méthode publique que l'on souhaite tester.
        // ====================================================================================

        incidentAutoEnrollTask.processExpiredCertificates();

        // ====================================================================================
        //  3. VÉRIFICATION (Assert)
        //  On vérifie que les bonnes méthodes ont été appelées (ou non) avec les bons arguments.
        // ====================================================================================

        // a) Vérification principale : la méthode de création a-t-elle été appelée ?
        // On vérifie que `createIncidentAutoEnroll` a été appelée exactement 1 fois.
        // On utilise `eq()` pour s'assurer que les arguments passés sont exactement ceux que l'on attend.
        verify(itsmTaskService, times(1)).createIncidentAutoEnroll(
            eq(3), // Priorité STANDARD
            eq(mockCertificate),
            eq(mockReference)
        );

        // b) Vérification de sécurité : les autres chemins logiques n'ont PAS été pris.
        // On s'assure que ni la recréation, ni la mise à jour n'ont été appelées.
        // C'est une vérification très importante pour garantir que notre logique de branchement est correcte.
        verify(itsmTaskService, never()).recreateIncidentAutoEnroll(anyInt(), any(), any(), any());
        verify(itsmTaskService, never()).upgradeIncidentAutoEnroll(anyInt(), any());

        // c) Vérification de l'effet de bord : le rapport a-t-il été envoyé ?
        // On utilise un "ArgumentCaptor" pour capturer l'objet Map passé à la méthode d'envoi d'e-mail.
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(sendMailUtils, times(1)).sendEmail(
            anyString(),
            mapCaptor.capture(), // On capture la Map ici
            any(List.class),
            anyString()
        );
        
        // Maintenant, on peut inspecter le contenu du rapport capturé.
        Map<String, Object> capturedMap = mapCaptor.getValue();
        List<String> successReport = (List<String>) capturedMap.get("report");
        
        // On vérifie que le rapport contient bien 1 entrée de succès et 0 erreur.
        assertEquals(1, successReport.size());
        // On peut même vérifier que le rapport contient le nom de notre certificat de test.
        String reportLine = successReport.get(0);
        assertTrue(reportLine.contains("Création INC STANDARD"));
        assertTrue(reportLine.contains("HUB456"));
        assertTrue(reportLine.contains("app.prod.example.com"));
    }
}