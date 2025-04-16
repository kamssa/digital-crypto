@Override
public List<AutomationHubCertificateDto> searchNotCompliantsTest() {
    int page = 1;
    int size = 2;

    if (!nonCompliantDiscoveryQuery.isPresent()) {
        throw new NotImplementedException("You must set the query from config because OR filters are not implemented yet");
    }

    List<AutomationHubCertificateDto> allResults = new ArrayList<>();
    boolean hasMore;

    do {
        SearchCertificateRequestDto requestDto = buildRequestDto(page, size);
        List<AutomationHubCertificateDto> pageResults = searchCertificates(requestDto);

        allResults.addAll(pageResults);
        hasMore = pageResults.size() == size;
        page++;

    } while (hasMore);

    return allResults;
}

private SearchCertificateRequestDto buildRequestDto(int page, int size) {
    // À terme tu peux enrichir ici selon le cas (module, status, OR logic…)
    return new SearchCertificateRequestDto(nonCompliantDiscoveryQuery.get(), page, size);
}
//////////////////////////////////
@Override
public List<AutomationHubCertificateDto> searchNotCompliantsTest() {
    int page = 1;
    int size = 1000;
    int maxElements = 100_000;

    if (!nonCompliantDiscoveryQuery.isPresent()) {
        throw new NotImplementedException("Discovery query not configured.");
    }

    List<AutomationHubCertificateDto> allResults = new ArrayList<>();
    boolean hasMore;

    do {
        SearchCertificateRequestDto requestDto = buildRequestDto(page, size);
        List<AutomationHubCertificateDto> pageResults = searchCertificates(requestDto);

        allResults.addAll(pageResults);
        hasMore = pageResults.size() == size && allResults.size() < maxElements;
        page++;

        System.out.println("Page " + (page - 1) + " fetched, total so far: " + allResults.size());

    } while (hasMore);

    return allResults;
}

private SearchCertificateRequestDto buildRequestDto(int page, int size) {
    return new SearchCertificateRequestDto(nonCompliantDiscoveryQuery.get(), page, size);
}
🧠 Ce que ça fait :
Récupère les certificats par paquets de 1000

Continue tant que le résultat contient 1000 éléments et que tu n’as pas dépassé les 100 000

S'arrête automatiquement à la fin des résultats ou à 100k

🚀 Et si tu veux rendre ça encore plus propre/configurable :
Tu peux externaliser size et maxElements dans un application.properties

Ou les passer en paramètres de méthode
//////////////////test/////////////////
@ExtendWith(MockitoExtension.class)
public class AutomationHubServiceImplTest {

    @InjectMocks
    private AutomationHubServiceImpl automationHubService;

    @Mock
    private NonCompliantDiscoveryQuery nonCompliantDiscoveryQuery;

    @Mock
    private SearchService searchService; // hypothèse : c'est là que searchCertificates() est défini

    @Test
    public void testSearchNotCompliantsTest_returnsResults() {
        // Arrange
        SearchCertificateRequestDto mockRequest = new SearchCertificateRequestDto();
        List<AutomationHubCertificateDto> mockPage = Arrays.asList(new AutomationHubCertificateDto(), new AutomationHubCertificateDto());

        Mockito.when(nonCompliantDiscoveryQuery.get())
               .thenReturn(mockRequest);

        Mockito.when(searchService.searchCertificates(Mockito.any(SearchCertificateRequestDto.class)))
               .thenReturn(new PageResult<>(mockPage, mockPage.size()));

        // Act
        List<AutomationHubCertificateDto> result = automationHubService.searchNotCompliantsTest();

        // Assert
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
    }
}
//////////////////////////// autotmationHubClient/////////////////////////
public ResponseEntity<SearchResultDto> searchCertificates(SearchCertificateRequestDto searchCertificateRequestDto) throws Exception {
    int pageIndex = 1; // Commencer à 1, comme indiqué dans la réponse JSON
    boolean hasMorePages = true;
    List<Certificate> allCertificates = new ArrayList<>();

    while (hasMorePages) {
        // Préparer la charge utile
        SearchPayloadDto payload = new SearchPayloadDto();
        Map<String, Object> query = searchCertificateRequestDto.getQuery();

        if (!query.isEmpty()) {
            payload.setQuery(query);
        }

        // Appel de postForEntity pour récupérer les résultats de la page actuelle
        ResponseEntity<SearchResultDto> resp = automationHubRestTemplate.postForEntity(
            "/api/v1/certificates/aggregate", // Remplacez par l'URL réelle
            payload,
            SearchResultDto.class
        );

        SearchResultDto result = resp.getBody();
        if (result != null) {
            allCertificates.addAll(result.getResults()); // Récupérer les certificats

            // Mettre à jour la logique pour déterminer s'il y a plus de pages
            hasMorePages = result.isHasMore();
            pageIndex++; // Incrémenter le pageIndex quand il y a plus de pages
        } else {
            hasMorePages = false; // Arrêtez la boucle si aucun résultat n'est trouvé
        }
    }

    // Construire le résultat final
    SearchResultDto finalResult = new SearchResultDto();
    finalResult.setCertificates(allCertificates);
    finalResult.setTotalCount(allCertificates.size()); // ou le nombre total récupéré, si nécessaire

    return ResponseEntity.ok(finalResult);
}
Adaptation duSearchResultDto
Assurez-vous SearchResultDtoque

