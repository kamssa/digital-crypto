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
//////////////////////////
Excellente question, c'est un point central de l'architecture !
HealthStatus est une classe de transport de données (un DTO - Data Transfer Object).
Son unique rôle est de transporter le résultat d'un seul check de manière structurée et standardisée. Il sert de "messager" entre la classe qui exécute la vérification (ex: CmdbHealthCheck) et la classe qui orchestre toutes les vérifications (le HealthCheckRunner).
Visualisons son parcours, c'est le plus simple pour comprendre :
Le Voyage d'un Objet HealthStatus
Imaginez que le HealthCheckRunner demande à CmdbHealthCheck de faire son travail.
Étape 1 : Création (dans le HealthCheck spécifique)
La classe CmdbHealthCheck exécute sa logique (l'appel RestTemplate).
Si l'appel réussit : La classe crée un objet HealthStatus qui représente le succès.
code
Java
// Dans CmdbHealthCheck.java
return HealthStatus.ok("Service CMDB accessible."); 
// Crée un new HealthStatus("OK", "Service CMDB accessible.");
Si l'appel échoue : La classe crée un objet HealthStatus qui représente l'échec, en y incluant la raison.
code
Java
// Dans CmdbHealthCheck.java
return HealthStatus.ko("Impossible de contacter le service. Erreur : " + e.getMessage());
// Crée un new HealthStatus("KO", "Impossible de contacter le service...");
À ce niveau, HealthStatus sert de bulletin de notes pour le check qui vient de s'exécuter.
Étape 2 : Réception (par le HealthCheckRunner)
Le HealthCheckRunner, qui a appelé la méthode check(), reçoit ce "bulletin de notes". Il ne sait pas comment le check a été fait, mais il reçoit un résultat standardisé.
code
Java
// Dans HealthCheckRunner.java
for (HealthCheck check : allChecks) {
    // 1. Le Runner reçoit l'objet HealthStatus
    HealthStatus status = check.check(); 

    // ... la suite du code
}
Étape 3 : Utilisation (par le HealthCheckRunner pour préparer la sauvegarde)
Le HealthCheckRunner "ouvre" l'objet HealthStatus pour en lire les informations. Il utilise ces informations pour remplir l'objet HealthCheckResult qui, lui, sera sauvegardé en base de données.
code
Java
// Dans HealthCheckRunner.java (suite)

// 2. Le Runner crée l'entité qui sera sauvegardée
HealthCheckResult result = new HealthCheckResult();

// 3. Il utilise les informations de HealthStatus pour remplir l'entité
result.setCheckName(check.getName());
result.setStatus(status.getStatus());     // <- Utilise le statut de HealthStatus
result.setDetails(status.getDetails());   // <- Utilise les détails de HealthStatus
result.setHostname(this.hostname);
result.setCheckedAt(LocalDateTime.now());
            
resultRepository.save(result);
Pourquoi est-ce une bonne architecture ?
Utiliser cette classe HealthStatus intermédiaire nous apporte 3 avantages majeurs :
Standardisation : Que le check se fasse via REST, LDAP ou une connexion à une base de données, le HealthCheckRunner reçoit toujours le même type d'objet en retour. Il n'a pas besoin de s'adapter.
Richesse de l'Information : Une simple String ("OK"/"KO") ou un boolean ne suffirait pas. HealthStatus nous permet de transporter à la fois un statut simple ET des détails utiles pour le débogage en cas de problème.
Découplage : Le HealthCheckRunner n'a absolument aucune connaissance de RestTemplate ou de la logique interne de CmdbHealthCheck. Il ne connaît que l'interface HealthCheck et l'objet de retour HealthStatus. C'est ce qui rend le système si facile à étendre.
En résumé, HealthStatus est le format de communication standardisé entre chaque "vérificateur" et le "chef d'orchestre".
/////////////////////////////////////
Bien sûr, voici des exemples concrets pour chaque type de Subject Alternative Name (SAN) que vous avez demandé. Ces exemples sont parfaits pour des tests unitaires, des tests d'intégration ou pour alimenter des formulaires dans votre application.
1. RFC822NAME (Adresse E-mail)
C'est le type de SAN standard pour représenter une adresse e-mail. Le format est simplement utilisateur@domaine.
Exemples pour vos tests :
Standard :
utilisateur.test@example.com
Avec un sous-domaine :
support@it.entreprise.fr
Avec un alias (le signe + est valide) :
jean.dupont+projetA@gmail.com
Simple, sans point dans le nom :
admin@mondomaine.io
2. OTHERNAME_UPN (User Principal Name)
L'UPN (User Principal Name) est un nom d'utilisateur au format d'une adresse e-mail. Il est principalement utilisé dans les environnements Microsoft Active Directory pour l'authentification. Il ne correspond pas forcément à une boîte mail réelle.
Techniquement, c'est un type otherName avec un identifiant d'objet (OID) spécifique : 1.3.6.1.4.1.311.20.2.3. La valeur est le nom UPN lui-même.
Exemples pour vos tests :
Classique d'entreprise (domaine interne) :
jdoe@internal.mycompany.corp
Avec un domaine Active Directory simple :
asmith@AD.LOCAL
Format plus complexe :
prenom.nom@emea.corp.societe.com
Utilisateur de service (service account) :
srv_app_certis@ad.bnpparibas.net
3. OTHERNAME_GUID (Globally Unique Identifier)
Le GUID (Globally Unique Identifier), aussi connu sous le nom d'UUID (Universally Unique Identifier), est un identifiant unique de 128 bits. Dans le contexte de la certification, il est souvent utilisé pour lier un certificat à un objet spécifique dans un annuaire, comme l'attribut objectGUID d'un utilisateur ou d'un ordinateur dans Active Directory.
Le format standard est une chaîne de 32 caractères hexadécimaux, généralement groupés au format 8-4-4-4-12.
Exemples pour vos tests :
Exemple 1 (standard, en minuscules) :
123e4567-e89b-12d3-a456-426614174000
Exemple 2 (généré aléatoirement) :
f81d4fae-7dec-11d0-a765-00a0c91e6bf6
Exemple 3 (avec des majuscules, ce qui est aussi valide) :
A9B8C7D6-E5F4-A3B2-C1D0-E9F8A7B6C5D4
Exemple 4 (sans les tirets, certains systèmes le fournissent ainsi) :
a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5d4
Tableau Récapitulatif
Type de SAN	Description	Format d'Exemple
RFC822NAME	Adresse e-mail standard	nom.prenom@example.com
OTHERNAME_UPN	Identifiant de connexion Active Directory	utilisateur@domaine.interne.corp
OTHERNAME_GUID	Identifiant unique universel (UUID)	xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
Vous pouvez utiliser ces chaînes de caractères directement comme valeurs dans vos DTOs, vos corps de requêtes JSON, ou vos tests pour simuler les différentes données que votre système pourrait recevoir.
////////////////////////////
email.1 = utilisateur.test@example.com

# 2. OTHERNAME_UPN (User Principal Name)
# Syntaxe: OID;TYPE:VALEUR
# OID pour UPN : 1.3.6.1.4.1.311.20.2.3
otherName.1 = 1.3.6.1.4.1.311.20.2.3;UTF8String:utilisateur@domaine.interne.corp

# 3. OTHERNAME_GUID (Globally Unique Identifier, ici comme objectGUID)
# Syntaxe: OID;FORMAT:HEX,VALUE:VALEUR_HEX_SANS_TIRETS
# OID pour objectGUID: 1.3.6.1.4.1.311.25.1
# La valeur est le GUID SANS LES TIRETS
# Par exemple, pour le GUID "123e4567-e89b-12d3-a456-426614174000"
otherName.2 = 1.3.6.1.4.1.311.25.1;FORMAT:HEX,VALUE:123e4567e89b12d3a456426614174000
1.3.6.1.4.1.311.25.1;ASN1:OCTET_STRING:123e4567e89b12d3a456426614174000
echo "otherName.2 = 1.3.6.1.4.1.311.25.1;HEX:123e4567e89b12d3a456426614174000"
//////////////////////
DNS.1 = bnpTiti.staging.intra
IP.1 = 2.2.2.2
URI.1 = http://bnpTiti.com/app
email.1 = utilisateur.test@example.com
otherName.1 = 1.3.6.1.4.1.311.20.2.3;UTF8String:utilisateur@domaine.interne.corp
otherName.2 = 1.3.6.1.4.1.311.25.1;ASN1:OCTET_STRING:123e4567e89b12d3a456426614174000
//////////////////// suite du ticket ////////////////////////
Explication Détaillée
import java.net.InetAddress; : C'est la classe principale du JDK Java pour tout ce qui concerne les adresses IP et les noms d'hôtes.
InetAddress.getLocalHost() : C'est un appel statique qui demande à Java de trouver l'adresse de la machine locale (localhost). Java va essayer de déterminer cela en se basant sur la configuration réseau du système d'exploitation.
.getHostName() : Une fois que Java a trouvé l'adresse de la machine locale, cette méthode demande son nom. Par exemple, si votre serveur s'appelle SRV-APP-PROD-01, c'est cette chaîne de caractères qui sera retournée.
try { ... } catch (UnknownHostException e) { ... } : C'est la partie la plus importante pour la robustesse. L'appel à InetAddress.getLocalHost() peut échouer. Par exemple, dans un conteneur Docker mal configuré ou sur une machine sans réseau correctement initialisé, Java pourrait ne pas être capable de trouver le nom d'hôte.
Si cela arrive, une UnknownHostException est levée.
Au lieu de laisser l'application planter, nous l'interceptons (catch).
Dans le bloc catch, nous faisons deux choses :
On écrit une erreur dans les logs (log.error(...)) pour que les administrateurs système soient au courant du problème de configuration.
On retourne une valeur par défaut, "unknown-host", pour que le reste de l'application puisse continuer à fonctionner normalement.
Contexte d'Utilisation dans HealthCheckRunner
Cette méthode est appelée une seule fois, dans le constructeur de la classe HealthCheckRunner. On stocke le résultat dans une variable final pour ne pas avoir à refaire cet appel (qui peut être un peu coûteux) toutes les 5 minutes.
Voici à quoi ressemble l'intégration finale :
code
Java
@Service
public class HealthCheckRunner {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckRunner.class);

    private final List<HealthCheck> allChecks;
    private final HealthCheckResultRepository resultRepository;
    private final String hostname; // Le nom du serveur sera stocké ici

    public HealthCheckRunner(List<HealthCheck> allChecks, HealthCheckResultRepository resultRepository) {
        this.allChecks = allChecks;
        this.resultRepository = resultRepository;
        this.hostname = resolveHostname(); // Appel de la méthode au démarrage !
        log.info("HealthCheckRunner initialisé sur le serveur : {}", this.hostname);
    }
    
    // ... reste de la classe (performAllChecks, etc.)

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.error("Impossible de déterminer le nom du serveur. Utilisation de 'unknown-host'.", e);
            return "unknown-host";
        }
    }
}
///////////////////////////////////////////////////////////
Excellente question, c'est le cœur du problème ! Pour "atteindre" un serveur distant, votre application se comporte exactement comme un navigateur web ou tout autre client réseau.
Pour faire simple, imaginez que votre application Java veut poser une question à un service distant (par exemple, "Es-tu en bonne santé ?"). Pour cela, elle a besoin de 4 informations essentielles, tout comme pour envoyer une lettre par la poste :
Le Protocole : Quelle "langue" ou quel "canal" utiliser ? (ex: HTTP, HTTPS, LDAP, JDBC pour les bases de données...).
L'Adresse du Serveur (Hostname) : Où se trouve le bâtiment ? (ex: cmdb.mon-entreprise.com ou une adresse IP comme 10.20.30.40).
Le Port : À quelle "porte" du bâtiment faut-il frapper ? Chaque service sur un serveur écoute sur une porte numérotée. (ex: 80 pour HTTP, 443 pour HTTPS, 389 pour LDAP).
Le Chemin (Endpoint) : Une fois dans le bâtiment, à quel "bureau" s'adresser pour poser la question ? (ex: /api/health, /status).
Ces informations sont généralement combinées pour former une URL (Uniform Resource Locator).
Le Cas le plus Courant : L'Appel API REST avec RestTemplate
Pour la majorité des services modernes (comme le CMDB, l'API Snow, etc.), la communication se fait via des API REST sur HTTPS. En Java, l'outil standard pour cela est RestTemplate.
Voici comment le code CmdbHealthCheck "atteint" le serveur distant, étape par étape :
code
Java
// Dans CmdbHealthCheck.java

// ...

public HealthStatus check() {
    try {
        // 1. L'ADRESSE COMPLÈTE
        // L'URL combine le protocole (https://), l'hostname (cmdb.mon-entreprise.com)
        // et le chemin (/api/health). Le port 443 est implicite pour HTTPS.
        String cmdbHealthUrl = "https://cmdb.mon-entreprise.com/api/health";

        // 2. L'OUTIL POUR ENVOYER LA REQUÊTE
        // RestTemplate est notre "agent" qui va physiquement envoyer la requête sur le réseau.
        // (Rappel : dans le code final, cet objet est injecté et configuré par Spring).
        RestTemplate restTemplate = new RestTemplate();

        // 3. L'ACTION : ENVOYER LA REQUÊTE ET ATTENDRE LA RÉPONSE
        // C'est ici que la magie opère. La ligne suivante :
        // - Ouvre une connexion réseau vers cmdb.mon-entreprise.com sur le port 443.
        // - Envoie une requête HTTP GET pour le chemin /api/health.
        // - Attend la réponse du serveur distant.
        // - Si la réponse arrive, elle est stockée dans la variable 'response'.
        ResponseEntity<String> response = restTemplate.getForEntity(cmdbHealthUrl, String.class);

        // 4. ANALYSER LA RÉPONSE
        // Si nous sommes arrivés ici, cela signifie que nous avons réussi à "atteindre" le serveur
        // et qu'il nous a répondu.
        if (response.getStatusCode().is2xxSuccessful()) {
            return HealthStatus.ok("Connexion réussie.");
        } else {
            return HealthStatus.ko("Le serveur a répondu avec une erreur : " + response.getStatusCode());
        }

    } catch (Exception e) {
        // 5. GÉRER L'ÉCHEC
        // Si le serveur ne peut pas être atteint (il est éteint, problème de pare-feu, mauvais hostname...),
        // RestTemplate lèvera une exception.
        // C'est notre signal que le serveur est "injoignable".
        return HealthStatus.ko("Impossible d'atteindre le serveur : " + e.getMessage());
    }
}
Comment ça marche pour les autres services ?
Le principe reste le même, mais l'outil et le protocole changent.
Type de Service	Protocole	Exemple d'Adresse	Outil Java / Spring	Comment ça marche ?
API REST (CMDB, Snow...)	HTTP/HTTPS	https://cmdb.service.com/api/status	RestTemplate	Envoie une requête HTTP et analyse la réponse HTTP.
Annuaire LDAP (ldap_refsg)	LDAP/LDAPS	ldap://ldap.mon-entreprise.com:389	LdapTemplate (de Spring LDAP)	Ouvre une connexion TCP sur le port 389 et envoie des commandes LDAP (ex: une tentative de connexion ou "bind").
Base de Données (Oracle, etc.)	JDBC (via SQL*Net)	jdbc:oracle:thin:@db.server.com:1521:SID	DataSource / JdbcTemplate	Utilise un driver JDBC pour établir une connexion à la base et exécuter une requête SQL très simple (ex: SELECT 1 FROM DUAL).
Vault (hvault)	HTTP/HTTPS	https://vault.service.com:8200/v1/sys/health	RestTemplate	Comme une API REST, Vault expose des endpoints HTTP pour vérifier son état.
En résumé, pour atteindre un serveur distant, vous devez :
Connaître son adresse (protocole, hostname, port, chemin).
Utiliser la bibliothèque Java appropriée (RestTemplate pour HTTP, JdbcTemplate pour les DB, etc.).
Appeler une méthode de cette bibliothèque qui va se charger d'établir la connexion réseau et d'envoyer la requête.
Interpréter la réponse ou gérer l'exception si la connexion échoue.
//////////////// resumer pour resoudre l'us ///////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////
Absolument. Voici un résumé complet et structuré de tout le code nécessaire pour implémenter la fonctionnalité de A à Z, avec des explications détaillées pour chaque partie.
C'est un guide complet qui vous mènera de la base de données à l'API finale.
Résumé du Plan d'Action
Base de Données : Créer la table avec un script SQL.
Couche Données (Java) : Créer l'entité JPA et le Repository pour interagir avec la table.
Couche Service (Java) : Définir l'architecture extensible des checks (interface, DTO, implémentation).
Le Worker (Java) : Créer le service planifié (@Scheduled) qui exécute les checks.
L'API (Java) : Créer le Controller REST qui expose les résultats.
Configuration Finale : Activer la planification et configurer les URLs.
Étape 1 : La Base de Données (Script SQL pour Oracle)
C'est la fondation. Ce script crée la table qui stockera tous les résultats.
Fichier : create_table.sql
code
SQL
-- Création de la table pour stocker les résultats des vérifications de santé
CREATE TABLE HEALTH_CHECK_RESULTS (
    -- Clé primaire auto-incrémentée (syntaxe pour Oracle 12c+)
    ID NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    -- Nom du service vérifié (ex: 'cmdb', 'ldap_refsg'). Non nul.
    CHECK_NAME VARCHAR2(100 CHAR) NOT NULL,

    -- Statut du check (ex: 'OK', 'KO'). Non nul.
    STATUS VARCHAR2(20 CHAR) NOT NULL,

    -- Champ pour les détails ou messages d'erreur. CLOB pour du texte long.
    DETAILS CLOB,

    -- Nom du serveur qui a exécuté la vérification. Non nul.
    HOSTNAME VARCHAR2(255 CHAR) NOT NULL,

    -- Date et heure précises de la fin de la vérification. Non nul.
    CHECKED_AT TIMESTAMP NOT NULL
);

-- Index pour accélérer la recherche des derniers résultats pour chaque service/hostname.
CREATE INDEX IDX_HEALTH_CHECK_LATEST
ON HEALTH_CHECK_RESULTS (CHECK_NAME, HOSTNAME, CHECKED_AT DESC);
Explication :
Cette table est conçue pour stocker l'historique de chaque check (CHECK_NAME) exécuté depuis chaque serveur (HOSTNAME).
L'index est crucial pour que la requête de l'API qui ne cherche que les "derniers résultats" soit très rapide.
Étape 2 : La Couche Données (Model & Repository)
Ce code permet à Java de comprendre et de manipuler la table HEALTH_CHECK_RESULTS.
Fichier : .../healthcheck/model/HealthCheckResult.java
code
Java
package com.bnpparibas.certis.api.healthcheck.model;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data // Lombok pour générer getters/setters/etc.
@Entity
@Table(name = "HEALTH_CHECK_RESULTS")
public class HealthCheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CHECK_NAME", nullable = false)
    private String checkName;

    @Column(name = "STATUS", nullable = false)
    private String status;

    @Lob
    @Column(name = "DETAILS")
    private String details;

    @Column(name = "HOSTNAME", nullable = false)
    private String hostname;

    @Column(name = "CHECKED_AT", nullable = false)
    private LocalDateTime checkedAt;
}
Explication :
C'est une classe "miroir" de notre table SQL.
Les annotations JPA (@Entity, @Table, @Id, @Column...) indiquent à Spring Data JPA comment faire le lien entre cette classe Java et la table en base de données.
Fichier : .../healthcheck/repository/HealthCheckResultRepository.java
code
Java
package com.bnpparibas.certis.api.healthcheck.repository;

