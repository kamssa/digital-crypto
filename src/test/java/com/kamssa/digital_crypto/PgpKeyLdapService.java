import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import java.util.List;

@RestController
public class PgpKeyLdapController {

    private final LdapTemplate ldapTemplate;

    public PgpKeyLdapController(@Value("${ldap.url}") String ldapUrl,
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

    @GetMapping("/search-pgp-keys")
    public List<String> searchPgpKeysByEmail(@RequestParam String email) {
        return ldapTemplate.search("ou=pgpkeys", "(mail=" + email + ")",
                (AttributesMapper<String>) attrs -> getAttributeValue(attrs, "pgpPublicKey"));
    }

    private String getAttributeValue(Attributes attributes, String attributeName) throws NamingException {
        return attributes.get(attributeName) != null ? attributes.get(attributeName).get().toString() : null;
    }
}
/*
méliorations recommandées :
🔹 Sécuriser la requête LDAP :
// Utilisation de LdapQueryBuilder pour éviter l'injection LDAP
LdapQuery query = LdapQueryBuilder.query()
        .base("ou=pgpkeys")
        .where("mail").is(email);

return ldapTemplate.search(query, (AttributesMapper<String>) attrs -> getAttributeValue(attrs, "pgpPublicKey"));
 Schéma de fonctionnement global
L'utilisateur envoie une requête
GET /search-pgp-keys?email=john.doe@example.com
Le contrôleur interroge LDAP
Recherche (mail=john.doe@example.com) dans ou=pgpkeys.
Si un résultat est trouvé, il extrait pgpPublicKey et le retourne en JSON.
*/
/*Ce code définit un contrôleur Spring Boot qui interagit avec un serveur LDAP pour rechercher des clés PGP associées à une adresse e-mail. Voici quelques points d'amélioration et de sécurité :

🔴 Problèmes potentiels :
Injection de paramètres LDAP (LDAP Injection)

La concaténation directe "(mail=" + email + ")" peut exposer l'application à des attaques par injection LDAP.
Solution : Utiliser Filter.escapeFilter() de Spring LDAP pour éviter les injections.
Gestion des erreurs

Si getAttributeValue() ne trouve pas l'attribut, il renvoie null, ce qui pourrait entraîner des NullPointerException en aval.
Solution : Gérer explicitement les cas où l'attribut est absent.
Mauvaise gestion de la connexion LDAP

Le LdapContextSource est instancié directement dans le constructeur, ce qui rend difficile son injection et le test unitaire.
Solution : Utiliser @Configuration pour configurer LdapTemplate et injecter la dépendance.
*/