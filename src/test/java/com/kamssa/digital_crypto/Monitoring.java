2. Structure du Projet
Une bonne organisation des packages rendra votre code plus facile à maintenir.
code
Code
com.votreentreprise.monitoring
├── controller/
│   └── HealthController.java         // L'API REST qui expose les résultats
├── model/
│   └── HealthCheckResult.java        // L'entité JPA pour la BDD
├── repository/
│   └── HealthCheckResultRepository.java // L'interface pour requêter la BDD
├── scheduler/
│   └── HealthCheckRunner.java        // Le service qui exécute les checks
└── service/
    ├── check/
    │   ├── CmdbHealthCheck.java      // Implémentation pour le check CMDB
    │   └── LdapHealthCheck.java      // Implémentation pour le check LDAP
    │   └── ... (un fichier par check)
    ├── HealthCheck.java              // L'interface commune à tous les checks
    └── HealthStatus.java             // Un DTO pour le statut d'un check
3. Implémentation pas à pas
Étape A : La Couche de Données (Model & Repository)
C'est la base de notre système.
1. L'entité JPA (HealthCheckResult.java)
code
Java
// src/main/java/com/votreentreprise/monitoring/model/HealthCheckResult.java
package com.votreentreprise.monitoring.model;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data // Lombok pour générer getters, setters, toString, etc.
@Entity
@Table(name = "health_check_results")
public class HealthCheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String checkName; // Ex: "cmdb", "ldap_refsg"

    @Column(nullable = false)
    private String status; // Ex: "OK", "KO", "TIMEOUT"

    @Lob // Pour stocker potentiellement de longs messages d'erreur
    private String details;

    @Column(nullable = false)
    private String hostname; // Le serveur qui a exécuté le check

    @Column(nullable = false)
    private LocalDateTime checkedAt; // Date et heure du check
}
2. Le Repository (HealthCheckResultRepository.java)
C'est ici que nous mettons la requête SQL complexe pour ne récupérer que les derniers résultats. La meilleure façon de faire cela est d'utiliser une "window function" comme ROW_NUMBER().
code
Java
// src/main/java/com/votreentreprise/monitoring/repository/HealthCheckResultRepository.java
package com.votreentreprise.monitoring.repository;

import com.votreentreprise.monitoring.model.HealthCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HealthCheckResultRepository extends JpaRepository<HealthCheckResult, Long> {

    /**
     * Récupère le résultat le plus récent pour chaque paire unique (checkName, hostname).
     * La sous-requête partitionne les données par nom de check et par hostname,
     * les ordonne par date descendante, et assigne un rang.
     * La requête principale ne garde que les entrées avec le rang 1 (les plus récentes).
     */
    @Query(value = "SELECT * FROM (" +
                   "    SELECT *, ROW_NUMBER() OVER (PARTITION BY check_name, hostname ORDER BY checked_at DESC) as rn " +
                   "    FROM health_check_results" +
                   ") sub " +
                   "WHERE sub.rn = 1", nativeQuery = true)
    List<HealthCheckResult> findLatestResults();
}
Note : La syntaxe de ROW_NUMBER() est standard SQL et fonctionne sur PostgreSQL, Oracle, SQL Server. Pour MySQL 8+, c'est aussi supporté.
Étape B : L'Architecture des Checks (Service)
C'est le cœur de la logique métier, conçu pour être extensible.
1. Le DTO pour le statut (HealthStatus.java)
code
Java
// src/main/java/com/votreentreprise/monitoring/service/HealthStatus.java
package com.votreentreprise.monitoring.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HealthStatus {
    private String status;
    private String details;

    public static HealthStatus ok() {
        return new HealthStatus("OK", "Service is operational.");
    }

    public static HealthStatus ko(String reason) {
        return new HealthStatus("KO", reason);
    }
}
2. L'interface commune (HealthCheck.java)
code
Java
// src/main/java/com/votreentreprise/monitoring/service/HealthCheck.java
package com.votreentreprise.monitoring.service;