import com.bnpparibas.certis.api.healthcheck.model.HealthCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HealthCheckResultRepository extends JpaRepository<HealthCheckResult, Long> {

    @Query(value = "SELECT * FROM (" +
                   "    SELECT h.*, ROW_NUMBER() OVER (PARTITION BY check_name, hostname ORDER BY checked_at DESC) as rn " +
                   "    FROM health_check_results h" +
                   ") sub " +
                   "WHERE sub.rn = 1", nativeQuery = true)
    List<HealthCheckResult> findLatestResults();
}
Explication :
Cette interface nous donne les méthodes de base pour interagir avec la table (save(), findById(), etc.) grâce à JpaRepository.
La méthode findLatestResults contient la requête SQL native (car complexe) qui récupère uniquement la ligne la plus récente pour chaque paire (check_name, hostname), exactement ce dont l'API a besoin.
Étape 3 : La Couche Service (Le Cœur Extensible)
C'est la logique métier. On définit un contrat (HealthCheck) et chaque service à vérifier l'implémente.
Fichier : .../healthcheck/service/HealthCheck.java (L'Interface)
code
Java
package com.bnpparibas.certis.api.healthcheck.service;

public interface HealthCheck {
    String getName();
    HealthStatus check();
}
Explication : C'est le contrat. Toute classe qui implémente cette interface doit fournir un nom et une méthode de vérification.
Fichier : .../healthcheck/service/HealthStatus.java (Le DTO)
code
Java
package com.bnpparibas.certis.api.healthcheck.service;

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
Explication : C'est un simple "messager" qui transporte le résultat d'un check (un statut et des détails) de manière standardisée.
Fichier : .../healthcheck/service/impl/CmdbHealthCheck.java (Une Implémentation)
code
Java
package com.bnpparibas.certis.api.healthcheck.service.impl;

import com.bnpparibas.certis.api.healthcheck.service.HealthCheck;
import com.bnpparibas.certis.api.healthcheck.service.HealthStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component // Important pour que Spring la trouve !
public class CmdbHealthCheck implements HealthCheck {

    private final RestTemplate restTemplate;
    private final String cmdbHealthUrl;

    public CmdbHealthCheck(RestTemplate restTemplate, @Value("${healthcheck.cmdb.url}") String cmdbHealthUrl) {
        this.restTemplate = restTemplate;
        this.cmdbHealthUrl = cmdbHealthUrl;
    }

    @Override
    public String getName() {
        return "cmdb"; // Nom demandé dans le ticket
    }

    @Override
    public HealthStatus check() {
        try {
            restTemplate.getForEntity(cmdbHealthUrl, String.class);
            return HealthStatus.ok("Service CMDB accessible.");
        } catch (Exception e) {
            return HealthStatus.ko("Impossible de contacter le service CMDB : " + e.getMessage());
        }
    }
}
Explication :
C'est la logique concrète pour vérifier le service "cmdb".
Elle utilise RestTemplate pour faire un appel HTTP à une URL.
Elle est robuste : le try-catch garantit qu'elle retournera toujours un HealthStatus (OK ou KO) sans jamais planter.
Étape 4 : Le Worker (Le Moteur Périodique)
Ce service est le chef d'orchestre. Il s'exécute toutes les 5 minutes.
Fichier : .../healthcheck/tasks/HealthCheckRunner.java
code
Java
package com.bnpparibas.certis.api.healthcheck.tasks;

// ... imports ...

@Service
public class HealthCheckRunner {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckRunner.class);

    private final List<HealthCheck> allChecks;
    private final HealthCheckResultRepository resultRepository;
    private final String hostname;

    public HealthCheckRunner(List<HealthCheck> allChecks, HealthCheckResultRepository resultRepository) {
        this.allChecks = allChecks;
        this.resultRepository = resultRepository;
        this.hostname = resolveHostname();
    }

    @Scheduled(fixedRate = 300000) // 300 000 ms = 5 minutes
    public void performAllChecks() {
        log.info("Exécution des vérifications de santé sur le serveur {}", hostname);
        for (HealthCheck check : allChecks) {
            HealthStatus status = check.check();
            HealthCheckResult result = new HealthCheckResult();
            result.setCheckName(check.getName());
            result.setStatus(status.getStatus());
            result.setDetails(status.getDetails());
            result.setHostname(this.hostname);
            result.setCheckedAt(LocalDateTime.now());
            resultRepository.save(result);
        }
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.error("Impossible de déterminer le nom du serveur. Utilisation de 'unknown-host'.", e);
            return "unknown-host";
        }
    }
}
Explication :
@Scheduled dit à Spring d'exécuter performAllChecks() toutes les 5 minutes.
La magie de Spring : List<HealthCheck> allChecks est automatiquement remplie avec toutes les implémentations que vous créez (comme CmdbHealthCheck).
Pour chaque check, il exécute la vérification, puis utilise le résultat pour créer et sauvegarder une entité HealthCheckResult en base de données.
resolveHostname récupère le nom du serveur pour l'enregistrer avec le résultat.
Étape 5 : L'API (La Partie Visible)
Ce controller expose les résultats stockés en base. Il n'exécute jamais de check lui-même.
Fichier : .../healthcheck/controller/HealthController.java
code
Java
package com.bnpparibas.certis.api.healthcheck.controller;

