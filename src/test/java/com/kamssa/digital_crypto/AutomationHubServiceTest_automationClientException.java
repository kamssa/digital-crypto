import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

class AutomationHubServiceTest {

    @InjectMocks
    private AutomationHubServiceImpl automationHubService; // La classe contenant votre méthode

    @Mock
    private AutomationHubClient automationHubClient; // Simuler la dépendance externe

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this); // Initialiser les mocks
    }

    @Test
    void testGetCertificateByCnAndSerial_SearchCertificatesThrowsException() {
        String cn = "testCN";
        String serial = "12345";

        // Construire les critères de recherche attendus
        SearchCertificateRequestDto expectedRequest = new SearchCertificateRequestDto(
            List.of(
                new SearchTextCriterion(SearchTextFieldEnum.STATUS, CertificateStatusEnum.VALID.getValue(), SearchCriterionTextOperatorEnum.EQ),
                new SearchTextCriterion(SearchTextFieldEnum.MODULE, ModuleEnum.WEBRA.getValue(), SearchCriterionTextOperatorEnum.EQ),
                new SearchTextCriterion(SearchTextFieldEnum.COMMON_NAME, cn, SearchCriterionTextOperatorEnum.EQ),
                new SearchTextCriterion(SearchTextFieldEnum.SERIAL, serial, SearchCriterionTextOperatorEnum.EQ)
            ),
            1,
            999999
        );

        // Simuler une exception levée par automationHubClient.searchCertificates
        when(automationHubClient.searchCertificates(eq(expectedRequest)))
            .thenThrow(new FailedCallException("Search failed"));

        FailedToGetCertificateException exception = assertThrows(
            FailedToGetCertificateException.class,
            () -> automationHubService.getCertificateByCnAndSerial(cn, serial)
        );

        assertEquals("search failed", exception.getMessage());
    }
}