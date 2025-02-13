@RunWith(MockitoJUnitRunner.class)
public class AutomationHubServiceImplTest {

    @InjectMocks
    private AutomationHubServiceImpl automationHubService;

    @Mock
    private AutomationHubClient automationHubClient;

    // Attribut nonCompliantDiscoveryQuery simulé
    private Optional<String> nonCompliantDiscoveryQuery = Optional.empty();

    @Before
    public void setUp() {
        // Injection de l'attribut dans le service
        ReflectionTestUtils.setField(automationHubService, "nonCompliantDiscoveryQuery", nonCompliantDiscoveryQuery);
    }

    @Test
    public void testSearchNotCompliants_WithQuery() {
        // GIVEN: nonCompliantDiscoveryQuery est présent
        nonCompliantDiscoveryQuery = Optional.of("some-query");
        ReflectionTestUtils.setField(automationHubService, "nonCompliantDiscoveryQuery", nonCompliantDiscoveryQuery);

        SearchCertificateRequestDto expectedRequest = new SearchCertificateRequestDto("some-query", 1, 999999);
        List<AutomationHubCertificateDto> mockResponse = Arrays.asList(new AutomationHubCertificateDto());

        // Mock du client
        when(automationHubClient.searchCertificates(expectedRequest)).thenReturn(mockResponse);

        // WHEN
        List<AutomationHubCertificateDto> result = automationHubService.searchNotCompliants();

        // THEN
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(automationHubClient, times(1)).searchCertificates(expectedRequest);
    }

    @Test
    public void testSearchNotCompliants_NoQuery() {
        // GIVEN: nonCompliantDiscoveryQuery est absent
        nonCompliantDiscoveryQuery = Optional.empty();
        ReflectionTestUtils.setField(automationHubService, "nonCompliantDiscoveryQuery", nonCompliantDiscoveryQuery);

        // WHEN + THEN (on attend une exception car la partie OR n'est pas implémentée)
        assertThrows(NotImplementedException.class, () -> automationHubService.searchNotCompliants());
    }
}