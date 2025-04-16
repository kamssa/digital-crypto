{
  "pageIndex": 1,
  "pageSize": 100,
  "withCount": true
}
Et tu veux récupérer tous les certificats en appelant cette API page par page, avec un pageSize configurable (par défaut 50).

✅ Exemple complet : appel REST externe paginé
1. Configurable pageSize
java
Copier
Modifier
@Configuration
public class PaginationConfig {

    @Value("${pagination.pageSize:50}")
    private int pageSize;

    public int getPageSize() {
        return pageSize;
    }
}
2. DTOs pour la réponse
java
Copier
Modifier
public class Certificate {
    private String id;
    private String name;

    // Getters & Setters
}

public class CertificatePageResponse {
    private List<Certificate> items;
    private int totalCount;

    // Getters & Setters
}
3. Service REST Client
java
Copier
Modifier
@Service
public class CertificateService {

    private final RestTemplate restTemplate;
    private final PaginationConfig paginationConfig;

    @Value("${external.api.url}")
    private String externalApiUrl;

    public CertificateService(RestTemplateBuilder builder, PaginationConfig paginationConfig) {
        this.restTemplate = builder.build();
        this.paginationConfig = paginationConfig;
    }

    public List<Certificate> searchCertificates() {
        List<Certificate> allCertificates = new ArrayList<>();
        int pageIndex = 1;
        int pageSize = paginationConfig.getPageSize();
        boolean hasMore = true;

        while (hasMore) {
            Map<String, Object> body = new HashMap<>();
            body.put("pageIndex", pageIndex);
            body.put("pageSize", pageSize);
            body.put("withCount", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<CertificatePageResponse> response = restTemplate.exchange(
                    externalApiUrl,
                    HttpMethod.POST,
                    entity,
                    CertificatePageResponse.class
            );

            List<Certificate> pageItems = response.getBody().getItems();
            allCertificates.addAll(pageItems);

            if (pageItems.size() < pageSize) {
                hasMore = false;
            } else {
                pageIndex++;
            }
        }

        return allCertificates;
    }
}
4. Exemple de test unitaire (mock du REST)
java
Copier
Modifier
@SpringBootTest
@AutoConfigureMockRestServiceServer
public class CertificateServiceTest {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MockRestServiceServer server;

    @Value("${external.api.url}")
    private String externalApiUrl;

    @Test
    public void testSearchCertificatesWithPagination() {
        // Mock de deux pages de certificats (exemple simplifié)
        List<Certificate> page1 = Arrays.asList(new Certificate("1", "CertA"), new Certificate("2", "CertB"));
        List<Certificate> page2 = Collections.singletonList(new Certificate("3", "CertC"));

        server.expect(ExpectedCount.once(),
                requestTo(externalApiUrl))
                .andRespond(withSuccess(jsonOf(page1, 2), MediaType.APPLICATION_JSON));

        server.expect(ExpectedCount.once(),
                requestTo(externalApiUrl))
                .andRespond(withSuccess(jsonOf(page2, 3), MediaType.APPLICATION_JSON));

        List<Certificate> certs = certificateService.searchCertificates();

        assertEquals(3, certs.size());
    }

    private String jsonOf(List<Certificate> items, int totalCount) {
        return """
               {
                   "items": %s,
                   "totalCount": %d
               }
               """.formatted(toJson(items), totalCount);
    }

    private String toJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
🧪 Bonus : Test manuel ou appel rapide
Tu peux aussi tester l’appel manuellement depuis un contrôleur temporaire si tu veux vérifier que ça fonctionne bien :

java
Copier
Modifier
@RestController
@RequestMapping("/test")
public class TestController {

    private final CertificateService certificateService;

    public TestController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping("/certs")
    public List<Certificate> getAllCerts() {
        return certificateService.searchCertificates();
    }
}
////////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////////////////
✅ 1. Configuration du RestTemplate + Authentification (ex: token Bearer)
Tu peux configurer un RestTemplate avec un Interceptor pour ajouter automatiquement un token d’authentification dans tous les appels :

java
Copier
Modifier
@Configuration
public class RestTemplateConfig {

    @Value("${external.api.token}")
    private String apiToken;

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // Ajouter un interceptor pour injecter l'en-tête Authorization
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("Authorization", "Bearer " + apiToken);
            return execution.execute(request, body);
        });

        return restTemplate;
    }
}
🧠 Tu peux aussi gérer un token renouvelable (OAuth2) mais on reste simple ici.

✅ 2. Utilisation dans ton CertificateService
Tu injectes ton RestTemplate et t’en sers comme montré précédemment.

java
Copier
Modifier
@Service
public class CertificateService {

    private final RestTemplate restTemplate;
    private final PaginationConfig paginationConfig;

    @Value("${external.api.url}")
    private String externalApiUrl;

    public CertificateService(RestTemplate restTemplate, PaginationConfig paginationConfig) {
        this.restTemplate = restTemplate;
        this.paginationConfig = paginationConfig;
    }

    public List<Certificate> searchCertificates() {
        List<Certificate> allCertificates = new ArrayList<>();
        int pageIndex = 1;
        int pageSize = paginationConfig.getPageSize();

        boolean hasMore = true;

        while (hasMore) {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("pageIndex", pageIndex);
            requestBody.put("pageSize", pageSize);
            requestBody.put("withCount", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<CertificatePageResponse> response = restTemplate.exchange(
                    externalApiUrl,
                    HttpMethod.POST,
                    entity,
                    CertificatePageResponse.class
            );

            CertificatePageResponse body = response.getBody();
            List<Certificate> items = body.getItems();
            allCertificates.addAll(items);

            if (items.size() < pageSize) {
                hasMore = false;
            } else {
                pageIndex++;
            }
        }

        return allCertificates;
    }
}
✅ 3. Fichier application.yml ou application.properties
yaml
Copier
Modifier
external:
  api:
    url: https://mon-api.com/certificates/search
    token: eyJhbGciOiJIUzI1NiIsInR5cCI6...
pagination:
  pageSize: 50
//////////////////////////webclient//////////////////////////////////
public Mono<SearchResultDto> searchCertificates(SearchCertificateRequestDto request) {
    int pageIndex = 1;
    List<AutomationHubCertificateDto> allCertificates = new ArrayList<>();
    SearchPayloadDto payload = new SearchPayloadDto();

    if (!StringUtils.isEmpty(request.getRawQuery())) {
        payload.setQuery(request.getRawQuery());
    } else {
        payload.setQuery(this.buildAutomationHubHqlQuery(request));
    }

    payload.setCaseSensitive(request.isCaseSensitive());
    payload.setPageIndex(request.getPage());
    payload.setPageSize(request.getSize());
    payload.setWithCount(true);

    return webClient.post()
        .uri("/certificates/search")
        .bodyValue(payload)
        .retrieve()
        .bodyToMono(ResponseSearchDto.class)
        .map(resp -> {
            SearchResultDto result = mapper.toSearchResultDto(resp);
            if (result != null && result.getResults() != null) {
                allCertificates.addAll(result.getResults());
            }
            return result;
        });
}