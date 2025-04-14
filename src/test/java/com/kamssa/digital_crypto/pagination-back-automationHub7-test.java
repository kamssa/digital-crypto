@Configuration
public class PaginationConfig {

    @Value("${pagination.pageSize:50}")
    private int pageSize;

    public int getPageSize() {
        return pageSize;
    }
}
✅ 2. Classe utilitaire (optionnelle)
java
Copier
Modifier
public class PageRequestUtil {

    public static Map<String, Object> createPageRequest(int pageIndex, int pageSize, boolean withCount) {
        Map<String, Object> params = new HashMap<>();
        params.put("pageIndex", pageIndex);
        params.put("pageSize", pageSize);
        params.put("withCount", withCount);
        return params;
    }
}
✅ 3. searchCertificates avec boucle paginée
java
Copier
Modifier
@Service
public class CertificateService {

    private final PaginationConfig paginationConfig;

    public CertificateService(PaginationConfig paginationConfig) {
        this.paginationConfig = paginationConfig;
    }

    public List<Certificate> searchCertificates() {
        List<Certificate> allCertificates = new ArrayList<>();
        int pageIndex = 1;
        int pageSize = paginationConfig.getPageSize();
        boolean hasMore = true;

        while (hasMore) {
            Map<String, Object> params = PageRequestUtil.createPageRequest(pageIndex, pageSize, true);
            PageResult<Certificate> page = searchCertificatesPage(params);
            allCertificates.addAll(page.getItems());

            if (page.getItems().size() < pageSize) {
                hasMore = false;
            } else {
                pageIndex++;
            }
        }

        return allCertificates;
    }

    // Méthode simulée qui retourne une page (à adapter selon ta logique métier)
    private PageResult<Certificate> searchCertificatesPage(Map<String, Object> params) {
        // Simuler une recherche avec un backend ou une base
        // pageIndex, pageSize, withCount extraits de params
        int pageIndex = (int) params.get("pageIndex");
        int pageSize = (int) params.get("pageSize");

        // Simuler des résultats
        List<Certificate> results = fetchFromBackend(pageIndex, pageSize);
        return new PageResult<>(results, 1000); // 1000 est total count simulé
    }

    private List<Certificate> fetchFromBackend(int pageIndex, int pageSize) {
        // Simuler des résultats
        return IntStream.range(0, pageSize)
                .mapToObj(i -> new Certificate("Cert" + ((pageIndex - 1) * pageSize + i)))
                .collect(Collectors.toList());
    }
}
✅ 4. Classe PageResult générique
java
Copier
Modifier
public class PageResult<T> {
    private List<T> items;
    private int totalCount;

    public PageResult(List<T> items, int totalCount) {
        this.items = items;
        this.totalCount = totalCount;
    }

    public List<T> getItems() {
        return items;
    }

    public int getTotalCount() {
        return totalCount;
    }
}
✅ 5. Exemple de certificat
java
Copier
Modifier
public class Certificate {
    private String name;

    public Certificate(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
✅ 6. Exemple de test JUnit
java
Copier
Modifier
@SpringBootTest
public class CertificateServiceTest {

    @Autowired
    private CertificateService certificateService;

    @Test
    public void testSearchCertificatesPagination() {
        List<Certificate> certs = certificateService.searchCertificates();
        assertNotNull(certs);
        assertTrue(certs.size() > 0);
        System.out.println("Total certificates fetched: " + certs.size());
    }
}
