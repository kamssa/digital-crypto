import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.ArrayList;

@ExtendWith(MockitoExtension.class)
public class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository; // Remplacez par la classe réelle

    @InjectMocks
    private CertificateService certificateService; // Remplacez par votre classe qui contient la méthode

    private AutomationHubCertificateDto certificate;

    @BeforeEach
    void setUp() {
        certificate = new AutomationHubCertificateDto();
        certificate.setCommonName("TestCN");
        certificate.setSerial("123456");
    }

    @Test
    void testGetCertificate_Success() throws Exception {
        List<AutomationHubCertificateDto> mockResult = new ArrayList<>();
        mockResult.add(certificate);

        when(certificateRepository.searchCertificates(any())).thenReturn(mockResult);

        AutomationHubCertificateDto result = certificateService.getCertificateByCnAndSerialAndId("TestCN", "123456", "ID123");

        assertNotNull(result);
        assertEquals("TestCN", result.getCommonName());
    }

    @Test
    void testGetCertificate_NotFound() {
        when(certificateRepository.searchCertificates(any())).thenReturn(new ArrayList<>());

        Exception exception = assertThrows(FailedToGetCertificateException.class, () -> {
            certificateService.getCertificateByCnAndSerialAndId("InvalidCN", "InvalidSerial", "ID123");
        });

        assertEquals("No certificate matching cn InvalidCN and serial InvalidSerial", exception.getMessage());
    }

    @Test
    void testGetCertificate_MultipleResults() {
        List<AutomationHubCertificateDto> mockResult = new ArrayList<>();
        mockResult.add(certificate);
        mockResult.add(new AutomationHubCertificateDto()); // Second certificat pour simuler un conflit

        when(certificateRepository.searchCertificates(any())).thenReturn(mockResult);

        Exception exception = assertThrows(FailedToGetCertificateException.class, () -> {
            certificateService.getCertificateByCnAndSerialAndId("TestCN", "123456", "ID123");
        });

        assertTrue(exception.getMessage().contains("More than one certificate matching"));
    }
}