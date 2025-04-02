import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;

public List<CertificatAttributes> searchKeys(String cnOrPgpCertID, String value, boolean applyKeyFilter) {
    String dn = String.format("CN=%s, OU=Applications, O=Group", cnOrPgpCertID);

    // Construction de la requête LDAP
    LdapQueryBuilder queryBuilder = LdapQueryBuilder.query()
        .base("ou=Chiffrement_CFT_PGP, ou=APPLICATIONS, o=GROUP")
        .where("pgpCertID").is(cnOrPgpCertID)
        .or("pgpUserID").is(dn)
        .and(LdapQueryBuilder.query().where("pgpKey").isPresent());

    // Si la recherche est approximative ("like")
    if (!value.equals("exact")) {
        queryBuilder = LdapQueryBuilder.query()
            .base("ou=Chiffrement_CFT_PGP, ou=APPLICATIONS, o=GROUP")
            .where("pgpCertID").like(dn)
            .or("pgpUserID").like(cnOrPgpCertID)
            .and(LdapQueryBuilder.query().where("pgpKey").isPresent());
    }

    // Ajout conditionnel du filtre "objectClass=pgpKeyInfo"
    if (applyKeyFilter) {
        queryBuilder.and("objectClass").is("pgpKeyInfo");
    }

    LdapQuery query = queryBuilder; // Génération de la requête finale

    // Appel du service LDAP pour exécuter la requête (exemple)
    return ldapTemplate.search(query, new CertificatAttributesMapper());
}
Explication des améliorations :
Utilisation de LdapQueryBuilder

Au lieu de manipuler une String filter, on utilise LdapQueryBuilder pour une meilleure lisibilité et maintenabilité.

Refactoring du queryBuilder

Un premier queryBuilder est utilisé pour la recherche stricte (is).

Si value n'est pas "exact", on utilise like() pour une recherche plus large.

Ajout dynamique du filtre objectClass=pgpKeyInfo

Si applyKeyFilter est true, on ajoute .and("objectClass").is("pgpKeyInfo") proprement.

Facilité d'intégration avec ldapTemplate.search()

La requête query est directement utilisable avec ldapTemplate.search(query, mapper).

