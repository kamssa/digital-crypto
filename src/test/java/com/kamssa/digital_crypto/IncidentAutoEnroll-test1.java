import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

// Remplacez par les vrais chemins de vos DTOs
import com.example.dto.AutomationHubCertificateLightDto;
import com.example.dto.RequestDto;
import com.example.dto.SnowIncidentReadResponseDto;
import com.example.service.AutomationHubService;
import com.example.service.ItomTaskService;
import com.example.service.ReferenceRefiService;
import com.example.service.SendMailUtils;
import com.example.service.SnowService;

@SpringBootTest
class IncidentAutoEnrollTaskTest {

    // --- La classe que nous voulons tester ---
    @Autowired
    private IncidentAutoEnrollTask incidentAutoEnrollTask;

    // --- Mocks pour toutes les dépendances externes ---
    // @MockBean remplace le vrai bean dans le contexte Spring par un mock Mockito.
    @MockBean private AutomationHubService automationHubService;
    @MockBean private ItomTaskService itomTaskService;
    @MockBean private SnowService snowService;
    @MockBean private ReferenceRefiService referenceRefiService;
    @MockBean private SendMailUtils sendMailUtils;
    // Ajoutez d'autres services si nécessaire (ex: CertificateOwnerService)

    // Constantes pour les états ServiceNow (adaptez si nécessaire)
    private static final String STATE_OPEN = "1";
    private static final String STATE_RESOLVED = "6";

    @BeforeEach
    void setUp() {
        // Optionnel : réinitialiser les mocks avant chaque test pour une isolation parfaite.
        // C'est généralement une bonne pratique, bien que @MockBean le fasse en partie.
        reset(automationHubService, itomTaskService, snowService, sendMailUtils);
    }

    // --- Tests des Scénarios ---

    @Test
    @DisplayName("SCÉNARIO 0 : La tâche est désactivée via la configuration")
    @TestPropertySource(properties = "task.incidentAutoEnroll.disabled=true")
    void whenTaskIsDisabled_thenProcessDoesNotRun() {
        // WHEN
        incidentAutoEnrollTask.processExpireCertificats();

        // THEN
        // On vérifie qu'AUCUN service n'a été appelé, car la tâche doit s'arrêter immédiatement.
        verify(automationHubService, never()).searchAutoEnrollExpiring(anyInt());
        verify(itomTaskService, never()).createIncidentAutoEnroll(any());
    }
    
    @Test
    @DisplayName("SCÉNARIO 1 : Aucun certificat expirant n'est trouvé")
    void whenNoCertificatesFound_thenNoIncidentsAreCreated() {
        // GIVEN: Le service de recherche ne retourne aucun certificat
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(Collections.emptyList());

        // WHEN
        incidentAutoEnrollTask.processExpireCertificats();

        // THEN
        // On vérifie que la recherche a eu lieu, mais aucune création d'incident ou envoi de mail.
        verify(automationHubService, times(1)).searchAutoEnrollExpiring(anyInt());
        verify(itomTaskService, never()).createIncidentAutoEnroll(any());
        verify(sendMailUtils, never()).sendEmail(anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("SCÉNARIO 2 : Crée un incident P3 pour un certificat expirant dans 15 jours")
    void givenCertificateExpiringLater_whenNoExistingIncident_thenCreatesP3Incident() {
        // GIVEN: Un certificat expirant dans 15 jours
        AutomationHubCertificateLightDto certP3 = createMockCertificate("cert-p3", 15);
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(certP3));
        
        // Et aucun incident n'existe pour ce certificat
        when(snowService.findIncidentByCorrelationId(anyString())).thenReturn(null);

        // WHEN
        incidentAutoEnrollTask.processExpireCertificats();

        // THEN
        // On capture l'objet envoyé à la méthode de création d'incident
        ArgumentCaptor<RequestDto> requestCaptor = ArgumentCaptor.forClass(RequestDto.class);
        verify(itomTaskService, times(1)).createIncidentAutoEnroll(requestCaptor.capture());

        // On vérifie que la priorité est bien P3
        assertEquals("3", requestCaptor.getValue().getPriority());
    }

    @Test
    @DisplayName("SCÉNARIO 3 : Crée un incident P2 pour un certificat expirant dans 2 jours (urgent)")
    void givenCertificateExpiringSoon_whenNoExistingIncident_thenCreatesP2Incident() {
        // GIVEN: Un certificat expirant dans 2 jours (sous le seuil de 3 jours)
        AutomationHubCertificateLightDto certP2 = createMockCertificate("cert-p2", 2);
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(certP2));
        when(snowService.findIncidentByCorrelationId(anyString())).thenReturn(null);

        // WHEN
        incidentAutoEnrollTask.processExpireCertificats();

        // THEN
        ArgumentCaptor<RequestDto> requestCaptor = ArgumentCaptor.forClass(RequestDto.class);
        verify(itomTaskService, times(1)).createIncidentAutoEnroll(requestCaptor.capture());

        // On vérifie que la priorité est bien P2
        assertEquals("2", requestCaptor.getValue().getPriority());
    }
    
