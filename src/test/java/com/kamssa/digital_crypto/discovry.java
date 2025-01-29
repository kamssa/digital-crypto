@ExtendWith(MockitoExtension.class)
public class AutomationHubServiceTest {

    @Mock
    private DiscoveryQuery discoveryQuery;

    @Mock
    private SearchCertificateRequestDto searchCertificateRequestDto;

    @InjectMocks
    private AutomationHubService automationHubService; // Remplacez par le vrai service

    @BeforeEach
    void setUp() {
        // Configuration initiale des mocks si nécessaire
    }

    @Test
    void testSearchDiscovery_WithDiscoveryQuery() {
        // Préparation des données
        String rawQuery = "search query";
        Date dateAfter3Months = DateUtils.addMonths(new Date(), 3);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String expectedDate = format.format(dateAfter3Months);
        String expectedQuery = rawQuery.replace("dateM+3", expectedDate);

        when(discoveryQuery.isPresent()).thenReturn(Optional.of(rawQuery));

        // Exécution de la méthode
        List<AutomationHubCertificateDto> result = automationHubService.searchDiscovery();

        // Vérifications
        assertNotNull(result);
        verify(discoveryQuery, times(1)).isPresent();
    }

    @Test
    void testSearchDiscovery_WithoutDiscoveryQuery() {
        when(discoveryQuery.isPresent()).thenReturn(Optional.empty());

        List<AutomationHubCertificateDto> result = automationHubService.searchDiscovery();

        assertNotNull(result);
        verify(discoveryQuery, times(1)).isPresent();
    }
}