// ... imports ...

@RestController
public class HealthController {

    private final HealthCheckResultRepository repository;

    public HealthController(HealthCheckResultRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/health")
    public Map<String, Map<String, String>> getAggregatedHealthStatus() {
        List<HealthCheckResult> latestResults = repository.findLatestResults();

        // Transformation de la liste de résultats en la structure JSON demandée
        return latestResults.stream()
            .collect(Collectors.groupingBy(
                HealthCheckResult::getHostname,
                Collectors.toMap(
                    HealthCheckResult::getCheckName,
                    HealthCheckResult::getStatus
                )
            ));
    }
}
Explication :
@GetMapping("/health") crée l'endpoint GET /health.
Il appelle la méthode findLatestResults() du repository pour obtenir les données fraîches de la base.
Il utilise les Streams Java 8 pour transformer la liste de résultats en une Map imbriquée qui correspondra parfaitement au format JSON attendu dans le ticket.
Étape 6 : La Configuration Finale
Il ne reste plus qu'à activer le système et à fournir les configurations.
Fichier : .../CertisApiApplication.java (Votre classe principale)
code
Java
package com.bnpparibas.certis.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // <-- TRÈS IMPORTANT : active le @Scheduled du HealthCheckRunner
public class CertisApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CertisApiApplication.class, args);
    }
}
Fichier : src/main/resources/application.properties
code
Properties
# Configuration de la base de données
spring.datasource.url=jdbc:oracle:thin:@...
spring.datasource.username=...
spring.datasource.password=...