public interface HealthCheck {
    /** Le nom unique du service vérifié. */
    String getName();
    
    /** Exécute la vérification et retourne son statut. */
    HealthStatus check();
}
3. Une implémentation d'exemple (CmdbHealthCheck.java)
Chaque service à vérifier aura une classe comme celle-ci. Grâce à @Component, Spring la trouvera automatiquement.
code
Java
// src/main/java/com/votreentreprise/monitoring/service/check/CmdbHealthCheck.java
package com.votreentreprise.monitoring.service.check;

import com.votreentreprise.monitoring.service.HealthCheck;
import com.votreentreprise.monitoring.service.HealthStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component // Important pour que Spring puisse l'injecter !
public class CmdbHealthCheck implements HealthCheck {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CMDB_HEALTH_URL = "http://url.de.votre.cmdb/api/health";

    @Override
    public String getName() {
        return "cmdb";
    }

    @Override
    public HealthStatus check() {
        try {
            // Ici, vous mettriez la vraie logique : appeler une API, se connecter à une DB...
            // C'est une bonne pratique de configurer des timeouts sur vos appels réseau.
            // restTemplate.getForObject(CMDB_HEALTH_URL, String.class);
            
            // Simuler un succès
            return HealthStatus.ok();
        } catch (Exception e) {
            // En cas d'échec (timeout, erreur 500, etc.), on retourne KO.
            return HealthStatus.ko("Failed to connect to CMDB: " + e.getMessage());
        }
    }
}
Étape C : Le Tâcheron (Scheduler)
Ce service s'exécute en arrière-plan à intervalle régulier.
code
Java
// src/main/java/com/votreentreprise/monitoring/scheduler/HealthCheckRunner.java
package com.votreentreprise.monitoring.scheduler;

import com.votreentreprise.monitoring.model.HealthCheckResult;
import com.votreentreprise.monitoring.repository.HealthCheckResultRepository;
import com.votreentreprise.monitoring.service.HealthCheck;
import com.votreentreprise.monitoring.service.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class HealthCheckRunner {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckRunner.class);

    private final List<HealthCheck> allChecks;
    private final HealthCheckResultRepository repository;
    private final String hostname;

    // Grace à Spring, `allChecks` sera une liste de toutes les classes qui implémentent HealthCheck
    public HealthCheckRunner(List<HealthCheck> allChecks, HealthCheckResultRepository repository) {
        this.allChecks = allChecks;
        this.repository = repository;
        this.hostname = resolveHostname();
    }

    // Le ticket demande toutes les 5 minutes (300 000 ms)
    @Scheduled(fixedRateString = "${monitoring.check.frequency-ms:300000}")
    public void performAllChecks() {
        log.info("Starting health checks on host: {}", hostname);
        for (HealthCheck check : allChecks) {
            log.debug("Running check: {}", check.getName());
            HealthStatus status = check.check();

            HealthCheckResult result = new HealthCheckResult();
            result.setCheckName(check.getName());
            result.setStatus(status.getStatus());
            result.setDetails(status.getDetails());
            result.setHostname(hostname);
            result.setCheckedAt(LocalDateTime.now());
            
            repository.save(result);
        }
        log.info("Finished all health checks.");
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.error("Could not determine hostname", e);
            return "unknown-host";
        }
    }
}
Étape D : L'API d'Exposition (Controller)
Le point final qui sera consulté par les outils de monitoring.
code
Java
// src/main/java/com/votreentreprise/monitoring/controller/HealthController.java
package com.votreentreprise.monitoring.controller;

