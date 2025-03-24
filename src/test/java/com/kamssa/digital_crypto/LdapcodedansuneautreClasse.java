import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;
import javax.naming.NameNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@Service
public class LdapSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LdapSearchService.class);
    private final LdapTemplate ldapTemplate;

    public LdapSearchService(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    public List<Object> searchByIdentifier(String identifier) {
        try {
            String dn = String.format("CN=%s, OU=Applications, O=Group", identifier);

            LdapQuery query = LdapQueryBuilder.query()
                    .where("pgpCertID").is(identifier)
                    .or("pgpUserID").is(dn)
                    .and(LdapQueryBuilder.query()
                            .where("pgpKey").isPresent()
                            .or("gpgKey").isPresent());

            return ldapTemplate.search(query, new KeyAttributesMapper());
        } catch (NameNotFoundException e) {
            LOGGER.warn("Aucune clé trouvée pour l'identifiant: {}", identifier);
        } catch (LimitExceededException e) {
            LOGGER.error("Trop de résultats retournés pour l'identifiant: {}", identifier);
        } catch (ServiceUnavailableException e) {
            LOGGER.error("Le serveur LDAP est indisponible.");
        } catch (Exception e) {
            LOGGER.error("Erreur lors de la requête LDAP : {}", e.getMessage(), e);
        }
        return List.of(); // Retourne une liste vide en cas d'erreur
    }
}
@Service
public class SearchServiceImpl {
    private final LdapSearchService ldapSearchService;

    public SearchServiceImpl(LdapSearchService ldapSearchService) {
        this.ldapSearchService = ldapSearchService;
    }

    public List<Object> findKeysByIdentifier(String identifier) {
        return ldapSearchService.searchByIdentifier(identifier);
    }
}
import org.springframework.ldap.core.AttributesMapper;
import javax.naming.NamingException;

public class KeyAttributesMapper implements AttributesMapper<Object> {
    @Override
    public Object mapFromAttributes(javax.naming.directory.Attributes attributes) throws NamingException {
        // Crée ton objet personnalisé ici
        return new MyCustomObject(
                attributes.get("pgpCertID").get().toString(),
                attributes.get("pgpKey") != null ? attributes.get("pgpKey").get().toString() : null
        );
    }
}
@RestController
public class SearchController {
    private final SearchServiceImpl searchService;

    public SearchController(SearchServiceImpl searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public List<Object> search(@RequestParam String identifier) {
        return searchService.findKeysByIdentifier(identifier);
    }
}
public class LdapSearchUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(LdapSearchUtil.class);

    public static <T> List<T> findLdapKeyByPgpCertIDOrPgpUserID(
            String identifier,
            LdapTemplate ldapTemplate,
            AttributesMapper<T> attributesMapper) {

        try {
            String dn = String.format("CN=%s, OU=Applications, O=Group", identifier);

            LdapQuery query = LdapQueryBuilder.query()
                    .where("pgpCertID").is(identifier)
                    .or("pgpUserID").is(dn)
                    .and(LdapQueryBuilder.query()
                            .where("pgpKey").isPresent()
                            .or("gpgKey").isPresent()
                    );

            List<T> results = ldapTemplate.search(query, attributesMapper);
            System.out.println("Voir le résultat de recherche dans le LDAP: " + results);
            return results;

        } catch (NameNotFoundException e) {
            LOGGER.warn("Aucune clé trouvée pour l'identifiant: {}", identifier);
        } catch (LimitExceededException e) {
            LOGGER.error("Trop de résultats retournés pour l'identifiant: {}", identifier);
        } catch (ServiceUnavailableException e) {
            LOGGER.error("Le serveur LDAP est indisponible.");
        } catch (Exception e) {
            LOGGER.error("Erreur lors de la requête LDAP : {}", e.getMessage(), e);
        }

        return Collections.emptyList();
    }
}
