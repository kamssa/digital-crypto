@ExtendWith(MockitoExtension.class)
class AutomationHubServiceImplTest {

    @InjectMocks
    private AutomationHubServiceImpl automationHubService;

    @Mock
    private AutomationHubClient automationHubClient;

    @Mock
    private CertificateRepository certificateRepository; // Remplace selon ton implémentation

    @Test
    void testRevoke_Success() throws Exception {
        // GIVEN
        AutomationHubRevokeRequestDto requestDto = new AutomationHubRevokeRequestDto();
        requestDto.setCommonName("testCN");
        requestDto.setSerial("123456");

        AutomationHubCertificateDto certificateDto = new AutomationHubCertificateDto();
        certificateDto.setPemEncodedCertificate("testPem");

        when(certificateRepository.getCertificateByCnAndSerial(anyString(), anyString()))
                .thenReturn(certificateDto);

        doNothing().when(automationHubClient).revokeCertificate(anyString(), any());

        // WHEN
        AutomationHubCertificateDto result = automationHubService.revoke(requestDto);

        // THEN
        assertNotNull(result);
        assertEquals("testPem", result.getPemEncodedCertificate());

        verify(certificateRepository, times(1)).getCertificateByCnAndSerial("testCN", "123456");
        verify(automationHubClient, times(1)).revokeCertificate("testPem", RevocationReason.UNSPECIFIED);
    }

    @Test
    void testRevoke_CertificateNotFound() {
        // GIVEN
        AutomationHubRevokeRequestDto requestDto = new AutomationHubRevokeRequestDto();
        requestDto.setCommonName("testCN");
        requestDto.setSerial("123456");

        when(certificateRepository.getCertificateByCnAndSerial(anyString(), anyString()))
                .thenReturn(null);

        // WHEN & THEN
        assertThrows(FailedToRevokeException.class, () -> automationHubService.revoke(requestDto));

        verify(certificateRepository, times(1)).getCertificateByCnAndSerial("testCN", "123456");
        verify(automationHubClient, never()).revokeCertificate(anyString(), any());
    }
}
/////////////////////////////////////////////////////////////////////////////////////////////////////
@ExtendWith(MockitoExtension.class)
class AutomationHubServiceImplTest {

    @InjectMocks
    private AutomationHubServiceImpl automationHubService;

    @Mock
    private AutomationHubClient automationHubClient;

    @Mock
    private CertificateRepository certificateRepository; // À adapter selon ton code

    @Test
    void testRevoke_ThrowsRevokeUnexistingCertificateException() {
        // GIVEN
        AutomationHubRevokeRequestDto requestDto = new AutomationHubRevokeRequestDto();
        requestDto.setCommonName("testCN");
        requestDto.setSerial("123456");

        AutomationHubCertificateDto certificateDto = new AutomationHubCertificateDto();
        certificateDto.setPemEncodedCertificate("testPem");

        when(certificateRepository.getCertificateByCnAndSerial(anyString(), anyString()))
                .thenReturn(certificateDto);

        FailedCallException mockException = mock(FailedCallException.class);
        when(mockException.getErrorCode()).thenReturn(ErrorCodeEnum.REQ009.getValue());
        
        doThrow(mockException).when(automationHubClient)
                .revokeCertificate(anyString(), any());

        // WHEN & THEN
        assertThrows(RevokeUnexistingCertificateException.class, () -> 
            automationHubService.revoke(requestDto)
        );

        verify(certificateRepository, times(1)).getCertificateByCnAndSerial("testCN", "123456");
        verify(automationHubClient, times(1)).revokeCertificate("testPem", RevocationReason.UNSPECIFIED);
    }

    @Test
    void testRevoke_ThrowsRevokeAlreadyRevokedException() {
        // GIVEN
        AutomationHubRevokeRequestDto requestDto = new AutomationHubRevokeRequestDto();
        requestDto.setCommonName("testCN");
        requestDto.setSerial("123456");

        AutomationHubCertificateDto certificateDto = new AutomationHubCertificateDto();
        certificateDto.setPemEncodedCertificate("testPem");

        when(certificateRepository.getCertificateByCnAndSerial(anyString(), anyString()))
                .thenReturn(certificateDto);

        FailedCallException mockException = mock(FailedCallException.class);
        when(mockException.getTitle()).thenReturn("Certificate is already revoked");

        doThrow(mockException).when(automationHubClient)
                .revokeCertificate(anyString(), any());

        // WHEN & THEN
        assertThrows(RevokeAlreadyRevokedException.class, () -> 
            automationHubService.revoke(requestDto)
        );

        verify(certificateRepository, times(1)).getCertificateByCnAndSerial("testCN", "123456");
        verify(automationHubClient, times(1)).revokeCertificate("testPem", RevocationReason.UNSPECIFIED);
    }

