
// voir temps d'execution d'une methode 
LOGGER.debug("Requête LDAP : {}", query.toString());
long startTime = System.currentTimeMillis();
List<LdapDto> results = ldapTemplate.search(query, new KeyAttributesMapper());
long endTime = System.currentTimeMillis();
LOGGER.debug("Temps d'exécution : {} ms", (endTime - startTime));