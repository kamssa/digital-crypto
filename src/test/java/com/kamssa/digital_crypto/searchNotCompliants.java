import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository; // Remplacez par la classe réelle

    @InjectMocks
    private CertificateService certificateService; // Remplacez par votre classe contenant `searchNotCompliants`

    private List<AutomationHubCertificateDto> mockCertificates;

    @BeforeEach
    void setUp() {
        mockCertificates = new ArrayList<>();
        AutomationHubCertificateDto cert1 = new AutomationHubCertificateDto();
        cert1.setModule("DISCOVERY");
        cert1.setStatus("VALID");
        mockCertificates.add(cert1);
    }

    @Test
    void testSearchNotCompliants_WithQuery() {
        Optional<String> nonCompliantQuery = Optional.of("test-query");

        when(certificateRepository.searchCertificates(any())).thenReturn(mockCertificates);

        List<AutomationHubCertificateDto> result = certificateService.searchNotCompliants();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("DISCOVERY", result.get(0).getModule());
    }

    @Test
    void testSearchNotCompliants_WithException() {
        when(certificateRepository.searchCertificates(any())).thenThrow(new FailedToSearchCertificatesException("Test Exception"));

        assertThrows(FailedToSearchCertificatesException.class, () -> {
            certificateService.searchNotCompliants();
        });
    }

    @Test
    void testSearchNotCompliants_EmptyResult() {
        when(certificateRepository.searchCertificates(any())).thenReturn(new ArrayList<>());

        List<AutomationHubCertificateDto> result = certificateService.searchNotCompliants();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}