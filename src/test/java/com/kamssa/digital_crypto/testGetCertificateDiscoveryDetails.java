@RunWith(SpringRunner.class)
@SpringBootTest
public class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository; // Remplacez avec le bon repository ou service dépendant.

    @InjectMocks
    private CertificateService certificateService; // Remplacez avec le service réel que vous testez.

    @Test
    public void testGetCertificateDiscoveryDetails() throws FailedToGetCertificateException {
        // Préparation des mocks
        String certificateId = "12345";
        AutomationHubCertificateDto mockCertificateDto = new AutomationHubCertificateDto();
        List<CertificateDiscoveryDataDto> mockDiscoveryDataList = new ArrayList<>();
        CertificateDiscoveryDataDto mockDiscoveryData = new CertificateDiscoveryDataDto();
        mockDiscoveryData.setIp("192.168.1.1");
        mockDiscoveryDataList.add(mockDiscoveryData);
        mockCertificateDto.setDiscoveryDatas(mockDiscoveryDataList);

        List<DiscoveryInfoDto> mockDiscoveryInfoList = new ArrayList<>();
        DiscoveryInfoDto mockDiscoveryInfo = new DiscoveryInfoDto();
        mockDiscoveryInfo.setLastDiscoveryDate(new Date());
        mockDiscoveryInfoList.add(mockDiscoveryInfo);
        mockCertificateDto.setDiscoveryInfos(mockDiscoveryInfoList);

        Mockito.when(certificateRepository.getCertificateById(certificateId))
               .thenReturn(mockCertificateDto);

        // Exécution de la méthode à tester
        List<DiscoveryDetailDto> result = certificateService.getCertificateDiscoveryDetails(certificateId);

        // Assertions
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("192.168.1.1", result.get(0).getIp());
        Assert.assertNotNull(result.get(0).getLastDiscoveryDate());
    }
}