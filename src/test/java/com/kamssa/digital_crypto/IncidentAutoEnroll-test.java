Absolument. Rédiger un test unitaire avec Mockito pour la classe IncidentAutoEnrollTask est une excellente pratique pour garantir sa fiabilité. Le but est d'isoler la classe de ses dépendances (les services externes) et de tester sa logique interne.
Voici un exemple complet et détaillé d'une classe de test IncidentAutoEnrollTaskTest utilisant JUnit 5 et Mockito.
Étape 1 : Ajouter les dépendances de test (si nécessaire)
Assurez-vous que votre pom.xml (si vous utilisez Maven) contient les dépendances nécessaires pour les tests. Spring Boot les inclut généralement par défaut dans spring-boot-starter-test.
Generated xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
Use code with caution.
Xml
Étape 2 : Créer la classe de test
Créez un nouveau fichier dans src/test/java/com/bnpparibas/certis/api/tasks/IncidentAutoEnrollTaskTest.java.
Voici le code complet du test. Les commentaires expliquent chaque partie et chaque scénario testé.
Generated java
package com.bnpparibas.certis.api.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Utilise l'extension Mockito pour JUnit 5
@ExtendWith(MockitoExtension.class)
class IncidentAutoEnrollTaskTest {

    // Crée une instance de la classe à tester et y injecte les mocks
    @InjectMocks
    private IncidentAutoEnrollTask incidentAutoEnrollTask;

    // Crée des mocks pour toutes les dépendances de la classe
    @Mock private AutomationHubService automationHubService;
    @Mock private ReferenceRefiService referenceRefiService;
    @Mock private SnowService snowService;
    @Mock private ItemTaskService itemTaskService;
    @Mock private SendMailUtils sendMailUtils;

    // Captureur d'arguments pour vérifier les données passées à la méthode d'envoi d'email
    @Captor
    private ArgumentCaptor<Map<String, Object>> emailDataCaptor;
    @Captor
    private ArgumentCaptor<String> subjectCaptor;

