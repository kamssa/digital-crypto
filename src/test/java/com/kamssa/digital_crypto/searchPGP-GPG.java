package com.example.ldap.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PgpGpgKeyLdapService {

    @Autowired
    private LdapTemplate ldapTemplate;

    public List<String> searchKeysByEmail(String email) {
        LdapQuery query = LdapQueryBuilder.query()
                .where("mail").is(email)
                .and(
                    LdapQueryBuilder.query().where("pgpKey").isPresent()
                    .or("gpgKey").isPresent()
                );

        return ldapTemplate.search(query, (attributes, name) -> {
            String pgpKey = attributes.get("pgpKey") != null ? attributes.get("pgpKey").get().toString() : null;
            String gpgKey = attributes.get("gpgKey") != null ? attributes.get("gpgKey").get().toString() : null;
            return pgpKey != null ? pgpKey : gpgKey; // Priorité à la clé PGP si les deux existent
        });
    }
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
    public List<String> searchKeys(@RequestParam String email) {
        return pgpGpgKeyLdapService.searchKeysByEmail(email);
    }
}
//curl "http://localhost:8080/api/keys/search?email=user@example.com"