# URL pour les checks (exemple pour CMDB)
healthcheck.cmdb.url=http://url.de.votre.cmdb/api/health
Et voilà ! Vous avez un système de monitoring complet, robuste, performant et facile à étendre, qui répond à toutes les exigences du ticket Jira.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
/////////////////////////// quelques explications//////////////////////////////

Model
C'est une excellente question, et elle met le doigt sur un point crucial de l'architecture. Vous avez parfaitement raison de vous interroger sur ce point.
La confusion vient de la distinction entre QUI fait le test et QUOI est testé.
Laissez-moi clarifier avec une analogie simple.
Imaginez que vous avez deux employés, Alice et Bob, dans deux bureaux différents (Paris et Lyon). Leur travail est de téléphoner à un service client centralisé (le service distant) toutes les 5 minutes pour vérifier qu'il répond.
Après chaque appel, ils notent le résultat dans un carnet commun :
10:05 - Alice (Paris) - Le service a répondu - OK
10:05 - Bob (Lyon) - Le service n'a pas répondu - KO
Dans cette analogie :
Alice est votre application Java qui tourne sur le serveur A.
Bob est votre application Java qui tourne sur le serveur B.
Le service client est le service distant (ex: le CMDB).
Le carnet commun est votre base de données HEALTH_CHECK_RESULTS.
À quoi sert InetAddress.getLocalHost().getHostName() ?
Cette méthode sert à répondre à la question : "Qui suis-je ?".
Quand le code s'exécute sur le serveur d'Alice, getLocalHost().getHostName() retourne Paris. C'est le nom de la machine qui effectue le test.
Quand le code s'exécute sur le serveur de Bob, getLocalHost().getHostName() retourne Lyon.
Pourquoi est-ce si important ?
Le ticket Jira précise :
"La task ne devra pas etre sous shedlock et devra s'executer sur chaque serveur. Dans la base pour chaque check il faudra tester le hostname ayant effectué le check."
Le but est de pouvoir diagnostiquer des problèmes qui ne sont visibles que depuis un seul serveur. Reprenons l'exemple :
À 10h05, le service client est inaccessible uniquement pour Bob (peut-être un problème de réseau spécifique à son bureau de Lyon).
Grâce à getLocalHost().getHostName(), votre base de données va contenir des informations très précises :
CHECK_NAME	STATUS	HOSTNAME	CHECKED_AT
cmdb	OK	serveur-alice-paris	10:05:01
cmdb	KO	serveur-bob-lyon	10:05:02
Quand vous consultez l'API /health, vous voyez immédiatement :
Le service cmdb est OK du point de vue du serveur de Paris.
Le service cmdb est KO du point de vue du serveur de Lyon.
Conclusion : Le problème ne vient probablement pas du service cmdb lui-même, mais de la connectivité réseau entre le serveur de Lyon et le service cmdb.
En résumé :
InetAddress.getLocalHost().getHostName() ne sert PAS à trouver l'adresse du service distant. Il sert à identifier le serveur local qui est en train d'exécuter le test.
L'adresse du service distant est, elle, définie dans une URL (ex: healthcheck.cmdb.url=...) et utilisée par RestTemplate pour "atteindre" ce service.
Vous avez raison, le nom getLocalHost peut prêter à confusion. Il faut bien le comprendre comme "donne-moi les informations sur la machine sur laquelle je suis en train de tourner en ce moment".
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
//////////////////////////////////////
@Entity
@Setter
@Getter
@ToString
@Table(name = "health_check_results") // Assurez-vous que le nom de la table est correct
public class HealthCheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "healthCheckResult_id_generator")
    @SequenceGenerator(name = "healthCheckResult_id_generator", sequenceName = "SEQ_HEALTHCHECK_RESULT_ID", allocationSize = 1)
    @Column(name = "ID") // Explicite pour la colonne ID
    private Long id;

    @Column(name = "CHECK_NAME", nullable = false) // Annotation pour checkName
    private String checkName;

    @Column(name = "STATUS", nullable = false) // Annotation pour status
    private String status;

    @Lob // Pour les champs de type CLOB
    @Column(name = "DETAILS") // Annotation pour details
    private String details;

    @Column(name = "HOSTNAME", nullable = false) // Annotation pour hostname
    private String hostname;

    @Column(name = "CHECKED_AT", nullable = false) // Annotation pour checkedAt
    private LocalDateTime checkedAt;
}
//////////////////////
code
SQL
CREATE SEQUENCE SEQ_HEALTHCHECK_RESULT_ID
START WITH 1
INCREMENT BY 1
NOCACHE
NOCYCLE;
/////////////////////////////////////////////
code
Java
package com.bnpparibas.certis.automationhub.healthcheck.dao;

