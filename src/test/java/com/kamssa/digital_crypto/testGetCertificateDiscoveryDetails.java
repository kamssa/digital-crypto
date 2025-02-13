import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class CertificateServiceImplTest {

    @Mock
    private CertificateRepository certificateRepository; 
    // ou le service qui contient vraiment getCertificateById si c’est ailleurs

    @InjectMocks
    private CertificateServiceImpl certificateService;

    @Test
    void testGetCertificateDiscoveryDetails() throws FailedToGetCertificateException {
        // GIVEN: Préparation de l’objet retourné par getCertificateById(...)
        AutomationHubCertificateDto mockCertificate = new AutomationHubCertificateDto();

        // Simulons la liste de DiscoveryDatas
        List<DiscoveryDataDto> discoveryDatas = new ArrayList<>();
        DiscoveryDataDto dataDto = new DiscoveryDataDto();
        discoveryDatas.add(dataDto);
        mockCertificate.setDiscoveryDatas(discoveryDatas);

        // Simulons la liste de DiscoveryInfos
        List<DiscoveryInfoDto> discoveryInfos = new ArrayList<>();
        DiscoveryInfoDto infoDto = new DiscoveryInfoDto();
        infoDto.setLastDiscoveryDate(1672531200000L); // 01/01/2023 en timestamp
          .add(infoDto);
        mockCertificate.setDiscoveryInfos(discoveryInfos);

        // On configure le mock : quand on appelle certificateRepository.findById("testId"),
        // on renvoie notre objet factice
        Mockito.when(certificateRepository.findById("testId"))
               .thenReturn(mockCertificate);

        // WHEN: Appel de la méthode à tester
        List<DiscoveryDetailDto> result = certificateService.getCertificateDiscoveryDetails("testId");

        // THEN: Vérifications
        Assertions.assertNotNull(result, "La liste ne doit pas être nulle");
        Assertions.assertEquals(1, result.size(), "On attend une seule DiscoveryDetailDto");
        Assertions.assertNotNull(result.get(0).getDiscoveryDate(), 
                                 "La date de découverte doit être renseignée");
    }
}