import com.votreentreprise.monitoring.model.HealthCheckResult;
import com.votreentreprise.monitoring.repository.HealthCheckResultRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class HealthController {

    private final HealthCheckResultRepository repository;

    public HealthController(HealthCheckResultRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/health")
    public Map<String, Map<String, String>> getAggregatedHealthStatus() {
        List<HealthCheckResult> latestResults = repository.findLatestResults();

        // On utilise les streams Java 8 pour transformer la liste plate en la structure Map<String, Map<...>> demandée
        return latestResults.stream()
            .collect(Collectors.groupingBy(
                HealthCheckResult::getHostname, // Clé externe : le nom du serveur
                Collectors.toMap(
                    HealthCheckResult::getCheckName, // Clé interne : le nom du check
                    HealthCheckResult::getStatus     // Valeur interne : le statut ("OK", "KO")
                )
            ));
    }
}
4. Configuration Finale
1. Activer la planification
Ajoutez l'annotation @EnableScheduling à votre classe principale.
code
Java
// src/main/java/com/votreentreprise/monitoring/MonitoringApplication.java
package com.votreentreprise.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Très important !
public class MonitoringApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonitoringApplication.class, args);
    }
}
2. Configurer la base de données (application.properties)
code
Properties
# Configuration de la base de données
spring.datasource.url=jdbc:postgresql://localhost:5432/votre_db
spring.datasource.username=user
spring.datasource.password=password

# Configuration de JPA
spring.jpa.hibernate.ddl-auto=update # ou validate en production
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Configuration de notre scheduler (optionnel, la valeur par défaut est 300000)
monitoring.check.frequency-ms=300000
Pour Résumer
Avec cette architecture :
Vous ajoutez un nouveau check simplement en créant une nouvelle classe qui implémente HealthCheck et en la marquant @Component. Le système la prendra en charge automatiquement, sans modifier une seule autre ligne de code.
La performance de l'API /health est excellente, car elle ne fait qu'une seule lecture rapide en base de données, sans jamais exécuter les checks elle-même.
La robustesse est assurée : un check qui plante n'empêchera pas les autres de s'exécuter, et le statut "KO" sera correctement enregistré avec le message d'erreur.
Le code est testable : vous pouvez facilement écrire des tests unitaires pour chaque HealthCheck et des tests d'intégration pour le contrôleur et le scheduler.
Model
ThinkingThoughts
(experimental)
Auto
Expand to view model thoughts

chevron_right
Oui, bien sûr. Voici le code complet et commenté pour le service qui s'exécute en arrière-plan.
Ce code est conçu pour être extensible. Vous n'aurez qu'à créer de nouvelles classes pour chaque nouveau service à surveiller, sans jamais modifier ce "worker".
Prérequis : La Structure de Base
Avant de créer le worker, nous avons besoin des "briques" sur lesquelles il va s'appuyer.
1. L'interface HealthCheck
C'est le contrat que chaque "vérificateur" devra respecter.
code
Java
// src/main/java/com/votreentreprise/monitoring/service/HealthCheck.java
package com.votreentreprise.monitoring.service;

public interface HealthCheck {
    /**
     * Retourne le nom unique du service qui est vérifié.
     * Ex: "cmdb", "ldap_refsg", "api_snow".
     */
    String getName();
    
    /**
     * Exécute la vérification de santé et retourne son état.
     * Cette méthode doit gérer ses propres erreurs (try-catch) et
     * ne jamais lever d'exception pour ne pas bloquer le worker.
     */
    HealthStatus check();
}
2. La classe HealthStatus
Un simple objet pour transporter le résultat d'un check.
code
Java
// src/main/java/com/votreentreprise/monitoring/service/HealthStatus.java
package com.votreentreprise.monitoring.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HealthStatus {
    private final String status; // "OK" ou "KO"
    private final String details;

    public static HealthStatus ok(String message) {
        return new HealthStatus("OK", message);
    }

    public static HealthStatus ko(String reason) {
        return new HealthStatus("KO", reason);
    }
}
3. Une implémentation d'exemple : CmdbHealthCheck
Pour que notre worker ait quelque chose à exécuter.
code
Java
// src/main/java/com/votreentreprise/monitoring/service/check/CmdbHealthCheck.java
package com.votreentreprise.monitoring.service.check;

