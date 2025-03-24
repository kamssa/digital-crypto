import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import org.springframework.ldap.core.AttributesMapper;
import java.util.function.Function;

public class GenericAttributesMapper<T> implements AttributesMapper<T> {
    private final Function<Attributes, T> mapperFunction;

    public GenericAttributesMapper(Function<Attributes, T> mapperFunction) {
        this.mapperFunction = mapperFunction;
    }

    @Override
    public T mapFromAttributes(Attributes attributes) throws NamingException {
        return mapperFunction.apply(attributes);
    }

    public static String getAttributeValue(Attributes attrs, String attributeName) {
        try {
            Attribute attr = attrs.get(attributeName);
            return (attr != null) ? attr.get().toString() : null;
        } catch (NamingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
////////////////////////////////////////
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@Service
public class SearchService {

    public <T> List<T> findLdapKeyLikePgpCertIDOrpgpUserID(String identifier, Function<Attributes, T> mapperFunction) {
        try {
            String dn = String.format("CN=%s, OU=Applications, O=Group", identifier);

            LdapQuery query = LdapQueryBuilder.query()
                    .base("ou=Chiffrement_CFT_PGP, ou=APPLICATIONS, o=GROUP")
                    .where("pgpCertID").like(identifier)
                    .or("pgpUserID").like(dn)
                    .and(LdapQueryBuilder.query().where("pgpKey").isPresent());

            return refSGLdapConfig.ldapTemplate().search(query, new GenericAttributesMapper<>(mapperFunction));
        } catch (Exception e) {
            LOGGER.error("Erreur lors de la requête LDAP : {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}

///////////////////////////////////////////////
List<LdapDto> results = searchService.findLdapKeyLikePgpCertIDOrpgpUserID(identifier, attrs -> {
    String pgpCertID = GenericAttributesMapper.getAttributeValue(attrs, "pgpCertID");
    String pgpKey = GenericAttributesMapper.getAttributeValue(attrs, "pgpKey");
    String pgpUserID = GenericAttributesMapper.getAttributeValue(attrs, "pgpUserID");
    return new LdapDtoImpl(pgpCertID, pgpKey, pgpUserID);
});

////////////////////////////////////////////////////////////////
List<CustomDto> customResults = searchService.findLdapKeyLikePgpCertIDOrpgpUserID(identifier, attrs -> {
    String customField = GenericAttributesMapper.getAttributeValue(attrs, "customAttribute");
    return new CustomDto(customField);
});
