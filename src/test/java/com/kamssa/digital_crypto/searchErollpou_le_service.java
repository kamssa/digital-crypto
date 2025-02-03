import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.text.SimpleDateFormat;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository; // Mock du repository

    @Mock
    private DateUtils dateUtils; // Mock de l'utilitaire de date

    @InjectMocks
    private CertificateService certificateService; // Service contenant la méthode à tester

    private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    private final Date yesterday = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
    private final String formattedDate = format.format(yesterday);

    @BeforeEach
    void setUp() {
        // Simuler le comportement de DateUtils.addDays()
        when(dateUtils.addDays(any(Date.class), eq(-1))).thenReturn(yesterday);
    }

    @Test
    void shouldSearchWithRawQueryWhenAutoEnrollQueryIsPresent() {
        // 🔹 Simuler la présence d'une requête automatique
        Optional<String> autoEnrollQuery = Optional.of("dateJ-1 condition");

        // 🔹 Simuler la réponse attendue
        List<AutomationHubCertificateDto> expectedCertificates = Collections.singletonList(new AutomationHubCertificateDto());
        when(certificateRepository.searchCertificates(any())).thenReturn(expectedCertificates);

        // 🔹 Exécution
        List<AutomationHubCertificateDto> result = certificateService.searchAutoEnroll(true);

        // 🔹 Vérification
        assertNotNull(result);
        assertFalse(result.isEmpty());
        verify(certificateRepository).searchCertificates(argThat(request -> 
            request.getRawQuery().equals("dateJ-1 condition".replace("dateJ-1", formattedDate))
        ));
    }

    @Test
    void shouldSearchWithCriteriaWhenAutoEnrollQueryIsEmpty() {
        // 🔹 Simuler l'absence de requête automatique
        Optional<String> autoEnrollQuery = Optional.empty();

        // 🔹 Simuler la réponse attendue
        List<AutomationHubCertificateDto> expectedCertificates = Arrays.asList(new AutomationHubCertificateDto(), new AutomationHubCertificateDto());
        when(certificateRepository.searchCertificates(any())).thenReturn(expectedCertificates);

        // 🔹 Exécution
        List<AutomationHubCertificateDto> result = certificateService.searchAutoEnroll(false);

        // 🔹 Vérification
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(certificateRepository).searchCertificates(argThat(request -> 
            request.getCriterionList().size() > 0
        ));
    }

    @Test
    void shouldReturnEmptyListWhenNoCertificatesFound() {
        // 🔹 Simuler aucune correspondance
        when(certificateRepository.searchCertificates(any())).thenReturn(Collections.emptyList());

        // 🔹 Exécution
        List<AutomationHubCertificateDto> result = certificateService.searchAutoEnroll(false);

        // 🔹 Vérification
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}