import com.votreentreprise.monitoring.service.HealthCheck;
import com.votreentreprise.monitoring.service.HealthStatus;
import org.springframework.stereotype.Component;

@Component // Très important: pour que Spring le détecte et l'injecte dans le worker.
public class CmdbHealthCheck implements HealthCheck {

    @Override
    public String getName() {
        return "cmdb";
    }

    @Override
    public HealthStatus check() {
        try {
            // METTEZ ICI VOTRE LOGIQUE DE VÉRIFICATION
            // Par exemple, un appel à une API REST avec RestTemplate.
            // Si l'appel réussit, on retourne OK.
            // simulateSuccess(); 
            return HealthStatus.ok("Connexion au service CMDB réussie.");
        } catch (Exception e) {
            // Si une exception est levée (timeout, erreur 503, etc.), on retourne KO.
            return HealthStatus.ko("Erreur lors de la connexion à CMDB: " + e.getMessage());
        }
    }
}
Le Code du Worker (Scheduler)
Voici le cœur de votre demande. Ce service va automatiquement trouver et exécuter toutes les implémentations de HealthCheck (comme CmdbHealthCheck).
code
Java
// src/main/java/com/votreentreprise/monitoring/scheduler/HealthCheckRunner.java
package com.votreentreprise.monitoring.scheduler;

import com.votreentreprise.monitoring.model.HealthCheckResult;
import com.votreentreprise.monitoring.repository.HealthCheckResultRepository;
import com.votreentreprise.monitoring.service.HealthCheck;
import com.votreentreprise.monitoring.service.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ce service est un worker qui s'exécute périodiquement en arrière-plan.
 * Son rôle est d'exécuter tous les "health checks" disponibles dans l'application
 * et de sauvegarder leurs résultats en base de données.
 */
@Service
public class HealthCheckRunner {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckRunner.class);

    private final List<HealthCheck> allChecks;
    private final HealthCheckResultRepository resultRepository;
    private final String hostname;

    /**
     * Le constructeur utilise l'injection de dépendances de Spring.
     * @param allChecks Spring va automatiquement injecter ici une liste de TOUS les beans
     *                  qui implémentent l'interface HealthCheck. C'est ce qui rend le
     *                  système extensible : il suffit d'ajouter une nouvelle classe @Component
     *                  implémentant HealthCheck pour qu'elle soit prise en compte.
     * @param resultRepository Le repository pour sauvegarder les résultats.
     */
    public HealthCheckRunner(List<HealthCheck> allChecks, HealthCheckResultRepository resultRepository) {
        this.allChecks = allChecks;
        this.resultRepository = resultRepository;
        this.hostname = resolveHostname();
        log.info("HealthCheckRunner initialisé avec {} checks sur le serveur {}.", allChecks.size(), this.hostname);
    }

    /**
     * Cette méthode est le cœur du worker. L'annotation @Scheduled indique à Spring
     * de l'exécuter automatiquement à intervalle régulier.
     * `fixedRate = 300000` signifie toutes les 300 000 millisecondes, soit 5 minutes.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional // Enveloppe l'exécution dans une transaction de base de données.
    public void performAllChecks() {
        log.info("Début de l'exécution des vérifications de santé...");

        if (allChecks.isEmpty()) {
            log.warn("Aucun HealthCheck trouvé. Le worker n'a rien à faire.");
            return;
        }
        
        // On boucle sur chaque check injecté par Spring
        for (HealthCheck check : allChecks) {
            log.debug("Exécution du check: {}", check.getName());
            
            // 1. Exécuter la vérification
            HealthStatus status = check.check();

            // 2. Créer l'objet à sauvegarder en base
            HealthCheckResult result = new HealthCheckResult();
            result.setCheckName(check.getName());
            result.setStatus(status.getStatus());
            result.setDetails(status.getDetails());
            result.setHostname(this.hostname);
            result.setCheckedAt(LocalDateTime.now());

            // 3. Sauvegarder le résultat
            resultRepository.save(result);
            log.debug("Résultat pour '{}' : {} - Sauvegardé en BDD.", check.getName(), status.getStatus());
        }

        log.info("Toutes les vérifications de santé ont été exécutées et sauvegardées.");
    }

    /**
     * Méthode utilitaire pour récupérer le nom du serveur sur lequel l'application tourne.
     * Conforme à la demande du ticket de stocker le "hostname".
     */
    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.error("Impossible de déterminer le nom du serveur. Utilisation de 'unknown-host'.", e);
            return "unknown-host";
        }
    }
}
Dernière étape : Activer le Scheduling
Pour que l'annotation @Scheduled fonctionne, vous devez l'activer dans votre application Spring Boot. C'est une seule ligne à ajouter dans votre classe principale :
code
Java
// src/main/java/com/votreentreprise/MonitoringApplication.java
package com.votreentreprise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // <-- AJOUTER CETTE ANNOTATION !
public class MonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoringApplication.class, args);
    }
}
Avec ce code, vous avez un worker robuste, découplé et facile à étendre pour répondre parfaitement aux exigences de votre ticket.
//////////////////////////

