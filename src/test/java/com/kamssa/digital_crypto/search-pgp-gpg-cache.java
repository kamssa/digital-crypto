package com.example.ldap.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.ldap.LimitExceededException;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;

import javax.naming.ServiceUnavailableException;
import java.util.Collections;
import java.util.List;

@Service
public class PgpGpgKeyLdapService {
    private static final Logger logger = LoggerFactory.getLogger(PgpGpgKeyLdapService.class);

    @Autowired
    private LdapTemplate ldapTemplate;

    @Cacheable(value = "ldapKeysCache", key = "#identifier")
    public List<String> searchKeys(String identifier) {
        try {
            LdapQuery query = LdapQueryBuilder.query()
                    .where("mail").is(identifier)
                    .or("uid").is(identifier)
                    .and(
                        LdapQueryBuilder.query().where("pgpKey").isPresent()
                        .or("gpgKey").isPresent()
                    );

            return ldapTemplate.search(query, (attributes, name) -> {
                String pgpKey = attributes.get("pgpKey") != null ? attributes.get("pgpKey").get().toString() : null;
                String gpgKey = attributes.get("gpgKey") != null ? attributes.get("gpgKey").get().toString() : null;
                return pgpKey != null ? pgpKey : gpgKey; // Priorité à PGP si les deux existent
            });

        } catch (NameNotFoundException e) {
            logger.warn("Aucune clé trouvée pour l'identifiant: {}", identifier);
        } catch (LimitExceededException e) {
            logger.error("Trop de résultats retournés pour l'identifiant: {}", identifier);
        } catch (ServiceUnavailableException e) {
            logger.error("Le serveur LDAP est indisponible.");
        } catch (Exception e) {
            logger.error("Erreur lors de la requête LDAP : {}", e.getMessage(), e);
        }

        return Collections.emptyList(); // Retourne une liste vide en cas d'erreur
    }
}
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
}
package com.example.ldap.controller;

import com.example.ldap.service.PgpGpgKeyLdapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/keys")
public class PgpGpgKeyController {

    @Autowired
    private PgpGpgKeyLdapService pgpGpgKeyLdapService;

    @GetMapping("/search")
    public List<String> searchKeys(@RequestParam String identifier) {
        return pgpGpgKeyLdapService.searchKeys(identifier);
    }
}
curl "http://localhost:8080/api/keys/search?identifier=john.doe"
