import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutomationHubClientTest {

    private final AutomationHubRestTemplate automationHubRestTemplate = mock(AutomationHubRestTemplate.class);
    private final CertificateResponseDtoMapper certificateResponseDtoMapper = mock(CertificateResponseDtoMapper.class);
    private final AutomationHubClient automationHubClient =
            new AutomationHubClient(automationHubRestTemplate, certificateResponseDtoMapper);

    @Test
    void testRevokeCertificateSuccessful() {
        // Arrange
        String certificatePem = "testPem";
        RevocationReason revocationReason = RevocationReason.KEY_COMPROMISE;

        ResponseRequestSubmitDto responseMock = new ResponseRequestSubmitDto();
        responseMock.setStatus(RequestSubmitStatusEnum.completed.name());
        CertificateDto certificateMock = new CertificateDto();
        certificateMock.setRevoked(true);
        responseMock.setCertificate(certificateMock);

        when(automationHubRestTemplate.postForEntity(
                eq("/requests/submit"),
                any(RevokePayloadDto.class),
                eq(ResponseRequestSubmitDto.class)
        )).thenReturn(responseMock);

        CertificateResponseDto expectedResponse = new CertificateResponseDto();
        when(certificateResponseDtoMapper.toAutomationHubCertificateDto(any(CertificateDto.class)))
                .thenReturn(expectedResponse);

        // Act
        CertificateResponseDto actualResponse = automationHubClient.revokeCertificate(certificatePem, revocationReason);

        // Assert
        assertEquals(expectedResponse, actualResponse);
        verify(automationHubRestTemplate, times(1)).postForEntity(anyString(), any(), any());
        verify(certificateResponseDtoMapper, times(1)).toAutomationHubCertificateDto(any());
    }

    @Test
    void testRevokeCertificateFailedStatus() {
        // Arrange
        String certificatePem = "testPem";
        RevocationReason revocationReason = RevocationReason.KEY_COMPROMISE;

        ResponseRequestSubmitDto responseMock = new ResponseRequestSubmitDto();
        responseMock.setStatus("failed");
        when(automationHubRestTemplate.postForEntity(
                eq("/requests/submit"),
                any(RevokePayloadDto.class),
                eq(ResponseRequestSubmitDto.class)
        )).thenReturn(responseMock);

        // Act & Assert
        FailedToRevokeException exception = assertThrows(FailedToRevokeException.class, () ->
                automationHubClient.revokeCertificate(certificatePem, revocationReason));
        assertTrue(exception.getMessage().contains("the revoke request is in status failed"));
    }

    @Test
    void testRevokeCertificateNotMarkedAsRevoked() {
        // Arrange
        String certificatePem = "testPem";
        RevocationReason revocationReason = RevocationReason.KEY_COMPROMISE;

        ResponseRequestSubmitDto responseMock = new ResponseRequestSubmitDto();
        responseMock.setStatus(RequestSubmitStatusEnum.completed.name());
        CertificateDto certificateMock = new CertificateDto();
        certificateMock.setRevoked(false);
        responseMock.setCertificate(certificateMock);

        when(automationHubRestTemplate.postForEntity(
                eq("/requests/submit"),
                any(RevokePayloadDto.class),
                eq(ResponseRequestSubmitDto.class)
        )).thenReturn(responseMock);

        // Act & Assert
        FailedToRevokeException exception = assertThrows(FailedToRevokeException.class, () ->
                automationHubClient.revokeCertificate(certificatePem, revocationReason));
        assertTrue(exception.getMessage().contains("the revoke request is completed but the returned certificate is not marked as revoked"));
    }
}