Parfait, commençons pas à pas. C'est la meilleure approche.
La première étape est de créer la table dans votre base de données Oracle. Voici le script SQL CREATE TABLE qui correspond exactement au modèle de données dont nous avons besoin. Il est spécifiquement écrit pour la syntaxe Oracle.
Étape 1 : Le Script SQL pour Oracle
Ce script va créer :
La table HEALTH_CHECK_RESULTS.
Un index pour optimiser les recherches de performance, ce qui est une bonne pratique.
code
SQL
-- =====================================================================
-- Script de création de la table pour les résultats du Health Check
-- Base de données cible : Oracle
-- =====================================================================

-- Création de la table principale
CREATE TABLE HEALTH_CHECK_RESULTS (
    -- Clé primaire auto-incrémentée. C'est la méthode moderne (Oracle 12c+)
    ID NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    -- Nom du service vérifié (ex: 'cmdb', 'ldap_refsg'). Non nul.
    CHECK_NAME VARCHAR2(100 CHAR) NOT NULL,

    -- Statut du check (ex: 'OK', 'KO'). Non nul.
    STATUS VARCHAR2(20 CHAR) NOT NULL,

    -- Champ pour les détails ou messages d'erreur. CLOB pour stocker du texte long.
    DETAILS CLOB,

    -- Nom du serveur qui a exécuté la vérification. Non nul.
    HOSTNAME VARCHAR2(255 CHAR) NOT NULL,

    -- Date et heure précises de la fin de la vérification. Non nul.
    -- TIMESTAMP est plus précis que DATE car il inclut les fractions de seconde.
    CHECKED_AT TIMESTAMP NOT NULL
);

-- Commentaire pour expliquer le but de la table
COMMENT ON TABLE HEALTH_CHECK_RESULTS IS 'Stocke les résultats des vérifications de santé périodiques des services dépendants.';
COMMENT ON COLUMN HEALTH_CHECK_RESULTS.ID IS 'Identifiant unique auto-généré pour chaque résultat de check.';
COMMENT ON COLUMN HEALTH_CHECK_RESULTS.CHECK_NAME IS 'Nom technique du service qui a été vérifié (ex: cmdb, hvault).';
COMMENT ON COLUMN HEALTH_CHECK_RESULTS.STATUS IS 'Résultat de la vérification (ex: OK, KO, TIMEOUT).';
COMMENT ON COLUMN HEALTH_CHECK_RESULTS.DETAILS IS 'Message détaillé en cas d''erreur ou information complémentaire.';
COMMENT ON COLUMN HEALTH_CHECK_RESULTS.HOSTNAME IS 'Nom du serveur applicatif qui a effectué la vérification.';
COMMENT ON COLUMN HEALTH_CHECK_RESULTS.CHECKED_AT IS 'Date et heure exactes auxquelles la vérification a été effectuée.';


