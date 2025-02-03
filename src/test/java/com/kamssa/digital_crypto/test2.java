import com.example.service.AutomationHubProfileService;
import com.example.util.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AutomationHubClientTest {

    @Mock
    private AutomationHubProfileService automationHubProfileService;

    @Mock
    private EnrollPayloadBuilderFactory enrollPayloadBuilderFactory;

    @Mock
    private AutomationHubRestTemplate automationHubRestTemplate;

    @InjectMocks
    private AutomationHubClient automationHubClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
  
    @Test
    void testEnrollCertificateSuccess() throws Exception {
        // Arrange
        AutomationHubRequestDto requestDto = new AutomationHubRequestDto();
        requestDto.setCertIsId("cert123");
        requestDto.setType("type1");
        requestDto.setSubType("subType1");

        AutomationHubProfileDto profileDto = new AutomationHubProfileDto();
        EnrollPayloadBuilder payloadBuilder = mock(EnrollPayloadBuilder.class);
        ResponseRequestSubmitDto responseDto = new ResponseRequestSubmitDto();
        responseDto.setStatus("completed");
        responseDto.setCertificate(new AutomationHubCertificateDto("certificateId"));

        when(automationHubProfileService.getProfileByTypeAndSubType("type1", "subType1"))
                .thenReturn(profileDto);
        when(enrollPayloadBuilderFactory.createPayloadBuilder(requestDto, profileDto))
                .thenReturn(payloadBuilder);
        when(payloadBuilder.buildPayload()).thenReturn(new Object());
        when(automationHubRestTemplate.postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class)))
                .thenReturn(responseDto);

        // Act
        AutomationHubCertificateDto result = automationHubClient.enrollCertificate(requestDto);

        // Assert
        assertNotNull(result);
        assertEquals("certificateId", result.getId());
        verify(automationHubProfileService).getProfileByTypeAndSubType("type1", "subType1");
        verify(enrollPayloadBuilderFactory).createPayloadBuilder(requestDto, profileDto);
        verify(automationHubRestTemplate).postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class));
    }

    @Test
    void testEnrollCertificateFailedToRetrieveProfile() {
        // Arrange
        AutomationHubRequestDto requestDto = new AutomationHubRequestDto();
        requestDto.setCertIsId("cert123");
        requestDto.setType("type1");
        requestDto.setSubType("subType1");

        when(automationHubProfileService.getProfileByTypeAndSubType("type1", "subType1"))
                .thenThrow(new FailedToRetrieveProfileException("Profile not found"));

        // Act & Assert
        FailedToEnrollException exception = assertThrows(FailedToEnrollException.class, () -> {
            automationHubClient.enrollCertificate(requestDto);
        });

        assertEquals("Failed to retrieve profile", exception.getMessage());
        verify(automationHubProfileService).getProfileByTypeAndSubType("type1", "subType1");
    }

    @Test
    void testEnrollCertificateIncompleteStatus() {
        // Arrange
        AutomationHubRequestDto requestDto = new AutomationHubRequestDto();
        requestDto.setCertIsId("cert123");
        requestDto.setType("type1");
        requestDto.setSubType("subType1");

        AutomationHubProfileDto profileDto = new AutomationHubProfileDto();
        EnrollPayloadBuilder payloadBuilder = mock(EnrollPayloadBuilder.class);
        ResponseRequestSubmitDto responseDto = new ResponseRequestSubmitDto();
        responseDto.setStatus("incomplete");

        when(automationHubProfileService.getProfileByTypeAndSubType("type1", "subType1"))
                .thenReturn(profileDto);
        when(enrollPayloadBuilderFactory.createPayloadBuilder(requestDto, profileDto))
                .thenReturn(payloadBuilder);
        when(payloadBuilder.buildPayload()).thenReturn(new Object());
        when(automationHubRestTemplate.postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class)))
                .thenReturn(responseDto);

        // Act & Assert
        FailedToEnrollException exception = assertThrows(FailedToEnrollException.class, () -> {
            automationHubClient.enrollCertificate(requestDto);
        });

        assertTrue(exception.getMessage().contains("The enroll request is in status incomplete"));
        verify(automationHubRestTemplate).postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class));
    }
}