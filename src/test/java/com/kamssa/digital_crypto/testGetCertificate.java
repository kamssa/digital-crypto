import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;

@RunWith(MockitoJUnitRunner.class)
public class CertificateServiceTest {

    @Mock
    private RestTemplate automationhubRestTemplate;

    @Mock
    private Mapper<RetrieveCertificateResponseDto, AutomationHubCertificateDto> certificateResponseDtoToAutomationHubCertificateDtoMapper;

    @InjectMocks
    private CertificateService certificateService;  // Remplacez par le nom de votre classe

    @Before
    public void setUp() {
        // Setup initial objects if needed
    }

    @Test
    public void testGetCertificate() throws FailedCallException {
        String id = "test-id";
        RetrieveCertificateResponseDto mockResponseDto = new RetrieveCertificateResponseDto();
        AutomationHubCertificateDto mockMappedDto = new AutomationHubCertificateDto();
        
        ResponseEntity<RetrieveCertificateResponseDto> mockResponseEntity = 
            new ResponseEntity<>(mockResponseDto, HttpStatus.OK);
        
        when(automationhubRestTemplate.getForEntity(anyString(), eq(RetrieveCertificateResponseDto.class)))
            .thenReturn(mockResponseEntity);
        
        when(certificateResponseDtoToAutomationHubCertificateDtoMapper.toAutomationHubCertificateDto(any()))
            .thenReturn(mockMappedDto);

        AutomationHubCertificateDto result = certificateService.getCertificate(id);

        assertNotNull(result);
        assertEquals(mockMappedDto, result);
        
        verify(automationhubRestTemplate).getForEntity(contains("/certificates/"), eq(RetrieveCertificateResponseDto.class));
        verify(certificateResponseDtoToAutomationHubCertificateDtoMapper).toAutomationHubCertificateDto(mockResponseDto);
    }
}