-- Création d'un index pour optimiser la requête qui récupère les derniers résultats.
-- La requête va filtrer et trier sur ces colonnes. L'index est donc crucial.
CREATE INDEX IDX_HEALTH_CHECK_LATEST 
ON HEALTH_CHECK_RESULTS (CHECK_NAME, HOSTNAME, CHECKED_AT DESC);
////////////////////
# Configuration de notre scheduler (optionnel, la valeur par défaut est 300000)
monitoring.check.frequency-ms=300000
//////////////////////////
Étape 3 : Mettre à jour CmdbHealthCheck pour utiliser RestTemplate
Maintenant, nous pouvons injecter notre RestTemplate configuré et l'URL dans notre classe de check.
Voici le code complet et commenté :
code
Java
// Emplacement : com/bnpparibas/certis/api/healthcheck/service/impl/CmdbHealthCheck.java
package com.bnpparibas.certis.api.healthcheck.service.impl;

import com.bnpparibas.certis.api.healthcheck.service.HealthCheck;
import com.bnpparibas.certis.api.healthcheck.service.HealthStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class CmdbHealthCheck implements HealthCheck {

    private final RestTemplate restTemplate;
    private final String cmdbHealthUrl;

    /**
     * Le constructeur injecte les dépendances fournies par Spring :
     * @param restTemplate Le bean RestTemplate que nous avons configuré à l'étape 1.
     * @param cmdbHealthUrl L'URL que nous avons configurée à l'étape 2.
     */
    public CmdbHealthCheck(RestTemplate restTemplate, 
                           @Value("${healthcheck.cmdb.url}") String cmdbHealthUrl) {
        this.restTemplate = restTemplate;
        this.cmdbHealthUrl = cmdbHealthUrl;
    }

    @Override
    public String getName() {
        return "cmdb";
    }

    @Override
    public HealthStatus check() {
        try {
            // Utiliser getForEntity() est préférable à getForObject() pour un health check,
            // car cela nous donne accès au code de statut HTTP de la réponse.
            ResponseEntity<String> response = restTemplate.getForEntity(cmdbHealthUrl, String.class);

            // On vérifie si le statut est un succès (2xx)
            if (response.getStatusCode().is2xxSuccessful()) {
                return HealthStatus.ok("Service CMDB accessible et répond avec le statut " + response.getStatusCode());
            } else {
                // Si le service répond avec un code d'erreur (4xx, 5xx)
                return HealthStatus.ko("Service CMDB a répondu avec un statut d'erreur : " + response.getStatusCode());
            }
        } catch (Exception e) {
            // Cette partie capture toutes les autres erreurs :
            // - Problèmes réseau (ex: service injoignable, timeout)
            // - Erreurs de DNS, de certificat SSL, etc.
            // RestTemplate lève des exceptions de type RestClientException dans ces cas.
            return HealthStatus.ko("Impossible de contacter le service CMDB. Erreur : " + e.getMessage());
        }
    }
}
Résumé des Bonnes Pratiques Appliquées
Injection de Dépendances : On n'instancie pas RestTemplate nous-mêmes, on le laisse être géré par Spring.
Configuration Centralisée : Les timeouts sont définis une seule fois dans la classe RestTemplateConfig. Si vous avez 10 checks qui utilisent RestTemplate, ils bénéficieront tous de cette configuration.
Configuration Externalisée : L'URL est dans application.properties, ce qui vous permet d'avoir des URL différentes pour vos environnements de DEV, QUAL et PROD sans changer le code.
Gestion Robuste des Erreurs : Le bloc try-catch gère à la fois les réponses HTTP non-2xx et les erreurs de communication pures (réseau, timeout, etc.), garantissant que votre worker ne plantera jamais à cause d'un service distant défaillant.
Utilisation de getForEntity : Permet une vérification fine du code de statut HTTP, ce qui est plus précis pour un health check.