import com.bnpparibas.certis.automationhub.healthcheck.model.HealthCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

// On peut ajouter l'annotation @Repository, c'est une bonne pratique
@Repository
public interface HealthCheckResultDao extends JpaRepository<HealthCheckResult, Long> {

    // La requête corrigée : on sélectionne explicitement les colonnes de l'entité
    @Query(value = "SELECT ID, CHECK_NAME, STATUS, DETAILS, HOSTNAME, CHECKED_AT FROM (" +
                   "    SELECT h.*, ROW_NUMBER() OVER (PARTITION BY check_name, hostname ORDER BY checked_at DESC) as rn " +
                   "    FROM health_check_results h" +
                   ") sub " +
                   "WHERE sub.rn = 1", nativeQuery = true)
    List<HealthCheckResult> findLatestResults();
}
Changement clé :
Au lieu de SELECT *, nous avons maintenant SELECT ID, CHECK_NAME, STATUS, DETAILS, HOSTNAME, CHECKED_AT.
Cette nouvelle requête retourne uniquement les colonnes que Hibernate connaît, en ignorant la colonne rn qui ne servait qu'au filtrage. Hibernate sera maintenant capable de mapper parfaitement le résultat à votre liste de HealthCheckResult.
Amélioration Optionnelle : Nettoyer le HealthController
J'ai remarqué une petite incohérence dans votre HealthController. Le paramètre du constructeur s'appelle repository mais le champ de la classe s'appelle healthCheckResultDao. C'est mieux d'utiliser le même nom pour la clarté.
Fichier : HealthController.java (Version plus propre)
code
Java
package com.bnpparibas.certis.api.controller;

