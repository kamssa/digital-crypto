@RunWith(MockitoJUnitRunner.class)
public class AutomationHubServiceImplTest {

    @InjectMocks
    private AutomationHubServiceImpl automationHubService;

    @Mock
    private AutomationHubClient automationHubClient;

    // Attribut autoEnrollQuery simulé
    private Optional<String> autoEnrollQuery = Optional.empty();

    @Before
    public void setUp() {
        // Injection de l'attribut dans le service
        ReflectionTestUtils.setField(automationHubService, "autoEnrollQuery", autoEnrollQuery);
    }

    @Test
    public void testSearchAutoEnroll_WithQuery() {
        // GIVEN: autoEnrollQuery est présent
        autoEnrollQuery = Optional.of("some-query target date-J1");
        ReflectionTestUtils.setField(automationHubService, "autoEnrollQuery", autoEnrollQuery);

        // Simulation de la date J-1
        Date dateJMoins1 = DateUtils.addDays(new Date(), -1);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String formattedDate = format.format(dateJMoins1);

        String expectedQuery = "some-query ".replace("target date-J1", formattedDate);
        SearchCertificateRequestDto expectedRequest = new SearchCertificateRequestDto(expectedQuery, 1, 999999);
        List<AutomationHubCertificateDto> mockResponse = Arrays.asList(new AutomationHubCertificateDto());

        // Mock du client
        when(automationHubClient.searchCertificates(expectedRequest)).thenReturn(mockResponse);

        // WHEN
        List<AutomationHubCertificateDto> result = automationHubService.searchAutoEnroll(true);

        // THEN
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(automationHubClient, times(1)).searchCertificates(expectedRequest);
    }

    @Test
    public void testSearchAutoEnroll_NoQuery() {
        // GIVEN: autoEnrollQuery est absent
        autoEnrollQuery = Optional.empty();
        ReflectionTestUtils.setField(automationHubService, "autoEnrollQuery", autoEnrollQuery);

        // Simulation de la date J-1
        Date dateJMoins1 = DateUtils.addDays(new Date(), -1);

        // Construction des critères attendus
        List<ISearchCriterion> expectedCriteria = new ArrayList<>();
        expectedCriteria.add(new SearchTextCriterion(SearchTextFieldEnum.MODULE, Arrays.asList(
                ModuleEnum.DISCOVERY.getValue(), ModuleEnum.CRMP.getValue(), ModuleEnum.INTUNE.getValue(),
                ModuleEnum.INTUNE_PKCS.getValue(), ModuleEnum.JAMF.getValue(), ModuleEnum.WCCE.getValue(),
                ModuleEnum.WEBRA.getValue()
        ), SearchCriterionTextOperatorEnum.NOT_IN));

        expectedCriteria.add(new SearchTextCriterion(SearchTextFieldEnum.STATUS, CertificateStatusEnum.VALID.getValue(), SearchCriterionTextOperatorEnum.EQ));
        expectedCriteria.add(new SearchDateCriterion(SearchDateFieldEnum.VALIDITY_DATE, dateJMoins1, SearchCriterionDateOperatorEnum.AFTER));
        expectedCriteria.add(new SearchDateCriterion(SearchDateFieldEnum.VALIDITY_DATE, new Date(), SearchCriterionDateOperatorEnum.BEFORE));

        SearchCertificateRequestDto expectedRequest = new SearchCertificateRequestDto(expectedCriteria, 1, 999999);
        List<AutomationHubCertificateDto> mockResponse = Arrays.asList(new AutomationHubCertificateDto());

        // Mock du client
        when(automationHubClient.searchCertificates(expectedRequest)).thenReturn(mockResponse);

        // WHEN
        List<AutomationHubCertificateDto> result = automationHubService.searchAutoEnroll(true);

        // THEN
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(automationHubClient, times(1)).searchCertificates(expectedRequest);
    }
}