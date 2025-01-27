import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.example.dto.AutomationHubCertificateDto;
import com.example.dto.AutomationHubProfileDto;
import com.example.dto.EnrollPayloadDto;
import com.example.dto.ResponseRequestSubmitDto;
import com.example.exception.FailedToEnrollException;
import com.example.exception.FailedToRetrieveProfileException;
import com.example.service.AutomationHubClient;
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

    @Mock
    private CertificateResponseMapper certificateResponseMapper;

    @InjectMocks
    private AutomationHubClient automationHubClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testEnrollCertificateSuccess() throws Exception {
        // Arrange
        AutomationHubRequestDto automationHubRequestDto = new AutomationHubRequestDto();
        automationHubRequestDto.setCertisId("123");
        automationHubRequestDto.setType("type");
        automationHubRequestDto.setSubType("subType");

        AutomationHubProfileDto automationHubProfileDto = new AutomationHubProfileDto();
        EnrollPayloadDto enrollPayloadDto = new EnrollPayloadDto();
        ResponseRequestSubmitDto responseDto = new ResponseRequestSubmitDto();
        responseDto.setStatus("completed");
        responseDto.setCertificate(new ResponseCertificateDto("certId", "pkcs12Data"));

        AutomationHubCertificateDto expectedCertificateDto = new AutomationHubCertificateDto("certId", true);

        when(automationHubProfileService.getProfileByTypeAndSubType("type", "subType"))
                .thenReturn(automationHubProfileDto);
        when(enrollPayloadBuilderFactory.createPayloadBuilder(any(), any()))
                .thenReturn(new EnrollPayloadBuilder());
        when(automationHubRestTemplate.postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class)))
                .thenReturn(responseDto);
        when(certificateResponseMapper.toAutomationHubCertificateDto(any()))
                .thenReturn(expectedCertificateDto);

        // Act
        AutomationHubCertificateDto result = automationHubClient.enrollCertificate(automationHubRequestDto);

        // Assert
        assertNotNull(result);
        assertEquals("certId", result.getCertificateId());
        assertTrue(result.isPrivateKey());
        verify(automationHubProfileService).getProfileByTypeAndSubType("type", "subType");
        verify(automationHubRestTemplate).postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class));
        verify(certificateResponseMapper).toAutomationHubCertificateDto(any());
    }

    @Test
    void testEnrollCertificateFailedToRetrieveProfile() {
        // Arrange
        AutomationHubRequestDto automationHubRequestDto = new AutomationHubRequestDto();
        automationHubRequestDto.setCertisId("123");
        automationHubRequestDto.setType("type");
        automationHubRequestDto.setSubType("subType");

        when(automationHubProfileService.getProfileByTypeAndSubType("type", "subType"))
                .thenThrow(new FailedToRetrieveProfileException("Profile not found"));

        // Act & Assert
        FailedToEnrollException exception = assertThrows(FailedToEnrollException.class, () -> {
            automationHubClient.enrollCertificate(automationHubRequestDto);
        });

        assertTrue(exception.getMessage().contains("Failed to retrieve profile"));
        verify(automationHubProfileService).getProfileByTypeAndSubType("type", "subType");
    }

    @Test
    void testEnrollCertificateInvalidStatus() {
        // Arrange
        AutomationHubRequestDto automationHubRequestDto = new AutomationHubRequestDto();
        automationHubRequestDto.setCertisId("123");
        automationHubRequestDto.setType("type");
        automationHubRequestDto.setSubType("subType");

        AutomationHubProfileDto automationHubProfileDto = new AutomationHubProfileDto();
        EnrollPayloadDto enrollPayloadDto = new EnrollPayloadDto();
        ResponseRequestSubmitDto responseDto = new ResponseRequestSubmitDto();
        responseDto.setStatus("pending");

        when(automationHubProfileService.getProfileByTypeAndSubType("type", "subType"))
                .thenReturn(automationHubProfileDto);
        when(enrollPayloadBuilderFactory.createPayloadBuilder(any(), any()))
                .thenReturn(new EnrollPayloadBuilder());
        when(automationHubRestTemplate.postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class)))
                .thenReturn(responseDto);

        // Act & Assert
        FailedToEnrollException exception = assertThrows(FailedToEnrollException.class, () -> {
            automationHubClient.enrollCertificate(automationHubRequestDto);
        });

        assertTrue(exception.getMessage().contains("the enroll request is in status pending"));
        verify(automationHubRestTemplate).postForEntity(anyString(), any(), eq(ResponseRequestSubmitDto.class));
    }
}