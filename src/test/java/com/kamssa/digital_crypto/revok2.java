import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.example.dto.RevokePayloadDto;
import com.example.dto.ResponseRequestSubmitDto;
import com.example.dto.AutomationHubCertificateDto;
import com.example.exception.FailedToRevokeException;
import com.example.service.AutomationHubClient;
import com.example.util.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AutomationHubClientTest {

    @Mock
    private AutomationHubRestTemplate automationHubRestTemplate;

    @Mock
    private CertificateResponseMapper certificateResponseMapper;

    @InjectMocks
    private AutomationHubClient automationHubClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRevokeCertificateSuccess() throws Exception {
        // Arrange
        String certificatePem = "certificatePemMock";
        RevocationReason revocationReason = RevocationReason.COMPROMISED;

        RevokePayloadDto payload = new RevokePayloadDto();
        payload.setWorkflow("revoke");
        payload.setCertificatePem(certificatePem);
        payload.setTemplate(new RevokePayloadTemplateDto(revocationReason.name()));

        ResponseRequestSubmitDto responseDto = new ResponseRequestSubmitDto();
        responseDto.setStatus("completed");
        AutomationHubCertificateDto certificateDto = new AutomationHubCertificateDto();
        certificateDto.setRevoked(true);
        responseDto.setCertificate(certificateDto);

        when(automationHubRestTemplate.postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class)))
                .thenReturn(responseDto);
        when(certificateResponseMapper.toAutomationHubCertificateDto(any()))
                .thenReturn(new AutomationHubCertificateDto("certificateId", true));

        // Act
        AutomationHubCertificateDto result = automationHubClient.revokeCertificate(certificatePem, revocationReason);

        // Assert
        assertNotNull(result);
        assertTrue(result.isRevoked());
        verify(automationHubRestTemplate).postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class));
        verify(certificateResponseMapper).toAutomationHubCertificateDto(any());
    }

    @Test
    void testRevokeCertificateInvalidStatus() {
        // Arrange
        String certificatePem = "certificatePemMock";
        RevocationReason revocationReason = RevocationReason.COMPROMISED;

        ResponseRequestSubmitDto responseDto = new ResponseRequestSubmitDto();
        responseDto.setStatus("pending");

        when(automationHubRestTemplate.postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class)))
                .thenReturn(responseDto);

        // Act & Assert
        FailedToRevokeException exception = assertThrows(FailedToRevokeException.class, () -> {
            automationHubClient.revokeCertificate(certificatePem, revocationReason);
        });

        assertTrue(exception.getMessage().contains("the revoke request is in status pending"));
        verify(automationHubRestTemplate).postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class));
    }

    @Test
    void testRevokeCertificateNotRevoked() {
        // Arrange
        String certificatePem = "certificatePemMock";
        RevocationReason revocationReason = RevocationReason.COMPROMISED;

        ResponseRequestSubmitDto responseDto = new ResponseRequestSubmitDto();
        responseDto.setStatus("completed");
        AutomationHubCertificateDto certificateDto = new AutomationHubCertificateDto();
        certificateDto.setRevoked(false);
        responseDto.setCertificate(certificateDto);

        when(automationHubRestTemplate.postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class)))
                .thenReturn(responseDto);

        // Act & Assert
        FailedToRevokeException exception = assertThrows(FailedToRevokeException.class, () -> {
            automationHubClient.revokeCertificate(certificatePem, revocationReason);
        });

        assertTrue(exception.getMessage().contains("the revoke request is completed but the returned certificate is not marked as revoked"));
        verify(automationHubRestTemplate).postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class));
    }
}