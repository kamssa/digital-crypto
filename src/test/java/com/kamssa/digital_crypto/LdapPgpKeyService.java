import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import java.util.List;

@Service
public class LdapPgpKeyService {

    @Autowired
    private LdapTemplate ldapTemplate;

    /**
     * Recherche la clé PGP d'un utilisateur via son UID.
     * @param uid Identifiant de l'utilisateur (ex: "john.doe")
     * @return Liste des clés PGP trouvées
     */
    public List<String> searchPgpKeyByUid(String uid) {
        LdapQuery query = LdapQueryBuilder.query()
                .where("uid").is(uid);

        return ldapTemplate.search(query, (attrs, name) -> getPgpKey(attrs));
    }

    private String getPgpKey(Attributes attrs) throws NamingException {
        return attrs.get("pgpKey") != null ? (String) attrs.get("pgpKey").get() : "No key found";
    }
}
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ldap")
public class LdapPgpKeyController {

    @Autowired
    private LdapPgpKeyService ldapPgpKeyService;

    @GetMapping("/pgp-key/{uid}")
    public List<String> getPgpKey(@PathVariable String uid) {
        return ldapPgpKeyService.searchPgpKeyByUid(uid);
    }
}
