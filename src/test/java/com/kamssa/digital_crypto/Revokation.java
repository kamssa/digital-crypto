import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RunWith(MockitoJUnitRunner.class)
public class CertificateServiceTest {

    @Mock
    private RestTemplate automationhubRestTemplate;

    @Mock
    private Mapper<ResponseDto, AutomationHubCertificateDto> certMapper; // Replace with your actual mapper

    @InjectMocks
    private CertificateService certificateService; // Replace with the actual service class

    @Test
    public void testRevokeCertificate() throws FailedCallException {
        // Arrange
        String certificateId = "test-certificate-id";
        RevocationReason reason = RevocationReason.EXPIRED;
        
        RevocationRequestDto requestDto = new RevocationRequestDto();
        requestDto.setCertificateId(certificateId);
        requestDto.setReason(reason.name());

        ResponseEntity<ResponseDto> mockResponseEntity = 
            new ResponseEntity<>(new ResponseDto(), HttpStatus.OK);
        
        when(automationhubRestTemplate.postForEntity(anyString(), any(), eq(ResponseDto.class)))
            .thenReturn(mockResponseEntity);
        
        when(certMapper.map(any(ResponseDto.class))).thenReturn(new AutomationHubCertificateDto());

        // Act
        AutomationHubCertificateDto result = certificateService.revokeCertificate(certificateId, reason);

        // Assert
        assertNotNull(result);
        verify(automationhubRestTemplate).postForEntity(
            contains("/requests/someExpectedPath"), // Adjust the path
            any(),
            eq(ResponseDto.class)
        );

        verify(certMapper).map(any(ResponseDto.class));
    }
}