    // Cette méthode s'exécute avant chaque test pour initialiser les champs @Value
    @BeforeEach
    void setUp() {
        // Injection des valeurs de configuration via ReflectionTestUtils
        // car @Value ne fonctionne pas dans un test unitaire simple.
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "triggerDays", 30);
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "triggerDaysPlusTrois", 3);
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "ipkiTeam", "team@example.com");
        
        // Par défaut, on active la création d'incidents pour la plupart des tests
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "isIncidentCreationActive", true);
    }

    @Test
    @DisplayName("Test Dry Run: Ne doit créer aucun incident si la création est désactivée")
    void testProcessExpiringCertificates_whenCreationIsDisabled_shouldPerformDryRun() {
        // Arrange
        // On désactive la création d'incidents pour ce test
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "isIncidentCreationActive", false);

        AutomationHubCertificateLightDto cert = createMockCertificate("cert1", "id-123", "codeAp1", true);
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(cert));
        when(referenceRefiService.findReferenceByCodeAp("codeAp1")).thenReturn(new ReferenceRefiDto());
        when(snowService.getSnowIncidentBySysId("id-123")).thenReturn(null); // Simule qu'aucun incident n'existe

        // Act
        incidentAutoEnrollTask.processExpiringCertificates();

        // Assert
        // On vérifie qu'AUCUNE méthode de création ou de mise à jour n'a été appelée
        verify(itemTaskService, never()).createIncidentAutoEnroll(any(), any(), any(), any(), any());
        verify(itemTaskService, never()).updateIncident(any(), any(), any());

        // On vérifie que le rapport par email a bien été envoyé avec le sujet [DRY RUN]
        verify(sendMailUtils).sendEmail(any(), any(), any(), subjectCaptor.capture());
        assertTrue(subjectCaptor.getValue().startsWith("[DRY RUN]"));
    }

    @Test
    @DisplayName("Test Création: Doit créer un nouvel incident si aucun n'existe")
    void testProcessExpiringCertificates_whenNewIncidentIsNeeded_shouldCreateIt() {
        // Arrange
        AutomationHubCertificateLightDto cert = createMockCertificate("cert1", "id-123", "codeAp1", false);
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(cert));
        when(referenceRefiService.findReferenceByCodeAp("codeAp1")).thenReturn(new ReferenceRefiDto());
        when(snowService.getSnowIncidentBySysId("id-123")).thenReturn(null); // Aucun incident existant
        when(itemTaskService.createIncidentAutoEnroll(any(), any(), any(), any(), any()))
            .thenReturn(createMockTaskDto("INC001"));

        // Act
        incidentAutoEnrollTask.processExpiringCertificates();

        // Assert
        // On vérifie que la création a été appelée une fois avec la bonne priorité (standard)
        verify(itemTaskService, times(1)).createIncidentAutoEnroll(any(), any(), eq(3), any(), any());
        verify(sendMailUtils, times(1)).sendEmail(any(), emailDataCaptor.capture(), any(), any());
        
        // Vérifie que le rapport contient le message de succès
        List<String> report = (List<String>) emailDataCaptor.getValue().get("report");
        assertTrue(report.get(0).contains("INC001 créé/mis à jour"));
    }

    @Test
    @DisplayName("Test Recréation: Doit recréer un incident s'il est déjà clos")
    void testProcessExpiringCertificates_whenIncidentIsClosed_shouldRecreateIt() {
        // Arrange
        AutomationHubCertificateLightDto cert = createMockCertificate("cert1", "id-123", "codeAp1", true);
        SnowIncidentDto closedIncident = createMockSnowIncident("INC001", "6", "3"); // État 6 = Résolu

        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(cert));
        when(referenceRefiService.findReferenceByCodeAp("codeAp1")).thenReturn(new ReferenceRefiDto());
        when(snowService.getSnowIncidentBySysId("id-123")).thenReturn(closedIncident);
        when(itemTaskService.createIncidentAutoEnroll(any(), any(), any(), any(), any()))
            .thenReturn(createMockTaskDto("INC002"));

        // Act
        incidentAutoEnrollTask.processExpiringCertificates();

        // Assert
        // La méthode de création est appelée pour recréer l'incident
        verify(itemTaskService, times(1)).createIncidentAutoEnroll(any(), any(), eq(2), any(), any()); // Priorité urgente
    }

    @Test
    @DisplayName("Test P2 existante: Ne doit rien faire si un incident P2 est demandé mais qu'un P1/P2 existe déjà")
    void testProcessExpiringCertificates_whenIncidentIsAlreadyHighPriority_shouldDoNothing() {
        // Arrange
        // Ce certificat est urgent, donc une P2 sera demandée
        AutomationHubCertificateLightDto urgentCert = createMockCertificate("urgentCert", "id-456", "codeAp2", true);
        // L'incident existant est déjà P1 (donc haute priorité)
        SnowIncidentDto highPriorityIncident = createMockSnowIncident("INC555", "2", "1"); 

        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(urgentCert));
        when(referenceRefiService.findReferenceByCodeAp("codeAp2")).thenReturn(new ReferenceRefiDto());
        when(snowService.getSnowIncidentBySysId("id-456")).thenReturn(highPriorityIncident);

        // Act
        incidentAutoEnrollTask.processExpiringCertificates();

        // Assert
        // Aucune action de création ou de mise à jour ne doit être effectuée
        verify(itemTaskService, never()).createIncidentAutoEnroll(any(), any(), any(), any(), any());
        verify(itemTaskService, never()).updateIncident(any(), any(), any());
        
        // Le rapport doit indiquer "Aucune action"
        verify(sendMailUtils).sendEmail(any(), emailDataCaptor.capture(), any(), any());
        List<String> report = (List<String>) emailDataCaptor.getValue().get("report");
        assertTrue(report.get(0).contains("Aucune action"));
    }

    @Test
    @DisplayName("Test Mise à jour: Doit mettre à jour un incident ouvert de basse priorité")
    void testProcessExpiringCertificates_whenIncidentExistsAndIsLowPriority_shouldUpdateIt() {
        // Arrange
        AutomationHubCertificateLightDto urgentCert = createMockCertificate("urgentCert", "id-789", "codeAp3", true);
        // L'incident existant est ouvert mais en P4
        SnowIncidentDto lowPriorityIncident = createMockSnowIncident("INC888", "2", "4");

        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(urgentCert));
        when(referenceRefiService.findReferenceByCodeAp("codeAp3")).thenReturn(new ReferenceRefiDto());
        when(snowService.getSnowIncidentBySysId("id-789")).thenReturn(lowPriorityIncident);
        when(itemTaskService.updateIncident(any(), any(), any())).thenReturn(createMockTaskDto("INC888"));

        // Act
        incidentAutoEnrollTask.processExpiringCertificates();

        // Assert
        // On vérifie que la méthode de mise à jour est appelée avec la nouvelle priorité (P2)
        verify(itemTaskService, times(1)).updateIncident(any(), any(), eq(2));
    }

    @Test
    @DisplayName("Test Sans CodeAP: Doit ignorer le certificat et envoyer un rapport séparé")
    void testProcessExpiringCertificates_whenCertificateHasNoCodeAp_shouldSkipIt() {
        // Arrange
        AutomationHubCertificateLightDto certSansCodeAp = createMockCertificate("noCodeCert", "id-000", null, false);
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(certSansCodeAp));
        when(referenceRefiService.findReferenceByCodeAp(null)).thenReturn(null); // Le service ne trouve rien

        // Act
        incidentAutoEnrollTask.processExpiringCertificates();

        // Assert
        // Aucune interaction avec Snow ou ItemTaskService
        verify(snowService, never()).getSnowIncidentBySysId(any());
        verify(itemTaskService, never()).createIncidentAutoEnroll(any(), any(), any(), any(), any());

        // Vérifie que l'email pour les certificats sans code AP est bien envoyé
        verify(sendMailUtils).sendEmail(eq("/template/report-incident-sans-codeap.vm"), any(), any(), any());
    }
    
    // --- Méthodes utilitaires pour créer des objets mock facilement ---

    private AutomationHubCertificateLightDto createMockCertificate(String commonName, String id, String codeAp, boolean isUrgent) {
        AutomationHubCertificateLightDto cert = new AutomationHubCertificateLightDto();
        cert.setCommonName(commonName);
        cert.setAutomationHubId(id);
        cert.setCodeAp(codeAp);
        
        // Simule une date d'expiration urgente ou non
        int daysUntilExpiry = isUrgent ? 2 : 15;
        // La logique exacte de DateUtils n'est pas nécessaire, on simule juste le résultat
        cert.setExpiryDate(new Date(System.currentTimeMillis() + (long) daysUntilExpiry * 24 * 60 * 60 * 1000));
        return cert;
    }

    private SnowIncidentDto createMockSnowIncident(String number, String state, String priority) {
        SnowIncidentDto incident = new SnowIncidentDto();
        incident.setNumber(number);
        incident.setState(state);
        incident.setPriority(priority);
        return incident;
    }

    private AutoItsmTaskDto createMockTaskDto(String incidentId) {
        AutoItsmTaskDto dto = new AutoItsmTaskDto();
        dto.setIncidentId(incidentId);
        return dto;
    }
}