    @Test
    void testRevoke_ThrowsFailedToRevokeException() {
        // GIVEN
        AutomationHubRevokeRequestDto requestDto = new AutomationHubRevokeRequestDto();
        requestDto.setCommonName("testCN");
        requestDto.setSerial("123456");

        AutomationHubCertificateDto certificateDto = new AutomationHubCertificateDto();
        certificateDto.setPemEncodedCertificate("testPem");

        when(certificateRepository.getCertificateByCnAndSerial(anyString(), anyString()))
                .thenReturn(certificateDto);

        FailedCallException mockException = mock(FailedCallException.class);
        when(mockException.getMessage()).thenReturn("Unexpected error occurred");

        doThrow(mockException).when(automationHubClient)
                .revokeCertificate(anyString(), any());

        // WHEN & THEN
        assertThrows(FailedToRevokeException.class, () -> 
            automationHubService.revoke(requestDto)
        );

        verify(certificateRepository, times(1)).getCertificateByCnAndSerial("testCN", "123456");
        verify(automationHubClient, times(1)).revokeCertificate("testPem", RevocationReason.UNSPECIFIED);
    }
}
////////////////////////////////////////////////////////////////////
package com.example.automationhub;

import static org.mockito.BDDMockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutomationHubServiceImplTest {

    @InjectMocks
    private AutomationHubServiceImpl automationHubService;

    @Mock
    private AutomationHubClient automationHubClient;

    @Mock
    private CertificateRepository certificateRepository;

    @Test
    void testRevoke_ThrowsRevokeUnexistingCertificateException() {
        // GIVEN
        AutomationHubRevokeRequestDto requestDto = new AutomationHubRevokeRequestDto();
        requestDto.setCommonName("testCN");
        requestDto.setSerial("123456");

        AutomationHubCertificateDto certificateDto = new AutomationHubCertificateDto();
        certificateDto.setPemEncodedCertificate("testPem");

        given(certificateRepository.getCertificateByCnAndSerial(anyString(), anyString()))
                .willReturn(certificateDto);

        FailedCallException mockException = mock(FailedCallException.class);
        given(mockException.getErrorCode()).willReturn(ErrorCodeEnum.REQ009.getValue());

        willThrow(mockException).given(automationHubClient)
                .revokeCertificate(anyString(), any());

        // WHEN & THEN
        assertThrows(RevokeUnexistingCertificateException.class, () -> 
            automationHubService.revoke(requestDto)
        );

        then(certificateRepository).should(times(1)).getCertificateByCnAndSerial("testCN", "123456");
        then(automationHubClient).should(times(1)).revokeCertificate("testPem", RevocationReason.UNSPECIFIED);
    }

    @Test
    void testRevoke_ThrowsRevokeAlreadyRevokedException() {
        // GIVEN
        AutomationHubRevokeRequestDto requestDto = new AutomationHubRevokeRequestDto();
        requestDto.setCommonName("testCN");
        requestDto.setSerial("123456");

        AutomationHubCertificateDto certificateDto = new AutomationHubCertificateDto();
        certificateDto.setPemEncodedCertificate("testPem");

        given(certificateRepository.getCertificateByCnAndSerial(anyString(), anyString()))
                .willReturn(certificateDto);

        FailedCallException mockException = mock(FailedCallException.class);
        given(mockException.getTitle()).willReturn("Certificate is already revoked");

        willThrow(mockException).given(automationHubClient)
                .revokeCertificate(anyString(), any());

        // WHEN & THEN
        assertThrows(RevokeAlreadyRevokedException.class, () -> 
            automationHubService.revoke(requestDto)
        );

        then(certificateRepository).should(times(1)).getCertificateByCnAndSerial("testCN", "123456");
        then(automationHubClient).should(times(1)).revokeCertificate("testPem", RevocationReason.UNSPECIFIED);
    }

    @Test
    void testRevoke_ThrowsFailedToRevokeException() {
        // GIVEN
        AutomationHubRevokeRequestDto requestDto = new AutomationHubRevokeRequestDto();
        requestDto.setCommonName("testCN");
        requestDto.setSerial("123456");

        AutomationHubCertificateDto certificateDto = new AutomationHubCertificateDto();
        certificateDto.setPemEncodedCertificate("testPem");

        given(certificateRepository.getCertificateByCnAndSerial(anyString(), anyString()))
                .willReturn(certificateDto);

        FailedCallException mockException = mock(FailedCallException.class);
        given(mockException.getMessage()).willReturn("Unexpected error occurred");

        willThrow(mockException).given(automationHubClient)
                .revokeCertificate(anyString(), any());

        // WHEN & THEN
        assertThrows(FailedToRevokeException.class, () -> 
            automationHubService.revoke(requestDto)
        );

        then(certificateRepository).should(times(1)).getCertificateByCnAndSerial("testCN", "123456");
        then(automationHubClient).should(times(1)).revokeCertificate("testPem", RevocationReason.UNSPECIFIED);
    }
}