import com.bnpparibas.certis.automationhub.healthcheck.dao.HealthCheckResultDao;
import com.bnpparibas.certis.automationhub.healthcheck.model.HealthCheckResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/health") // Le mapping de base pour ce contrôleur
public class HealthController {

    private final HealthCheckResultDao healthCheckResultDao;

    // On utilise le même nom pour le paramètre et le champ, c'est plus clair
    public HealthController(HealthCheckResultDao healthCheckResultDao) {
        this.healthCheckResultDao = healthCheckResultDao;
    }

    @GetMapping
    public Map<String, Map<String, String>> getAggregatedHealthStatus() {
        List<HealthCheckResult> latestResults = healthCheckResultDao.findLatestResults();

        return latestResults.stream()
                .collect(Collectors.groupingBy(
                        HealthCheckResult::getHostname,
                        Collectors.toMap(
                                HealthCheckResult::getCheckName,
                                HealthCheckResult::getStatus
                        )
                ));
    }
}
/////////////////////
ALTER TABLE HEALTH_CHECK_RESULTS MODIFY (DETAILS CLOB);
////////////////////////////
CREATE TABLE HEALTH_CHECK_RESULTS (
    ID NUMBER(19,0) NOT NULL PRIMARY KEY,
    CHECK_NAME VARCHAR2(100 CHAR) NOT NULL,
    STATUS VARCHAR2(20 CHAR) NOT NULL,
    DETAILS CLOB,
    HOSTNAME VARCHAR2(255 CHAR) NOT NULL,
    CHECKED_AT TIMESTAMP NOT NULL
);


-- 2. Création de la SÉQUENCE pour l'auto-incrémentation des IDs
-- L'entité Java utilise @GenerationType.SEQUENCE et s'attend à trouver cette séquence.
CREATE SEQUENCE SEQ_HEALTHCHECK_RESULT_ID
START WITH 1
INCREMENT BY 1
NOCACHE
NOCYCLE;


-- 3. Création de l'INDEX pour optimiser les performances de l'API /health
CREATE INDEX IDX_HEALTH_CHECK_LATEST
ON HEALTH_CHECK_RESULTS (CHECK_NAME, HOSTNAME, CHECKED_AT DESC);


-- 4. (Optionnel) Ajout de commentaires pour la documentation de la base de données
COMMENT ON TABLE HEALTH_CHECK_RESULTS IS 'Stocke les résultats des vérifications de santé périodiques.';
COMMENT ON COLUMN HEALTH_CHECK_RESULTS.ID IS 'Clé primaire alimentée par la séquence SEQ_HEALTHCHECK_RESULT_ID.';
COMMENT ON COLUMN HEALTH_CHECK_RESULTS.DETAILS IS 'Message détaillé (type CLOB pour stocker des textes longs comme des stack traces).';
//////////////////////////////////////////////
BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE HEALTH_CHECK_RESULTS';
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
   EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_HEALTHCHECK_RESULT_ID';
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -2289 THEN RAISE; END IF;
END;
/

-- 2. Recréation de la table et de la séquence
CREATE TABLE HEALTH_CHECK_RESULTS (
    ID NUMBER(19,0) NOT NULL PRIMARY KEY,
    CHECK_NAME VARCHAR2(100 CHAR) NOT NULL,
    STATUS VARCHAR2(20 CHAR) NOT NULL,
    DETAILS CLOB,
    HOSTNAME VARCHAR2(255 CHAR) NOT NULL,
    CHECKED_AT TIMESTAMP NOT NULL
);

CREATE SEQUENCE SEQ_HEALTHCHECK_RESULT_ID
START WITH 1
INCREMENT BY 1
NOCACHE
NOCYCLE;

CREATE INDEX IDX_HEALTH_CHECK_LATEST
ON HEALTH_CHECK_RESULTS (CHECK_NAME, HOSTNAME, CHECKED_AT DESC);

PROMPT La table HEALTH_CHECK_RESULTS et ses objets ont été recréés avec succès.;
////////////////////////////// snow ///////////////////////////////
Fichier : ServiceSnowHealthCheck.java (Version Corrigée)
code
Java
package com.bnpparibas.certis.automationhub.healthcheck.service.check; // Assurez-vous que le package est le bon

import com.bnpparibas.certis.api.healthcheck.service.HealthCheck;
import com.bnpparibas.certis.api.healthcheck.service.HealthStatus;
import com.bnpparibas.certis.itsm.exception.NotFoundException;
import com.bnpparibas.certis.itsm.service.SnowService;
import org.springframework.stereotype.Component;

/**
 * Vérifie l'état de santé du service ServiceNow (Snow).
 * Cette implémentation réutilise le service existant 'SnowService' pour effectuer une
 * lecture simple afin de valider la connectivité et l'authentification.
 */
@Component
public class ServiceSnowHealthCheck implements HealthCheck {

    private final SnowService snowService;

    /**
     * Constructeur pour l'injection de dépendances.
     * Spring va automatiquement fournir une instance de SnowService (implémentée par SnowApigeeServiceImpl).
     * @param snowService le service pour communiquer avec ServiceNow.
     */
    public ServiceSnowHealthCheck(SnowService snowService) {
        this.snowService = snowService;
    }

    @Override
    public String getName() {
        return "api snow"; // Nom demandé dans le ticket
    }

    @Override
    public HealthStatus check() {
        try {
            // STRATÉGIE : Nous tentons de récupérer un incident qui n'existe pas.
            // Si nous recevons une exception "NotFoundException", cela signifie que l'API a bien été contactée.
            // C'est donc un SUCCÈS pour notre check de connectivité.
            snowService.getSnowIncidentBySysId("HEALTHCHECK_ID_DOES_NOT_EXIST");

            // Si aucune exception n'est levée (très improbable), c'est aussi un succès.
            return HealthStatus.ok("Snow API is responding, but unexpectedly found a test incident.");

        } catch (NotFoundException e) {
            // CAS IDÉAL : L'API nous répond "Non trouvé". C'est la preuve que la connexion et l'authentification fonctionnent.
            return HealthStatus.ok("Successfully connected and authenticated to Snow API (received expected 404 Not Found).");

        } catch (Exception e) {
            // CAS D'ÉCHEC : Toute autre exception (timeout, erreur 503, erreur de parsing, etc.)
            // indique un réel problème de connectivité ou de configuration.
            return HealthStatus.ko("Failed to communicate with Snow API: " + e.getMessage());
        }
    }
}
/////////////////////////////////
La Solution : Le Code pour DatabaseHealthCheck.java
Il est plus logique de renommer le fichier CmdbHealthCheck.java en DatabaseHealthCheck.java ou ReferentialDbHealthCheck.java pour mieux refléter ce qu'il fait. Voici le code complet.
Fichier : DatabaseHealthCheck.java (Remplace votre CmdbHealthCheck.java)
code
Java
package com.bnpparibas.certis.automationhub.healthcheck.service.check; // Adaptez le package si besoin

