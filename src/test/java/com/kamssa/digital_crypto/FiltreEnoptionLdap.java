try {
    LdapQuery query = LdapQueryBuilder.query()
            .where("objectClass").is("pgpKeyInfo");

    return ldapTemplate.search(query, new CertificatContextMapper());
} catch (Exception e) {
    LOGGER.error("Erreur lors de la recherche des certificats : ", e);
    return Collections.emptyList();
}
mettre un filtre en option
// Créer le constructeur de requête LDAP
LdapQueryBuilder queryBuilder = LdapQueryBuilder.query()
    .base("ou=Chiffrement_CFT_PGP, ou=APPLICATIONS, o=GROUP")
    .where("pgpCertID").is(identifier)
    .or("pgpUserID").is(dn);

// Supposons que vous avez une condition pour le filtre optionnel
boolean filterOptional = true; // Condition à définir

if (filterOptional) {
    queryBuilder.and(LdapQueryBuilder.query().where("pgpKey").isPresent()
        .or("pgpKey").isPresent());
}

// Terminer votre requête ici si nécessaire
///////////////////////////////////////
filtre en option et en paramettre
public List<CertificateAttributes> findLdapKeyByAndLikePgpCertIDOrPgpUserID(String identifier, String value, boolean applyKeyFilter) {
    String dn = String.format("CN=%s, OU=Applications, O=Group", identifier);
    
    // Création du builder de requête LDAP
    LdapQueryBuilder queryBuilder = LdapQueryBuilder.query()
        .base("ou=Chiffrement_CFT_PGP, ou=APPLICATIONS, o=GROUP")
        .where("pgpCertID").is(identifier)
        .or("pgpUserID").is(dn);
    
    // Ajout conditionnel du filtre basé sur le paramètre applyKeyFilter
    if (applyKeyFilter) {
        queryBuilder.and(LdapQueryBuilder.query()
            .where("pgpKey").isPresent()
            .or("pgpKey").isPresent());
    }

    // Exécution de la requête et retour des résultats
    // (Assurez-vous que cette partie correspond à votre logique)
    // return ldapTemplate.search(queryBuilder.build(), new CertificateAttributesMapper());
    
    return new ArrayList<>(); // Remplacer par la logique réelle pour retourner les résultats
}