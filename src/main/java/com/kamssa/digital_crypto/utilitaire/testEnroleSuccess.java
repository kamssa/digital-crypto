import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutomationHubClientTest {

    // Dépendances mockées
    private final AutomationHubProfileService automationHubProfileService = mock(AutomationHubProfileService.class);
    private final EnrollPayloadBuilderFactory enrollPayloadBuilderFactory = mock(EnrollPayloadBuilderFactory.class);
    private final AutomationHubRestTemplate automationHubRestTemplate = mock(AutomationHubRestTemplate.class);
    private final CertificateResponseMapper certificateResponseMapper = mock(CertificateResponseMapper.class);

    // Classe à tester
    private final AutomationHubClient automationHubClient = new AutomationHubClient(
            automationHubProfileService,
            enrollPayloadBuilderFactory,
            automationHubRestTemplate,
            certificateResponseMapper
    );

    @Test
    void testEnrollCertificate_Success() throws Exception {
        // Préparer les mocks
        AutomationHubRequestDto requestDto = new AutomationHubRequestDto("testType", "testSubType", "certId123");
        AutomationHubProfileDto profileDto = new AutomationHubProfileDto();
        EnrollPayloadBuilder builder = mock(EnrollPayloadBuilder.class);
        EnrollPayloadDto payloadDto = new EnrollPayloadDto();
        ResponseRequestSubmitDto responseDto = new ResponseRequestSubmitDto();
        responseDto.setStatus("completed");
        responseDto.setCertificate(new CertificateDto("testCertId", "testCertValue", "testPkcs12"));

        when(automationHubProfileService.getProfileByTypeAndSubType("testType", "testSubType"))
                .thenReturn(profileDto);
        when(enrollPayloadBuilderFactory.createPayloadBuilder(requestDto, profileDto))
                .thenReturn(builder);
        when(builder.buildPayload()).thenReturn(payloadDto);
        when(automationHubRestTemplate.postForEntity(anyString(), eq(payloadDto), eq(ResponseRequestSubmitDto.class)))
                .thenReturn(responseDto);
        when(certificateResponseMapper.toAutomationHubCertificateDto(responseDto.getCertificate()))
                .thenReturn(new AutomationHubCertificateDto("testCertId", "testCertValue"));

        // Appeler la méthode
        AutomationHubCertificateDto result = automationHubClient.enrollCertificate(requestDto);

        // Vérifier les résultats
        assertNotNull(result);
        assertEquals("testCertId", result.getCertificateId());
        assertEquals("testCertValue", result.getCertificateValue());
        verify(automationHubProfileService).getProfileByTypeAndSubType("testType", "testSubType");
        verify(enrollPayloadBuilderFactory).createPayloadBuilder(requestDto, profileDto);
        verify(automationHubRestTemplate).postForEntity(anyString(), eq(payloadDto), eq(ResponseRequestSubmitDto.class));
    }

    @Test
    void testEnrollCertificate_FailedToRetrieveProfile() {
        AutomationHubRequestDto requestDto = new AutomationHubRequestDto("invalidType", "invalidSubType", "certId123");

        when(automationHubProfileService.getProfileByTypeAndSubType("invalidType", "invalidSubType"))
                .thenThrow(new FailedToRetrieveProfileException("Profile not found"));

        assertThrows(FailedToEnrollException.class, () -> automationHubClient.enrollCertificate(requestDto));

        verify(automationHubProfileService).getProfileByTypeAndSubType("invalidType", "invalidSubType");
    }

    // Ajoutez d'autres tests pour les cas d'erreurs spécifiques
}
