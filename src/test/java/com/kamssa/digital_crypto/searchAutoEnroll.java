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
import java.util.Date;

@ExtendWith(MockitoExtension.class)
public class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository; // Remplacez par la classe réelle

    @InjectMocks
    private CertificateService certificateService; // Remplacez par votre classe contenant `searchAutoEnroll`

    private List<AutomationHubCertificateDto> mockCertificates;

    @BeforeEach
    void setUp() {
        mockCertificates = new ArrayList<>();
        AutomationHubCertificateDto cert1 = new AutomationHubCertificateDto();
        cert1.setModule("WEBRA");
        cert1.setStatus("VALID");
        mockCertificates.add(cert1);
    }

    @Test
    void testSearchAutoEnroll_WithRawQuery() {
        Optional<String> rawQuery = Optional.of("data0-1");

        when(certificateRepository.searchCertificates(any())).thenReturn(mockCertificates);

        List<AutomationHubCertificateDto> result = certificateService.searchAutoEnroll(true);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("VALID", result.get(0).getStatus());
    }

    @Test
    void testSearchAutoEnroll_WithCriteriaList() {
        when(certificateRepository.searchCertificates(any())).thenReturn(mockCertificates);

        List<AutomationHubCertificateDto> result = certificateService.searchAutoEnroll(false);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("WEBRA", result.get(0).getModule());
    }

    @Test
    void testSearchAutoEnroll_EmptyResult() {
        when(certificateRepository.searchCertificates(any())).thenReturn(new ArrayList<>());

        List<AutomationHubCertificateDto> result = certificateService.searchAutoEnroll(false);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}