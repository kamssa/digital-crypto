import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockedStatic;
import org.apache.commons.lang3.StringUtils;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AutomationHubServiceImplTest {

    @InjectMocks
    private AutomationHubServiceImpl automationHubService;

    @Mock
    private AutomationHubProfileService automationHubProfileService;

    @Mock
    private RestTemplate automationHubRestTemplate;

    @Mock
    private CertificateResponseDtoToAutomationHubCertificateDtoMapper certificateMapper;

    @Mock
    private EnrollPayloadBuilder enrollPayloadBuilder; // Car c'est une classe abstraite

    private AutomationHubRequestDto requestDto;
    private AutomationHubProfileDto profileDto;
    private ResponseRequestSubmitDto responseDto;

    @BeforeEach
    void setUp() {
        requestDto = new AutomationHubRequestDto();
        requestDto.setCertisId("12345");

        profileDto = new AutomationHubProfileDto();

        responseDto = new ResponseRequestSubmitDto();
        responseDto.setStatus(RequestSubmitStatusEnum.completed.name());
        CertificateDto certificateDto = new CertificateDto();
        certificateDto.setId("cert-id");
        responseDto.setCertificate(certificateDto);
    }

    @Test
    void shouldEnrollCertificateSuccessfully() {
        // Mock du service qui retourne un profil valide
        when(automationHubProfileService.getProfileByTypeAndSubType(any(), any()))
            .thenReturn(profileDto);

        // Mock de la méthode statique EnrollPayload.createPayloadBuilder
        try (MockedStatic<EnrollPayload> mockedStatic = mockStatic(EnrollPayload.class)) {
            mockedStatic.when(() -> EnrollPayload.createPayloadBuilder(any()))
                .thenReturn(enrollPayloadBuilder);

            when(enrollPayloadBuilder.buildPayload()).thenReturn(new EnrollPayloadDto());

            when(automationHubRestTemplate.postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class)))
                .thenReturn(new ResponseEntity<>(responseDto, HttpStatus.OK));

            when(certificateMapper.toAutomationHubCertificateDto(any()))
                .thenReturn(new AutomationHubCertificateDto());

            // Exécution
            AutomationHubCertificateDto result = automationHubService.enrollCertificate(requestDto);

            // Vérifications
            assertNotNull(result);
            verify(automationHubRestTemplate, times(1)).postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class));
        }
    }

    @Test
    void shouldThrowExceptionWhenProfileIsNull() {
        when(automationHubProfileService.getProfileByTypeAndSubType(any(), any()))
            .thenReturn(null);

        Exception exception = assertThrows(FailedToRetrieveProfileException.class, () ->
            automationHubService.enrollCertificate(requestDto)
        );

        assertTrue(exception.getMessage().contains("Failed to retrieve profile"));
    }

    @Test
    void shouldThrowExceptionWhenStatusIsNotCompleted() {
        responseDto.setStatus("FAILED");

        when(automationHubProfileService.getProfileByTypeAndSubType(any(), any()))
            .thenReturn(profileDto);

        when(automationHubRestTemplate.postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class)))
            .thenReturn(new ResponseEntity<>(responseDto, HttpStatus.OK));

        Exception exception = assertThrows(FailedToEnrollException.class, () ->
            automationHubService.enrollCertificate(requestDto)
        );

        assertTrue(exception.getMessage().contains("the enroll request is in status"));
    }

    @Test
    void shouldThrowExceptionWhenCertificateIsNull() {
        responseDto.setCertificate(null);

        when(automationHubProfileService.getProfileByTypeAndSubType(any(), any()))
            .thenReturn(profileDto);

        when(automationHubRestTemplate.postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class)))
            .thenReturn(new ResponseEntity<>(responseDto, HttpStatus.OK));

        Exception exception = assertThrows(FailedToEnrollException.class, () ->
            automationHubService.enrollCertificate(requestDto)
        );

        assertTrue(exception.getMessage().contains("the enroll request is completed but the returned certificate is null"));
    }
}