public List<CertificateAttributes> findLdapKeyByAndLikePgpCertIDOrPgpUserID(String identifier, String value) {
    String dn = String.format("CN=%s, OU=Applications, O=Group", identifier);
    LdapQueryBuilder queryBuilder = LdapQueryBuilder.query()
        .base("ou=Chiffrement_CFT_PGP, ou=APPLICATIONS, o=GROUP")
        .where("pgpCertID").is(identifier)
        .or("pgpUserID").is(dn)
        .and(LdapQueryBuilder.query().where("pgpKey").isPresent()
            .or("pgpKey").isPresent()
        );

    if (!value.equals("exact")) {
        queryBuilder = LdapQueryBuilder.query()
            .base("ou=Chiffrement_CFT_PGP, ou=APPLICATIONS, o=GROUP")
            .where("pgpUserID").like(dn)
            .or("pgpCertID").like(identifier)
            .and(LdapQueryBuilder.query().where("pgpKey").isPresent()
                .or("pgpKey").isPresent()
            );
    }

    try {
        List<CertificateAttributes> results = refSGldapConfig.ldapTemplate()
            .search(queryBuilder, new CertificateContextMapper());

        System.out.println("Résultats : " + results);
        return results;
    } catch (NameNotFoundException e) {
        LOGGER.warn("Aucune clé trouvée pour l'identifiant : {}", identifier);
    } catch (LimitExceededException e) {
        LOGGER.error("Trop de résultats retournés pour l'identifiant : {}", identifier);
    } catch (ServiceUnavailableException e) {
        LOGGER.error("Le serveur LDAP est indisponible.");
    } catch (Exception e) {
        LOGGER.error("Erreur lors de la requête LDAP : {}", e.getMessage(), e);
    }

    return Collections.emptyList();
}