import com.bnpparibas.certis.referential.refi.initialization.DynamicDataSource;
import com.bnpparibas.certis.api.healthcheck.service.HealthCheck;
import com.bnpparibas.certis.api.healthcheck.service.HealthStatus;
import org.springframework.stereotype.Component;

import java.sql.Connection;

/**
 * Vérifie l'état de santé de la connexion à la base de données PostgreSQL du référentiel (CMDB).
 * Cette implémentation réutilise le 'DynamicDataSource' existant, qui gère la récupération
 * des identifiants depuis Vault et l'établissement de la connexion.
 */
@Component
public class DatabaseHealthCheck implements HealthCheck {

    private final DynamicDataSource dynamicDataSource;

    /**
     * Constructeur pour l'injection de dépendances.
     * @param dynamicDataSource Le gestionnaire de connexion à la base de données.
     */
    public DatabaseHealthCheck(DynamicDataSource dynamicDataSource) {
        this.dynamicDataSource = dynamicDataSource;
    }

    @Override
    public String getName() {
        // Le ticket mentionne "cmdb", donc on garde ce nom pour la cohérence.
        // D'autres noms possibles seraient "referential-db" ou "postgresql-db".
        return "cmdb";
    }

    @Override
    public HealthStatus check() {
        Connection connection = null;
        try {
            // 1. Tenter d'obtenir une connexion depuis le DataSource.
            // Cette étape valide déjà la connexion à Vault et à la DB.
            connection = dynamicDataSource.getConnection();

            // 2. Vérifier que la connexion est réellement valide avec un timeout court.
            // Le "1" correspond à un timeout de 1 seconde.
            boolean isValid = connection.isValid(1);

            if (isValid) {
                return HealthStatus.ok("Successfully connected to the referential (CMDB) database.");
            } else {
                return HealthStatus.ko("Connection to the referential (CMDB) database was established but is not valid.");
            }

        } catch (Exception e) {
            // Toute exception ici (SQLException, erreur de Vault, etc.) indique un échec.
            return HealthStatus.ko("Failed to connect to the referential (CMDB) database: " + e.getMessage());

        } finally {
            // 3. TRÈS IMPORTANT : Toujours fermer la connexion !
            // Si on ne le fait pas, on va rapidement épuiser le pool de connexions
            // et faire planter l'application.
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    // Ignorer les erreurs à la fermeture ou les logger si nécessaire.
                }
            }
        }
    }
}
///////////////////////////
La Solution : Le Code pour VaultHealthCheck.java
Voici le code complet pour votre fichier VaultHealthCheck.java. Il est conçu pour être efficace et pour tester la bonne chose : la disponibilité de base du service Vault.
Fichier : VaultHealthCheck.java
code
Java
package com.bnpparibas.certis.automationhub.healthcheck.service.check; // Adaptez le package

import com.bnpparibas.certis.api.healthcheck.service.HealthCheck;
import com.bnpparibas.certis.api.healthcheck.service.HealthStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Vérifie l'état de santé du service HashiCorp Vault.
 * Ce check effectue un appel à l'endpoint de santé standard de Vault ('/v1/sys/health')
 * pour vérifier la disponibilité de base du service.
 */
@Component
public class VaultHealthCheck implements HealthCheck {

    private final RestTemplate restTemplate;
    private final String vaultHealthUrl;

    /**
     * Constructeur pour l'injection de dépendances.
     * @param vaultUrl L'URL de base de l'instance Vault, injectée depuis les propriétés de configuration.
     */
    public VaultHealthCheck(@Value("${vault.url}") String vaultUrl) {
        // Pour ce check, nous utilisons un RestTemplate simple car l'endpoint /sys/health
        // est généralement accessible sans l'authentification mTLS complexe.
        // Si ce n'est pas le cas, il faudrait injecter et utiliser la VaultTemplateFactory.
        this.restTemplate = new RestTemplate();
        // On construit l'URL complète de l'endpoint de santé.
        this.vaultHealthUrl = vaultUrl + "/v1/sys/health";
    }

    @Override
    public String getName() {
        // Le ticket de Jira liste "hvault"
        return "hvault";
    }

    @Override
    public HealthStatus check() {
        try {
            // On appelle l'endpoint de santé de Vault.
            // Il retourne un statut HTTP 200 si tout va bien, 429 si scellé mais OK, 50x en cas de problème.
            // L'appel lui-même peut lever une exception en cas de problème réseau.
            restTemplate.getForEntity(vaultHealthUrl, String.class);
            
            // Si aucune exception n'est levée, on considère que Vault est accessible.
            return HealthStatus.ok("Vault instance is reachable and responding.");

        } catch (Exception e) {
            // Toute exception (timeout, 404, 503, etc.) indique un problème.
            return HealthStatus.ko("Failed to connect to Vault instance: " + e.getMessage());
        }
    }
}
/////////////////////////////////////
La Solution : Le Code pour LdapRefsgHealthCheck.java
Voici le code final et correct pour votre LdapRefsgHealthCheck.java. Il utilise LdapTemplate comme il se doit.
Fichier : LdapRefsgHealthCheck.java (Version Corrigée)
code
Java
package com.bnpparibas.certis.automationhub.healthcheck.service.check; // Adaptez le package

import com.bnpparibas.certis.api.healthcheck.service.HealthCheck;
import com.bnpparibas.certis.api.healthcheck.service.HealthStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Component;

import javax.naming.directory.SearchControls;

/**
 * Vérifie l'état de santé de la connexion à l'annuaire LDAP "refsg".
 * Cette implémentation réutilise le 'LdapTemplate' configuré dans l'application
 * pour effectuer une recherche simple afin de valider la connectivité et l'authentification.
 */
@Component
public class LdapRefsgHealthCheck implements HealthCheck {

    private final LdapTemplate ldapTemplate;

