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
public class CertificateSearchServiceTest {

    @Mock
    private RestTemplate restTemplatePost;

    @Mock
    private Mapper<SearchResponseDto, SearchPayloadDto> searchResponseDtoMapper;

    @InjectMocks
    private CertificateService certificateService; // Replace with your service class

    @Test
    public void testSearchCertificates() throws FailedCallException {
        
        SearchCertificateRequestDto requestDto = new SearchCertificateRequestDto();
        requestDto.setVisa("sampleVisa");
        SearchPayloadDto expectedPayload = new SearchPayloadDto();

        // Setting up mock response from RestTemplate
        SearchResponseDto mockSearchResponseDto = new SearchResponseDto();
        ResponseEntity<SearchResponseDto> mockResponseEntity =
            new ResponseEntity<>(mockSearchResponseDto, HttpStatus.OK);
        
        when(restTemplatePost.postForEntity(
                anyString(), 
                any(), 
                eq(SearchResponseDto.class)))
            .thenReturn(mockResponseEntity);

        when(searchResponseDtoMapper.map(any(SearchResponseDto.class)))
            .thenReturn(expectedPayload);

        SearchPayloadDto result = certificateService.searchCertificates(requestDto);

        assertNotNull(result);
        assertEquals(expectedPayload, result);

        verify(restTemplatePost).postForEntity(
            contains("/certificates/certs"), 
            any(), 
            eq(SearchResponseDto.class));
        verify(searchResponseDtoMapper).map(mockSearchResponseDto);
    }
}