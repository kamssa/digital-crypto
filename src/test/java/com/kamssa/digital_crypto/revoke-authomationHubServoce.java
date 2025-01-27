@SpringBootTest
@ExtendWith(MockitoExtension.class)
class AutomationHubServiceImplTest {

    @InjectMocks
    private AutomationHubServiceImpl automationHubService;

    @Mock
    private AutomationHubClient automationHubClient;

    @Mock
    private CertificateRepository certificateRepository;

    @Test
    void testRevokeCertificate_Success() throws Exception {
        // Arrange
        AutomationHubRevokeRequestDto requestDto = new AutomationHubRevokeRequestDto();
        requestDto.setCertId("testCertId");
        requestDto.setRevocationReason("KEY_COMPROMISE");

        AutomationHubCertificateDte certificate = new AutomationHubCertificateDte();
        certificate.setPemEncodedCertificate("mockPemCertificate");

        when(certificateRepository.getCertificateByCommonNameAndSerial(
                anyString(), anyString())).thenReturn(certificate);

        when(automationHubClient.revokeCertificate(anyString(), anyString()))
                .thenReturn(true);

        // Act
        String result = automationHubService.revoke(requestDto);

        // Assert
        assertNotNull(result);
        assertEquals("mockPemCertificate", result);
        verify(automationHubClient, times(1))
                .revokeCertificate(eq("mockPemCertificate"), eq("KEY_COMPROMISE"));
    }

    @Test
    void testRevokeCertificate_Failure() {
        // Arrange
        AutomationHubRevokeRequestDto requestDto = new AutomationHubRevokeRequestDto();
        requestDto.setCertId("testCertId");
        requestDto.setRevocationReason("KEY_COMPROMISE");

        when(automationHubClient.revokeCertificate(anyString(), anyString()))
                .thenThrow(new FailedToRevokeException("Revocation failed"));

        // Act & Assert
        assertThrows(FailedToRevokeException.class, () -> {
            automationHubService.revoke(requestDto);
        });

        verify(automationHubClient, times(1))
                .revokeCertificate(anyString(), eq("KEY_COMPROMISE"));
    }
}
