import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificateServiceTest {

    @Test
    void testGetCertificateByCnAndSerial_Success() throws FailedToGetCertificateException {
        // Mock de la méthode searchCertificates
        CertificateService service = Mockito.spy(new CertificateService());
        List<AutomationHubCertificateDto> mockResult = new ArrayList<>();
        mockResult.add(new AutomationHubCertificateDto());

        doReturn(mockResult).when(service).searchCertificates(any());

        // Appel de la méthode avec des paramètres factices
        AutomationHubCertificateDto result = service.getCertificateByCnAndSerial("testCN", "testSerial");

        // Assertions
        assertNotNull(result);
    }

    @Test
    void testGetCertificateByCnAndSerial_NoResults() {
        // Mock de la méthode searchCertificates
        CertificateService service = Mockito.spy(new CertificateService());
        doReturn(new ArrayList<>()).when(service).searchCertificates(any());

        // Test que l'exception est levée
        assertThrows(FailedToGetCertificateException.class, () -> {
            service.getCertificateByCnAndSerial("testCN", "testSerial");
        });
    }

    @Test
    void testGetCertificateByCnAndSerial_MultipleResults() {
        // Mock de la méthode searchCertificates
        CertificateService service = Mockito.spy(new CertificateService());
        List<AutomationHubCertificateDto> mockResult = new ArrayList<>();
        mockResult.add(new AutomationHubCertificateDto());
        mockResult.add(new AutomationHubCertificateDto());

        doReturn(mockResult).when(service).searchCertificates(any());

        // Test que l'exception est levée
        assertThrows(FailedToGetCertificateException.class, () -> {
            service.getCertificateByCnAndSerial("testCN", "testSerial");
        });
    }
}
