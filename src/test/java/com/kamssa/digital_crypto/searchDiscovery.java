@RunWith(MockitoJUnitRunner.class)
public class AutomationHubServiceImplTest {

    @InjectMocks
    private AutomationHubServiceImpl automationHubService;

    @Mock
    private AutomationHubClient automationHubClient;

    // Attribut discoveryQuery simulé
    private Optional<String> discoveryQuery = Optional.empty();

    @Before
    public void setUp() {
        // Injection de l'attribut dans le service
        ReflectionTestUtils.setField(automationHubService, "discoveryQuery", discoveryQuery);
    }

    @Test
    public void testSearchDiscovery_WithQuery() {
        // GIVEN: discoveryQuery est présent
        discoveryQuery = Optional.of("some-query target dateM+3");
        ReflectionTestUtils.setField(automationHubService, "discoveryQuery", discoveryQuery);

        // Simulation de la date après 3 mois
        Date dateAfter3Months = DateUtils.addMonths(new Date(), 3);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String formattedDate = format.format(dateAfter3Months);

        String expectedQuery = "some-query ".replace("target dateM+3", formattedDate);
        SearchCertificateRequestDto expectedRequest = new SearchCertificateRequestDto(expectedQuery, 1, 999999);
        List<AutomationHubCertificateDto> mockResponse = Arrays.asList(new AutomationHubCertificateDto());

        // Mock du client
        when(automationHubClient.searchCertificates(expectedRequest)).thenReturn(mockResponse);

        // WHEN
        List<AutomationHubCertificateDto> result = automationHubService.searchDiscovery();

        // THEN
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(automationHubClient, times(1)).searchCertificates(expectedRequest);
    }

    @Test
    public void testSearchDiscovery_NoQuery() {
        // GIVEN: discoveryQuery est absent
        discoveryQuery = Optional.empty();
        ReflectionTestUtils.setField(automationHubService, "discoveryQuery", discoveryQuery);

        // Simulation de la date après 3 mois
        Date dateAfter3Months = DateUtils.addMonths(new Date(), 3);

        // Construction des critères attendus
        List<ISearchCriterion> expectedCriteria = new ArrayList<>();
        expectedCriteria.add(new SearchTextCriterion(SearchTextFieldEnum.MODULE, ModuleEnum.DISCOVERY.getValue(), SearchCriterionTextOperatorEnum.EQ));
        expectedCriteria.add(new SearchTextCriterion(SearchTextFieldEnum.STATUS, CertificateStatusEnum.VALID.getValue(), SearchCriterionTextOperatorEnum.EQ));
        expectedCriteria.add(new SearchTextCriterion(SearchTextFieldEnum.TRUST_STATUS, TrustStatusEnum.TRUSTED.getValue(), SearchCriterionTextOperatorEnum.EQ));
        expectedCriteria.add(new SearchDateCriterion(SearchDateFieldEnum.EXPIRY_DATE, dateAfter3Months, SearchCriterionDateOperatorEnum.BEFORE));

        SearchCertificateRequestDto expectedRequest = new SearchCertificateRequestDto(expectedCriteria, 1, 999999);
        List<AutomationHubCertificateDto> mockResponse = Arrays.asList(new AutomationHubCertificateDto());

        // Mock du client
        when(automationHubClient.searchCertificates(expectedRequest)).thenReturn(mockResponse);

        // WHEN
        List<AutomationHubCertificateDto> result = automationHubService.searchDiscovery();

        // THEN
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(automationHubClient, times(1)).searchCertificates(expectedRequest);
    }
}