Java
Copie
public class SearchResultDto {
    private List<Certificate> results; // Liste des certificats
    private int pageIndex;
    private int pageSize;
    private int count;
    private boolean hasMore;

    // Getters et setters
}
Test
Assurez-vous que votre test unitaire simule correctement la réponse de l'API.

Java
Copie
@Test
void testPagination() {
    SearchCertificateRequestDto requestDto = new SearchCertificateRequestDto();
    
    // Mock de la réponse pour postForEntity
    SearchResultDto mockResult = new SearchResultDto();
    mockResult.setResults(Collections.singletonList(new Certificate())); // Exemple d'ajout d'un certificat
    mockResult.setPageIndex(1);
    mockResult.setPageSize(50);
    mockResult.setCount(12);
    mockResult.setHasMore(true); // Simulez qu'il y a plus de pages

    // Simulez l'appel à postForEntity
    when(automationHubRestTemplate.postForEntity(
        anyString(),
        any(),
        eq(SearchResultDto.class)
    )).thenReturn(new ResponseEntity<>(mockResult, HttpStatus.OK));

    ResponseEntity<SearchResultDto> response = myService.searchCertificates(requestDto);

    assertNotNull(response);
    SearchResultDto result = response.getBody();
    assertNotNull(result);
    List<Certificate> certificates = result.getResults();
    assertTrue(certificates.size() > 0);
    assertEquals(12, result.getCount()); // Exemple d'assertion
}
Avec ces mises à jour, votre méthode devrait maintenant gérer correctement la pagination basée sur la structure de réponse que vous avez fournie. N'hésitez pas à poser d



Photocopieuse
Régénérer
////////////////////////duree d'une methode en java//////////////////////////////
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyService.class);

    public ResponseEntity<List<SearchResultDto>> searchCertificates(SearchCertificateRequestDto searchCertificateRequestDto) {
        // Capturer le temps de début
        long startTime = System.currentTimeMillis();

        // Logique de la méthode
        ResponseEntity<List<SearchResultDto>> response = null; 
        try {
            // ... logique pour rechercher des certificats
        } finally {
            // Capturer le temps de fin
            long endTime = System.currentTimeMillis();
            // Calculer la durée
            long duration = endTime - startTime;
            // Logger la durée
            LOGGER.info("La méthode searchCertificates a pris {} ms pour s'exécuter.", duration);
        }

        return response; // Assurez-vous que la réponse est retournée
    }
}
////////////////////////////////////////////////////////////////proxy/////////////////////////
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return registry -> registry.addMapping("/**").allowedOrigins("http://localhost:4200");
    }
}//////////////////////////////////////autre logic//////////////////////////////
public SearchResults searchCertificates(SearchCertificateRequest request, int currentPage, int pageSize) throws FieldNotFoundException {
    boolean hasErrors = false;
    List<Certificate> allCertificates = new ArrayList<>();

    // Vérification de la requête
    if (request == null) {
        hasErrors = true;
        throw new FieldNotFoundException("La requête est nulle.");
    }
    
    // Récupération des certificats
    try {
        allCertificates = certificateService.findCertificates(request);
    } catch (Exception e) {
        hasErrors = true;
        System.err.println("Erreur lors de la recherche : " + e.getMessage());
    }
    
    // Gestion de la pagination
    List<Certificate> pagedCertificates = new ArrayList<>();
    int totalCertificates = allCertificates.size();
    int startIndex = (currentPage - 1) * pageSize;
    int endIndex = Math.min(startIndex + pageSize, totalCertificates);
    
    if (startIndex >= totalCertificates || startIndex < 0) {
        // Si la page demandée est invalide
        hasErrors = true;
        System.out.println("Numéro de page invalide.");
		} else {
        // Création de la sous-liste pour la page en cours
        pagedCertificates = allCertificates.subList(startIndex, endIndex);
    }

    // Gestion des erreurs
    if (hasErrors) {
        // Logique pour retourner des erreurs
    }

    // Retourner les résultats pour la page demandée
    return new SearchResults(pagedCertificates, totalCertificates, currentPage, pageSize);
}