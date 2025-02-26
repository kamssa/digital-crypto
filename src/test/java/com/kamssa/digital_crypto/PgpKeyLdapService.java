import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import java.util.List;

@Service
public class PgpKeyLdapService {

    private final LdapTemplate ldapTemplate;

    public PgpKeyLdapService(@Value("${ldap.url}") String ldapUrl,
                              @Value("${ldap.base.dn}") String baseDn,
                              @Value("${ldap.user}") String user,
                              @Value("${ldap.password}") String password) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapUrl);
        contextSource.setBase(baseDn);
        contextSource.setUserDn(user);
        contextSource.setPassword(password);
        contextSource.afterPropertiesSet();
        
        this.ldapTemplate = new LdapTemplate(contextSource);
    }

    public List<String> searchPgpKeysByEmail(String email) {
        return ldapTemplate.search("ou=pgpkeys", "(mail=" + email + ")",
                (AttributesMapper<String>) attrs -> getAttributeValue(attrs, "pgpPublicKey"));
    }

    private String getAttributeValue(Attributes attributes, String attributeName) throws NamingException {
        return attributes.get(attributeName) != null ? attributes.get(attributeName).get().toString() : null;
    }
}