    @Test
    @DisplayName("SCÉNARIO 4 : Un incident P3 existe déjà pour un certificat devenu urgent (P2)")
    void givenUrgentCertificate_whenP3IncidentExists_thenUpdatesIncidentToP2() {
        // GIVEN: Un certificat expirant dans 2 jours
        AutomationHubCertificateLightDto certUrgent = createMockCertificate("cert-urgent", 2);
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(certUrgent));

        // Et un incident P3 ouvert existe déjà pour lui
        SnowIncidentReadResponseDto existingIncident = new SnowIncidentReadResponseDto();
        existingIncident.setSysId("sys-id-123");
        existingIncident.setNumber("INC001");
        existingIncident.setPriority("3");
        existingIncident.setState(STATE_OPEN);
        when(snowService.findIncidentByCorrelationId(anyString())).thenReturn(existingIncident);
        
        // WHEN
        incidentAutoEnrollTask.processExpireCertificats();

        // THEN
        // On vérifie que la méthode de MISE A JOUR est appelée
        verify(itomTaskService, times(1)).updateIncidentPriority(anyString(), any(RequestDto.class));
        // Et qu'aucune nouvelle création/recréation n'a lieu
        verify(itomTaskService, never()).createIncidentAutoEnroll(any());
        verify(itomTaskService, never()).recreateIncidentAutoEnroll(any());
    }

    @Test
    @DisplayName("SCÉNARIO 5 : Un incident existe déjà mais est fermé")
    void givenCertificate_whenClosedIncidentExists_thenRecreatesNewIncident() {
        // GIVEN: Un certificat
        AutomationHubCertificateLightDto cert = createMockCertificate("cert-closed", 10);
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(cert));

        // Et un incident fermé existe déjà
        SnowIncidentReadResponseDto closedIncident = new SnowIncidentReadResponseDto();
        closedIncident.setState(STATE_RESOLVED); // État "Résolu"
        when(snowService.findIncidentByCorrelationId(anyString())).thenReturn(closedIncident);
        
        // WHEN
        incidentAutoEnrollTask.processExpireCertificats();

        // THEN
        // On vérifie que la méthode de RECREATION est appelée
        verify(itomTaskService, times(1)).recreateIncidentAutoEnroll(any(RequestDto.class));
        verify(itomTaskService, never()).createIncidentAutoEnroll(any());
        verify(itomTaskSertvice, never()).updateIncidentPriority(any(), any());
    }


    // --- Méthode utilitaire pour créer des mocks de certificats ---
    private AutomationHubCertificateLightDto createMockCertificate(String commonName, int daysToExpire) {
        AutomationHubCertificateLightDto cert = new AutomationHubCertificateLightDto();
        cert.setCommonName(commonName);
        cert.setAutomationHubId("id-" + commonName);
        
        LocalDate expiryDate = LocalDate.now().plusDays(daysToExpire);
        cert.setExpiryDate(Date.from(expiryDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        
        return cert;
    }
}