    /**
     * Constructeur pour l'injection de dépendances.
     * @param ldapTemplate Le template configuré pour communiquer avec l'annuaire LDAP.
     *                     L'annotation @Qualifier peut être nécessaire s'il y a plusieurs LdapTemplate Beans.
     */
    public LdapRefsgHealthCheck(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    @Override
    public String getName() {
        return "ldap refsg"; // Nom demandé dans le ticket
    }

    @Override
    public HealthStatus check() {
        try {
            // STRATÉGIE : On effectue une recherche très simple et légère.
            // On cherche n'importe quel objet ("objectclass=*") à la racine de l'annuaire.
            // On demande de ne retourner aucun attribut et de limiter la recherche à 1 résultat
            // pour que la requête soit la plus rapide possible.
            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.OBJECT_SCOPE); // Ne cherche que le "base object"
            searchControls.setCountLimit(1);
            searchControls.setReturningAttributes(new String[0]); // Ne retourne aucun attribut

            // L'exécution de cette recherche va tenter de se connecter et de s'authentifier.
            // La base DN est vide "" car elle est déjà configurée dans le LdapContextSource.
            ldapTemplate.search("", "(objectclass=*)", searchControls, (javax.naming.directory.SearchResult sr) -> {});

            // Si aucune exception n'est levée, la connexion a réussi.
            return HealthStatus.ok("Successfully connected and authenticated to LDAP server.");

        } catch (Exception e) {
            // Toute exception (ex: ServiceUnavailableException, AuthenticationException) indique un échec.
            return HealthStatus.ko("Failed to connect to LDAP server: " + e.getMessage());
        }
    }
}
///////////////////////////////////////////////////////////////////////////////////
Fichier : VaultHealthCheck.java (Version Corrigée et Sécurisée)
code
Java
package com.bnpparibas.certis.automationhub.healthcheck.service.check;

import com.bnpparibas.certis.api.healthcheck.service.HealthCheck;
import com.bnpparibas.certis.api.healthcheck.service.HealthStatus;
import org.apache.http.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

@Component
public class VaultHealthCheck implements HealthCheck {

    private final RestTemplate restTemplate;
    private final String vaultHealthUrl;

    public VaultHealthCheck(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${vault.url}") String vaultUrl,
            @Value("${vault.truststore.path}") String truststorePath,
            @Value("${vault.truststore.passphrase}") String truststorePassphrase) {
        
        this.vaultHealthUrl = vaultUrl + "/v1/sys/health";
        this.restTemplate = createSecureRestTemplate(restTemplateBuilder, truststorePath, truststorePassphrase);
    }

    @Override
    public String getName() {
        return "hvault";
    }

    @Override
    public HealthStatus check() {
        try {
            restTemplate.getForEntity(vaultHealthUrl, String.class);
            return HealthStatus.ok("Vault instance is reachable and responding.");
        } catch (Exception e) {
            return HealthStatus.ko("Failed to connect to Vault instance: " + e.getMessage());
        }
    }

    /**
     * Méthode utilitaire pour construire un RestTemplate qui fait confiance à un truststore JKS spécifique.
     */
    private RestTemplate createSecureRestTemplate(RestTemplateBuilder builder, String truststorePath, String truststorePassphrase) {
        try {
            KeyStore truststore = KeyStore.getInstance("JKS");
            try (InputStream in = new FileInputStream(truststorePath)) {
                truststore.load(in, truststorePassphrase.toCharArray());
            }

            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(truststore, null) // Le deuxième argument est une TrustStrategy, null utilise la stratégie par défaut.
                    .build();

            ClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(
                    org.apache.http.impl.client.HttpClients.custom()
                            .setSSLSocketFactory(new org.apache.http.conn.ssl.SSLConnectionSocketFactory(sslContext))
                            .build()
            );

            return builder.requestFactory(() -> requestFactory).build();
            
        } catch (Exception e) {
            // Si la configuration TLS échoue, on lève une exception pour faire échouer le démarrage
            // car le health check sera inutilisable.
            throw new IllegalStateException("Failed to create secure RestTemplate for Vault health check", e);
        }
    }
}
//////////////////////
package com.bnpparibas.certis.vault.config;

import com.bnpparibas.certis.vault.VaultService;
import com.bnpparibas.certis.vault.config.VaultLoggingInterceptor;
import com.bnpparibas.certis.vault.config.VaultTemplateFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Classe d'auto-configuration pour le module Vault.
 * Elle déclare les beans essentiels de ce module (VaultService, Factory, etc.)
 * pour les rendre disponibles à d'autres modules qui dépendent du module 'vault'.
 * Cette configuration est activée via le fichier META-INF/spring.factories.
 */
@Configuration
public class VaultAutoConfiguration {

    /**
     * Crée le bean pour l'intercepteur de logs.
     * Cet intercepteur est un @Component, donc Spring pourrait déjà le trouver,
     * mais le déclarer ici rend la configuration plus explicite et robuste.
     * @return Une instance de VaultLoggingInterceptor.
     */
    @Bean
    @ConditionalOnMissingBean // Ne crée ce bean que s'il n'existe pas déjà
    public VaultLoggingInterceptor vaultLoggingInterceptor() {
        return new VaultLoggingInterceptor();
    }

    /**
     * Crée le bean pour la factory de VaultTemplate.
     * C'est la classe responsable de la création des clients RestTemplate sécurisés
     * pour communiquer avec Vault.
     * @param vaultLoggingInterceptor Le bean d'intercepteur, injecté par Spring.
     * @return Une instance de VaultTemplateFactory.
     */
    @Bean
    @ConditionalOnMissingBean
    public VaultTemplateFactory vaultTemplateFactory(VaultLoggingInterceptor vaultLoggingInterceptor) {
        // Le constructeur de VaultTemplateFactory prend un VaultLoggingInterceptor en argument.
        return new VaultTemplateFactory(vaultLoggingInterceptor);
    }

    /**
     * Crée le bean principal du service Vault.
     * C'est le point d'entrée que les autres modules (comme 'referential-refi') utiliseront.
     * @param vaultTemplateFactory Le bean de la factory, injecté par Spring.
     * @return Une instance de VaultService.
     */
    @Bean
    @ConditionalOnMissingBean
    public VaultService vaultService(VaultTemplateFactory vaultTemplateFactory) {
        // Le constructeur de VaultService prend la factory en argument.
        return new VaultService(vaultTemplateFactory);
    }
}
/////////////////////////
package com.bnpparibas.certis.referential.refi.config;

import com.bnpparibas.certis.referential.refi.initialization.DynamicDataSource;
import com.bnpparibas.certis.referential.refi.initialization.OpenDataProperties;
import com.bnpparibas.certis.vault.VaultService; // L'import va maintenant fonctionner
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReferentialAutoConfiguration {

    @Bean
    public OpenDataProperties openDataProperties() {
        return new OpenDataProperties();
    }

    @Bean
    public DynamicDataSource dynamicDataSource(VaultService vaultService, OpenDataProperties openDataProperties) {
        return new DynamicDataSource(vaultService, openDataProperties);
    }
}