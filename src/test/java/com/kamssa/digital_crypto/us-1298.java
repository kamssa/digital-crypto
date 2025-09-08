
@Entity
@Table(name = "SAN_TYPE_RULE")
public class SanTypeRuleEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROFILE_ID", nullable = false)
    private CertificateProfileEntity certificateProfile; // Lien vers le profil parent

    @Enumerated(EnumType.STRING)
    @Column(name = "SAN_TYPE", nullable = false)
    private SanTypeEnum sanType;

    @Column(name = "MIN_VALUE", nullable = false)
    private Integer minValue;

    @Column(name = "MAX_VALUE", nullable = false)
    private Integer maxValue;

    @Column(name = "EDITABLE_BY_REQUESTER")
    private Boolean editableByRequester;

    @Column(name = "EDITABLE_BY_APPROVER")
    private Boolean editableByApprover;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CertificateProfileEntity getCertificateProfile() {
        return certificateProfile;
    }

    public void setCertificateProfile(CertificateProfileEntity certificateProfile) {
        this.certificateProfile = certificateProfile;
    }

    public SanTypeEnum getSanType() {
        return sanType;
    }

    public void setSanType(SanTypeEnum sanType) {
        this.sanType = sanType;
    }

    public Integer getMinValue() {
        return minValue;
    }

    public void setMinValue(Integer minValue) {
        this.minValue = minValue;
    }

    public Integer getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Integer maxValue) {
        this.maxValue = maxValue;
    }

    public Boolean getEditableByRequester() {
        return editableByRequester;
    }

    public void setEditableByRequester(Boolean editableByRequester) {
        this.editableByRequester = editableByRequester;
    }

    public Boolean getEditableByApprover() {
        return editableByApprover;
    }

    public void setEditableByApprover(Boolean editableByApprover) {
        this.editableByApprover = editableByApprover;
    }
}
///////////////////////////
package com.bnpparibas.certis.automationhub.repository;

import com.bnpparibas.certis.automationhub.model.CertificateProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CertificateProfileRepository extends JpaRepository<CertificateProfileEntity, Long> {
    Optional<CertificateProfileEntity> findByProfileName(String profileName);
}
code
Java
package com.bnpparibas.certis.automationhub.repository;

import com.bnpparibas.certis.automationhub.model.SanTypeRuleEntity;
import com.bnpparibas.certis.automationhub.model.CertificateProfileEntity;
import com.bnpparibas.certis.automationhub.dto.SanTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SanTypeRuleRepository extends JpaRepository<SanTypeRuleEntity, Long> {
    Optional<SanTypeRuleEntity> findByCertificateProfileAndSanType(CertificateProfileEntity profile, SanTypeEnum sanType);
}
4. Service pour la Logique Métier (dans votre package service)
C'est ici que vous allez orchestrer l'appel à l'API externe, le traitement des données et le stockage.
CertificateProfileService.java
code
Java
package com.bnpparibas.certis.automationhub.service;

import com.bnpparibas.certis.automationhub.dto.SanTypeEnum;
import com.bnpparibas.certis.automationhub.dto.external.ExternalSanProfileResponse;
import com.bnpparibas.certis.automationhub.dto.external.ExternalSanTypeRuleDto;
import com.bnpparibas.certis.automationhub.model.CertificateProfileEntity;
import com.bnpparibas.certis.automationhub.model.SanTypeRuleEntity;
import com.bnpparibas.certis.automationhub.repository.CertificateProfileRepository;
import com.bnpparibas.certis.automationhub.repository.SanTypeRuleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate; // Ou WebClient si vous préférez

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CertificateProfileService {

    @Value("${external.api.url.profiles}") // Définissez cette propriété dans application.properties/yml
    private String externalApiBaseUrl;

    private final RestTemplate restTemplate;
    private final CertificateProfileRepository certificateProfileRepository;
    private final SanTypeRuleRepository sanTypeRuleRepository;

    public CertificateProfileService(RestTemplate restTemplate,
                                     CertificateProfileRepository certificateProfileRepository,
                                     SanTypeRuleRepository sanTypeRuleRepository) {
        this.restTemplate = restTemplate;
        this.certificateProfileRepository = certificateProfileRepository;
        this.sanTypeRuleRepository = sanTypeRuleRepository;
    }

    /**
     * Récupère les règles de profil SAN pour un nom de profil donné depuis l'API externe,
     * les traite et les stocke en base de données.
     * @param profileName Le nom du profil à récupérer.
     * @return L'entité CertificateProfileEntity sauvegardée.
     */
    @Transactional // S'assure que toute l'opération est atomique
    public CertificateProfileEntity fetchAndSaveSanProfileRules(String profileName) {
        String apiUrl = externalApiBaseUrl + "/" + profileName;

        // 1. Appel à l'API externe
        ExternalSanProfileResponse externalResponse = restTemplate.getForObject(apiUrl, ExternalSanProfileResponse.class);

        if (externalResponse == null || externalResponse.getSans() == null) {
            // Gérer le cas où la réponse est vide ou invalide
            // Log, exception, ou retour d'un profil vide selon la logique métier
            throw new RuntimeException("Empty or invalid response from external SAN profile API for profile: " + profileName);
        }

        // 2. Récupérer ou créer le CertificateProfileEntity
        CertificateProfileEntity profileEntity = certificateProfileRepository.findByProfileName(profileName)
                .orElseGet(() -> {
                    CertificateProfileEntity newProfile = new CertificateProfileEntity();
                    newProfile.setProfileName(profileName);
                    return newProfile;
                });

        Set<SanTypeRuleEntity> updatedRules = new HashSet<>();

        // 3. Traitement des règles et mise à jour/création des SanTypeRuleEntity
        for (ExternalSanTypeRuleDto externalRule : externalResponse.getSans()) {
            SanTypeRuleEntity ruleEntity = sanTypeRuleRepository
                    .findByCertificateProfileAndSanType(profileEntity, externalRule.getType())
                    .orElseGet(SanTypeRuleEntity::new);

            ruleEntity.setCertificateProfile(profileEntity);
            ruleEntity.setSanType(externalRule.getType());

            // Appliquer les règles de min/max comme spécifié dans Jira
            Integer minVal = externalRule.getMin() != null ? externalRule.getMin() : 0;
            Integer maxVal = externalRule.getMax() != null ? externalRule.getMax() : 250;

            if (Boolean.FALSE.equals(externalRule.getEditableByRequester())) {
                maxVal = 0; // Si non éditable par le demandeur, le max est 0
            }

            ruleEntity.setMinValue(minVal);
            ruleEntity.setMaxValue(maxVal);
            ruleEntity.setEditableByRequester(externalRule.getEditableByRequester() != null ? externalRule.getEditableByRequester() : false);
            ruleEntity.setEditableByApprover(externalRule.getEditableByApprover() != null ? externalRule.getEditableByApprover() : false);

            updatedRules.add(ruleEntity);
        }

        // Gérer les types de SAN qui existent dans la base mais ne sont plus retournés par l'API
        // et qui devraient être réglés à min=max=0
        Set<SanTypeEnum> returnedSanTypes = externalResponse.getSans().stream()
                .map(ExternalSanTypeRuleDto::getType)
                .collect(Collectors.toSet());

        profileEntity.getSanTypeRules().stream()
                .filter(existingRule -> !returnedSanTypes.contains(existingRule.getSanType()))
                .forEach(ruleToUpdate -> {
                    ruleToUpdate.setMinValue(0);
                    ruleToUpdate.setMaxValue(0);
                    ruleToUpdate.setEditableByRequester(false);
                    ruleToUpdate.setEditableByApprover(false);
                    // Ajoutez ces règles mises à jour à updatedRules pour s'assurer qu'elles sont sauvegardées
                    updatedRules.add(ruleToUpdate);
                });


        // Important: Mettre à jour la collection dans l'entité parente
        // Si vous utilisez un Set comme dans l'exemple, cela gérera les ajouts/mises à jour.
        // Pour les suppressions, l'option `orphanRemoval=true` sur `@OneToMany` est utile,
        // mais nécessite une gestion explicite des éléments à supprimer.
        // Ici, nous allons simplement remplacer l'ensemble des règles.
        profileEntity.getSanTypeRules().clear(); // Efface les anciennes
        profileEntity.getSanTypeRules().addAll(updatedRules); // Ajoute les nouvelles/mises à jour

        // 4. Sauvegarde en base de données
        return certificateProfileRepository.save(profileEntity);
    }

    // Vous pourriez ajouter d'autres méthodes ici, comme :
    // - getSanProfileRules(String profileName) pour lire les règles depuis la BDD
    // - getAllCertificateProfiles()
}
Configuration de RestTemplate (dans votre classe de configuration Spring Boot, ex: Application.java ou une @Configuration séparée)
code
Java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
Exemple d'utilisation dans un contrôleur (pour déclencher la tâche manuellement ou via une API)
code
Java
package com.bnpparibas.certis.automationhub.controller;

import com.bnpparibas.certis.automationhub.model.CertificateProfileEntity;
import com.bnpparibas.certis.automationhub.service.CertificateProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/certificate-profiles")
public class CertificateProfileController {

    private final CertificateProfileService certificateProfileService;

    public CertificateProfileController(CertificateProfileService certificateProfileService) {
        this.certificateProfileService = certificateProfileService;
    }

    @PostMapping("/fetch-and-save/{profileName}")
    public ResponseEntity<CertificateProfileEntity> fetchAndSaveProfileRules(@PathVariable String profileName) {
        CertificateProfileEntity savedProfile = certificateProfileService.fetchAndSaveSanProfileRules(profileName);
        return ResponseEntity.ok(savedProfile);
    }

    // Vous pourriez aussi ajouter un GET pour récupérer les règles déjà stockées
    // @GetMapping("/{profileName}")
    // public ResponseEntity<CertificateProfileEntity> getProfileRules(@PathVariable String profileName) {
    //     return certificateProfileService.getSanProfileRules(profileName)
    //             .map(ResponseEntity::ok)
    //             .orElse(ResponseEntity.notFound().build());
    // }
}
/////////////////////////////
Ajout dans application.properties (ou application.yml)
code
Properties
# Configuration de l'API externe des profils de SAN
external.api.url.profiles=https://automation.staging.echonet.net.intra/api/v1/certificate/profiles
/////////////////////////////////

Model
ThinkingThoughts
(experimental)
Auto
Expand to view model thoughts

chevron_right
Model
C'est une excellente question, et elle met le doigt sur un point clé. Vous avez tout à fait raison de vous interroger.
Non, le fait qu'il n'y ait pas de relation directe ne pose absolument aucun problème. Au contraire, c'est la conception correcte et la plus flexible.
Laissez-moi vous expliquer pourquoi, en décomposant votre affirmation :
"j'enregistre les san dan la base donnée en fonction des regle min et max et du profil."
Cette phrase est la clé. Il faut la diviser en deux moments distincts :
Le moment de la VALIDATION (AVANT l'enregistrement)
Le moment de l'ENREGISTREMENT (APRÈS la validation)
1. Le Moment de la VALIDATION
C'est là que les règles (SanTypeRuleEntity) sont utilisées.
Quoi ? Votre service (SanServiceImpl) reçoit une requête pour créer un certificat avec une liste de San.
Action ? Le service charge les SanTypeRuleEntity qui correspondent au profil demandé. Il utilise ces règles uniquement pour vérifier si la liste de San est valide (respecte min et max).
Rôle des règles ? Les règles agissent comme un gardien. Elles disent "oui, cette liste de SANs est acceptable pour ce profil" ou "non, elle ne l'est pas".
À la fin de cette étape, les règles (SanTypeRuleEntity) ont terminé leur travail pour cette requête. On n'en a plus besoin.
2. Le Moment de l'ENREGISTREMENT
Si la validation a réussi, on passe à l'enregistrement.
Quoi ? Vous avez une liste de San qui a été jugée valide.
Action ? Vous créez une nouvelle entité Certificate en base de données. Puis, vous parcourez votre liste de San validés et vous les enregistrez dans la table SAN, en liant chacun d'eux à l'ID du nouveau Certificate.
Rôle des SANs enregistrés ? Les San enregistrés représentent l'état final et factuel du certificat. Ils ne se soucient plus des règles qui ont permis leur création. Ils existent simplement en tant que données attachées au certificat.
Pourquoi une relation directe serait une mauvaise idée ?
Imaginez que l'on force une relation (une clé étrangère) entre la table SAN et la table SAN_TYPE_RULE. Cela créerait plusieurs problèmes majeurs :
Manque de Flexibilité : Que se passe-t-il si vous changez une règle ? Par exemple, le max pour les CN passe de 1 à 2. Si vous aviez un lien direct, tous les anciens SANs enregistrés pointeraient vers une règle qui n'est plus la bonne. Cela rendrait l'historique de vos données incohérent.
Complexité Inutile : Quand vous lisez un certificat existant depuis la base, vous voulez juste savoir quels SANs il possède ("www.site.com", "api.site.com"). Vous n'avez pas besoin de savoir quelle règle exacte a permis leur création il y a 6 mois. Lier les deux alourdirait vos requêtes et votre modèle objet pour rien.
Le Profil est la Clé : La vraie information contextuelle n'est pas la règle individuelle, mais le profil du certificat. En sachant qu'un certificat a été créé avec le profil "SSL_SRVR", vous pouvez toujours, si besoin, retrouver les règles qui étaient en vigueur à un instant T (même si ce n'est généralement pas nécessaire).
Conclusion
Votre processus est le bon :
Vous utilisez les SanTypeRuleEntity comme un gabarit de validation temporaire au moment de la création/modification.
Une fois la validation passée, vous enregistrez les San en tant que données concrètes et indépendantes, liées uniquement à leur certificat parent.
Cette séparation entre les règles de validation (SanTypeRuleEntity) et les données résultantes (San) est un principe fondamental d'une bonne conception logicielle. Elle garantit que votre système est à la fois robuste, flexible et facile à maintenir. Donc non, l'absence de relation ne pose aucun problème, c'est au contraire la solution recherchée.
////////////////////////////////
Absolument ! Voici le code révisé pour votre classe SanServiceImpl.java.
J'ai structuré le code pour que vous puissiez voir clairement ce qui est nouveau, ce qui est modifié, et ce que vous devez supprimer.
Vous pouvez remplacer le contenu de votre fichier SanServiceImpl.java par celui-ci.
Code Repris de SanServiceImpl.java
code
Java
package com.bnpparibas.certis.certificate.request.service.impl;

// --- Imports existants ---
import com.bnpparibas.certis.certificate.request.dto.RequestDto;
import com.bnpparibas.certis.certificate.request.dto.San;
import com.bnpparibas.certis.certificate.request.dto.SanTypeEnum; // Assurez-vous que l'import est correct
import com.bnpparibas.certis.certificate.request.service.SanService;
import com.bnpparibas.certis.exception.CertisRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.http.HttpStatus;

// --- NOUVEAUX Imports ---
import com.bnpparibas.certis.automationhub.model.CertificateProfileEntity;
import com.bnpparibas.certis.automationhub.model.SanTypeRuleEntity;
import com.bnpparibas.certis.automationhub.repository.CertificateProfileRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SanServiceImpl implements SanService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SanServiceImpl.class);

    // --- Conservez vos dépendances existantes ---
    private final UrlRacineService urlRacineService;
    // ... autres services/daos ...

    // --- NOUVELLE Dépendance ---
    private final CertificateProfileRepository certificateProfileRepository;

    // --- MODIFIER le constructeur pour injecter le nouveau repository ---
    public SanServiceImpl(UrlRacineService urlRacineService, 
                          /* ... autres dépendances existantes ... */
                          CertificateProfileRepository certificateProfileRepository) {
        this.urlRacineService = urlRacineService;
        // ...
        this.certificateProfileRepository = certificateProfileRepository;
    }


    // =======================================================================================
    // --- SECTION A SUPPRIMER ---
    // Supprimez toutes les anciennes constantes de limites codées en dur.
    // Par exemple :
    // private final Integer EXT_SSL_LIMIT = 250;
    // private final Integer INT_SSL_SRVR_LIMIT = 249;
    // private final Integer INT_SSL_CLI_SRVR_LIMIT = 56;
    // private final Integer INT_SSL_CLI_LIMIT = 8;
    //
    // Supprimez également les anciennes méthodes de validation des limites comme :
    // - verifySansLimitForInternalCertificates(...)
    // - verifySansLimitForExternalCertificates(...)
    // - verifySansLimitForInternalCertificatesMarketplace(...)
    // =======================================================================================


    /**
     * Méthode publique principale pour la validation des SANs.
     * C'est cette méthode qui est appelée depuis l'extérieur (ex: RequestServiceImpl).
     * Elle est maintenant simplifiée pour appeler la nouvelle logique dynamique.
     */
    @Override
    public void validateSansPerRequest(RequestDto requestDto) throws CertisRequestException {
        if (this.skipValidationIfDataMissing(requestDto)) {
            return;
        }

        // --- DÉBUT DE LA MODIFICATION ---

        // NOUVEL APPEL UNIQUE à la validation dynamique des limites.
        this.verifySansLimitsDynamically(requestDto);

        // --- FIN DE LA MODIFICATION ---

        // On conserve les autres validations qui ne sont pas liées aux limites.
        this.verifySanFormats(requestDto);
        
        // ... conservez ici les appels à d'autres validations si nécessaire ...
    }


    /**
     * NOUVELLE méthode privée qui contient toute la logique de validation dynamique.
     * Elle remplace toutes les anciennes méthodes de validation des limites.
     * @param requestDto La requête à valider.
     * @throws CertisRequestException si une règle de min/max est violée.
     */
    private void verifySansLimitsDynamically(RequestDto requestDto) throws CertisRequestException {
        if (requestDto.getCertificate() == null || requestDto.getCertificate().getType() == null) {
            // Pas de type de certificat (profil), on ne peut pas valider.
            return;
        }

        // Le nom du type de certificat est utilisé comme nom de profil. Ex: "SSL_SRVR"
        String profileName = requestDto.getCertificate().getType().getName();
        List<San> sansInRequest = requestDto.getCertificate().getSans();

        if (CollectionUtils.isEmpty(sansInRequest)) {
            // S'il n'y a aucun SAN dans la requête, il faut quand même vérifier les règles "min".
        }

        // Étape 1 : Récupérer les règles pour ce profil depuis VOTRE base de données.
        Optional<CertificateProfileEntity> profileOpt = certificateProfileRepository.findByProfileName(profileName);
        if (!profileOpt.isPresent()) {
            // C'est une erreur de configuration. Le profil devrait exister après synchronisation.
            LOGGER.error("Configuration de profil de certificat introuvable pour le nom : {}", profileName);
            // Vous pouvez choisir de laisser passer ou de bloquer. Bloquer est plus sûr.
            throw new CertisRequestException("error.profile.config.not_found", new Object[]{profileName}, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Set<SanTypeRuleEntity> rules = profileOpt.get().getSanTypeRules();
        if (CollectionUtils.isEmpty(rules)) {
            LOGGER.warn("Aucune règle de SAN n'est définie pour le profil : {}. Tous les SANs seront refusés.", profileName);
        }

        // Étape 2 : Compter le nombre de SANs par type dans la requête de l'utilisateur.
        Map<SanTypeEnum, Long> sanCountsByType = sansInRequest.stream()
                .filter(san -> san.getSanType() != null) // Ignorer les SANs sans type pour la validation
                .collect(Collectors.groupingBy(San::getSanType, Collectors.counting()));

        // Étape 3 : Valider si les SANs de la requête sont autorisés par le profil.
        for (SanTypeEnum requestedSanType : sanCountsByType.keySet()) {
            boolean isTypeAllowedInProfile = rules.stream()
                    .anyMatch(rule -> rule.getSanType().equals(requestedSanType));

            if (!isTypeAllowedInProfile) {
                LOGGER.error("Validation échouée : le type de SAN '{}' n'est pas autorisé pour le profil '{}'", requestedSanType, profileName);
                throw new CertisRequestException("error.san.type.unauthorized", new Object[]{requestedSanType.name(), profileName}, HttpStatus.BAD_REQUEST);
            }
        }

        // Étape 4 : Valider les limites MIN et MAX pour chaque règle définie dans le profil.
        for (SanTypeRuleEntity rule : rules) {
            SanTypeEnum sanType = rule.getSanType();
            long countInRequest = sanCountsByType.getOrDefault(sanType, 0L);

            // Validation de la limite MAXIMALE
            if (countInRequest > rule.getMaxValue()) {
                LOGGER.error("Validation échouée : {} SANs de type '{}' soumis, mais le maximum est de {} pour le profil '{}'",
                        countInRequest, sanType, rule.getMaxValue(), profileName);
                throw new CertisRequestException("error.san.max.violation", new Object[]{sanType.name(), rule.getMaxValue()}, HttpStatus.BAD_REQUEST);
            }

            // Validation de la limite MINIMALE
            if (countInRequest < rule.getMinValue()) {
                 LOGGER.error("Validation échouée : {} SANs de type '{}' soumis, mais le minimum requis est de {} pour le profil '{}'",
                        countInRequest, sanType, rule.getMinValue(), profileName);
                throw new CertisRequestException("error.san.min.violation", new Object[]{sanType.name(), rule.getMinValue()}, HttpStatus.BAD_REQUEST);
            }
        }
    }


    // --- Conservez toutes les autres méthodes de SanServiceImpl ---
    // Par exemple :
    // - verifySanFormats(...)
    // - evaluateSan3W(...)
    // - buildSANs(...)
    // - etc.
    // Elles ne sont pas affectées par ce changement.
    
    private void verifySanFormats(RequestDto requestDto) {
        // ... votre code existant ici ...
    }

    private boolean skipValidationIfDataMissing(RequestDto requestDto) {
        // ... votre code existant ici ...
        return false;
    }
    
    // ... et toutes les autres méthodes de la classe ...

}
Explication des Modifications
Nouvelle Dépendance : On injecte CertificateProfileRepository pour pouvoir lire les règles que vous avez synchronisées dans votre base de données.
Suppression de l'Ancien Code : Il est crucial de supprimer les anciennes constantes et les anciennes méthodes de validation pour éviter toute confusion et ne plus utiliser la logique obsolète.
Simplification de validateSansPerRequest : La méthode principale devient très simple. Son unique rôle concernant les limites est d'appeler la nouvelle méthode verifySansLimitsDynamically.
Nouvelle Méthode verifySansLimitsDynamically : C'est le cœur de la nouvelle fonctionnalité.
Elle récupère le nom du profil depuis la requête (ex: "SSL_SRVR").
Elle utilise le repository pour charger les règles (min/max pour chaque type de SAN) associées à ce profil.
Elle compte le nombre de SANs pour chaque type (CN, DNSNAME, etc.) dans la requête de l'utilisateur.
Elle compare ces comptes aux règles chargées depuis la base de données.
Elle lance une CertisRequestException (comme vous le faisiez déjà) si une règle n'est pas respectée.
Prochaines Étapes pour Vous
Copier-coller ce code dans votre fichier SanServiceImpl.java.
Supprimer les anciennes constantes et méthodes comme indiqué dans les commentaires.
Créer les clés d'erreur dans vos fichiers de messages (ex: messages.properties) :
error.profile.config.not_found=Configuration de profil introuvable pour le profil : {0}
error.san.type.unauthorized=Le type de SAN {0} n'est pas autorisé pour le profil {1}.
error.san.max.violation=Le nombre de SANs de type {0} dépasse la limite autorisée de {1}.
error.san.min.violation=Le nombre de SANs de type {0} est inférieur au minimum requis de {1}.
Assurez-vous que toutes les autres classes (CertificateProfileEntity, SanTypeRuleEntity, CertificateProfileRepository, CertificateProfileService) sont bien créées comme nous l'avons discuté.
///////////////////////////////////////////
C'est une excellente approche pour garder le code cohérent.
En analysant votre image, je vois que vous utilisez un "pattern" très clair pour vos tâches :
Elles sont dans le package ...api.tasks.
Le nom de la classe se termine par Task.
Elles utilisent @Scheduled pour la planification.
Elles utilisent @SchedulerLock pour s'assurer que la tâche ne s'exécute pas sur plusieurs serveurs en même temps (très bonne pratique !).
La méthode planifiée est souvent une simple "enveloppe" qui appelle la vraie logique métier.
Nous allons donc transformer la classe ProfileSyncScheduler pour qu'elle corresponde exactement à ce modèle.
Voici la nouvelle classe, renommée SyncSanProfilesTask.java, à placer dans votre package ...api.tasks.
Nouvelle Classe : SyncSanProfilesTask.java
code
Java
package com.bnpparibas.certis.api.tasks; // Doit être dans le même package que vos autres tâches

import com.bnpparibas.certis.automationhub.service.CertificateProfileService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock; // Assurez-vous que cet import est correct pour votre projet
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tâche planifiée pour la synchronisation des règles de profils de SANs.
 * Cette classe suit le modèle des autres tâches de l'application.
 * Elle est responsable de déclencher périodiquement la mise à jour des règles
 * de profils en base de données depuis l'API externe.
 */
@Component
public class SyncSanProfilesTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncSanProfilesTask.class);

    private final CertificateProfileService profileService;

    // Récupère la liste des profils depuis votre fichier de configuration (recommandé)
    @Value("${certis.profiles.to-sync:}") // Le ':' évite une erreur si la propriété n'existe pas
    private List<String> profilesToSync = new ArrayList<>();

    public SyncSanProfilesTask(CertificateProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Méthode "wrapper" déclenchée par le scheduler.
     * Elle est protégée par un verrou pour éviter les exécutions concurrentes en environnement clusterisé.
     * Elle ne fait qu'appeler la méthode contenant la logique métier.
     */
    @Scheduled(cron = "${certis.profiles.sync-cron:0 0 2 * * ?}") // Utilise une propriété, avec une valeur par défaut
    @SchedulerLock(name = "SyncSanProfilesTask_lock")
    public void runTask() {
        this.syncAllSanProfileRules();
    }

    /**
     * Méthode privée contenant la logique métier de la synchronisation.
     */
    private void syncAllSanProfileRules() {
        LOGGER.info("Début de la tâche de synchronisation des profils de SANs.");

        if (profilesToSync.isEmpty()) {
            LOGGER.warn("Aucun profil n'est configuré pour la synchronisation (propriété 'certis.profiles.to-sync' vide). La tâche se termine.");
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        for (String profileName : profilesToSync) {
            try {
                LOGGER.info("Synchronisation du profil '{}'...", profileName);
                profileService.fetchAndSaveSanProfileRules(profileName);
                LOGGER.info("Le profil '{}' a été synchronisé avec succès.", profileName);
                successCount++;
            } catch (Exception e) {
                LOGGER.error("Échec de la synchronisation pour le profil '{}'. Cause : {}", profileName, e.getMessage(), e);
                failureCount++;
            }
        }

        LOGGER.info(
            "Fin de la tâche de synchronisation. {} profils synchronisés avec succès, {} en échec.",
            successCount,
            failureCount
        );
    }
}
Configuration à ajouter dans application.properties (ou .yml)
Pour rendre cette classe encore plus flexible, j'ai utilisé des propriétés. Ajoutez ceci à votre fichier de configuration :
code
Properties
# Planification CRON pour la synchronisation des profils de SANs. Par défaut : tous les jours à 2h.
certis.profiles.sync-cron=0 0 2 * * ?

# Liste des profils à synchroniser (séparés par une virgule)
certis.profiles.to-sync=SSL_SRVR,SSL_CLI_SRVR,SSL_CLI,Appli-SSL-Client-Server
Pourquoi cette version est meilleure et correspond à votre modèle :
Nom et Emplacement : Le nom SyncSanProfilesTask et son emplacement dans le package ...api.tasks sont cohérents avec votre projet.
@SchedulerLock : J'ai ajouté l'annotation @SchedulerLock(name = "SyncSanProfilesTask_lock"). C'est essentiel si votre application tourne sur plusieurs serveurs (cluster) pour garantir qu'un seul serveur exécute la tâche à un instant T.
Structure Wrapper : La méthode publique runTask() est très simple. C'est elle qui porte les annotations @Scheduled et @SchedulerLock. La logique complexe est isolée dans la méthode privée syncAllSanProfileRules(), ce qui rend le code plus lisible et plus facile à tester.
Configuration Externalisée : La planification (cron) et la liste des profils sont maintenant dans le fichier application.properties. C'est beaucoup plus propre et vous permet de modifier ces paramètres sans recompiler le code.
Cette classe SyncSanProfilesTask s'intègre maintenant parfaitement dans l'architecture de votre application.
////////////////////////////////
Étape 1 : Créer l'Enum CertificateProfileType.java
Créez ce nouveau fichier, par exemple dans un package ...dto ou ...model.enums.
code
Java
package com.bnpparibas.certis.model.enums; // Ou un autre package approprié

import java.util.stream.Stream;

/**
 * Énumération des types de profils de certificat connus par l'application.
 * Fournit une source unique et sécurisée pour les noms de profils.
 */
public enum CertificateProfileType {

    // Liste des constantes de l'énumération.
    // La valeur entre parenthèses doit correspondre EXACTEMENT
    // à la chaîne de caractères utilisée dans l'API externe et le fichier de configuration.
    SSL_SERVER("SSL_SRVR"),
    SSL_CLIENT_SERVER("SSL_CLI_SRVR"),
    SSL_CLIENT("SSL_CLI"),
    APPLI_SSL_CLIENT_SERVER("Appli-SSL-Client-Server");

    // Champ pour stocker la valeur textuelle du profil
    private final String value;

    // Constructeur privé
    CertificateProfileType(String value) {
        this.value = value;
    }

    /**
     * Retourne la valeur textuelle du profil.
     * @return Le nom du profil tel qu'attendu par les systèmes externes.
     */
    public String getValue() {
        return value;
    }

    /**
     * Méthode utilitaire pour retrouver une constante de l'énumération à partir de sa valeur textuelle.
     * C'est très utile pour convertir la configuration en Enum.
     * @param value La valeur textuelle à rechercher.
     * @return La constante Enum correspondante.
     * @throws IllegalArgumentException si aucune constante ne correspond.
     */
    public static CertificateProfileType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("La valeur du profil ne peut pas être nulle.");
        }
        return Stream.of(CertificateProfileType.values())
              .filter(profile -> profile.getValue().equalsIgnoreCase(value))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Aucun type de profil de certificat ne correspond à la valeur : " + value));
    }
}
Étape 2 : Modifier la Tâche SyncSanProfilesTask.java
Maintenant, nous allons modifier le scheduler pour qu'il utilise cet Enum au lieu d'une simple List<String>.
code
Java
package com.bnpparibas.certis.api.tasks;

// ... autres imports
import com.bnpparibas.certis.model.enums.CertificateProfileType; // <-- NOUVEL IMPORT
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet; // Utiliser EnumSet est très efficace
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SyncSanProfilesTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncSanProfilesTask.class);
    private final CertificateProfileService profileService;

    // --- DÉBUT DES MODIFICATIONS ---

    // Le champ qui contiendra les profils à synchroniser, maintenant de type Enum
    private Set<CertificateProfileType> profilesToSync = EnumSet.noneOf(CertificateProfileType.class);

    /**
     * C'est ici que la magie opère.
     * On utilise une méthode "setter" avec @Value. Spring va appeler cette méthode
     * avec la liste de chaînes de caractères lue depuis le fichier application.yml.
     * La méthode se charge de convertir cette liste de String en un Set d'Enums.
     */
    @Value("${certis.profiles.to-sync}")
    public void setProfilesToSync(List<String> profileNames) {
        if (profileNames != null && !profileNames.isEmpty()) {
            this.profilesToSync = profileNames.stream()
                .map(CertificateProfileType::fromValue) // Conversion de chaque String en Enum
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CertificateProfileType.class)));
        }
    }

    public SyncSanProfilesTask(CertificateProfileService profileService) {
        this.profileService = profileService;
    }

    @Scheduled(cron = "${certis.profiles.sync-cron:0 0 2 * * ?}")
    @SchedulerLock(name = "SyncSanProfilesTask_lock")
    public void runTask() {
        this.syncAllSanProfileRules();
    }

    private void syncAllSanProfileRules() {
        LOGGER.info("Début de la tâche de synchronisation des profils de SANs.");

        if (profilesToSync.isEmpty()) {
            LOGGER.warn("Aucun profil n'est configuré pour la synchronisation (propriété 'certis.profiles.to-sync' vide). La tâche se termine.");
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        // La boucle itère maintenant sur notre Set d'Enums (plus sûr !)
        for (CertificateProfileType profileType : profilesToSync) {
            String profileName = profileType.getValue(); // On récupère la valeur String pour appeler le service
            try {
                LOGGER.info("Synchronisation du profil '{}'...", profileName);
                profileService.fetchAndSaveSanProfileRules(profileName);
                LOGGER.info("Le profil '{}' a été synchronisé avec succès.", profileName);
                successCount++;
            } catch (Exception e) {
                LOGGER.error("Échec de la synchronisation pour le profil '{}'. Cause : {}", profileName, e.getMessage(), e);
                failureCount++;
            }
        }
        // ... reste de la méthode inchangée ...
    }

     // --- FIN DES MODIFICATIONS ---
}
//////////////////// resumer ///////////////
Absolument. Voici un résumé complet de tous les fichiers et modifications de code nécessaires pour résoudre votre ticket Jira.
C'est un guide "tout-en-un" que vous pouvez suivre pour l'implémentation.
Résumé du Flux de Travail
Une Tâche Planifiée (SyncSanProfilesTask) s'exécutera chaque jour.
Elle appellera un Nouveau Service (CertificateProfileService).
Ce service contactera l'API externe, récupérera les règles min/max pour chaque profil et les stockera dans de Nouvelles Tables en base de données (CERTIFICATE_PROFILE et SAN_TYPE_RULE).
Le Service Existant (SanServiceImpl) sera modifié pour lire les règles depuis ces nouvelles tables au lieu d'utiliser des constantes codées en dur.
FICHIER 1 : L'Énumération CertificateProfileType.java (Nouvelle Classe)
Rôle : Fournit une source unique et sûre pour les noms de profils.
Emplacement : src/main/java/com/bnpparibas/certis/model/enums/ (ou un autre package enums)
code
Java
package com.bnpparibas.certis.model.enums;

import java.util.stream.Stream;

public enum CertificateProfileType {

    SSL_SERVER("SSL_SRVR"),
    SSL_CLIENT_SERVER("SSL_CLI_SRVR"),
    SSL_CLIENT("SSL_CLI"),
    APPLI_SSL_CLIENT_SERVER("Appli-SSL-Client-Server");

    private final String value;

    CertificateProfileType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CertificateProfileType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("La valeur du profil ne peut pas être nulle.");
        }
        return Stream.of(CertificateProfileType.values())
              .filter(profile -> profile.getValue().equalsIgnoreCase(value))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Aucun type de profil ne correspond à la valeur : " + value));
    }
}
FICHIER 2 : Entité CertificateProfileEntity.java (Nouvelle Classe)
Rôle : Représente un profil de certificat dans la table CERTIFICATE_PROFILE.
Emplacement : src/main/java/com/bnpparibas/certis/automationhub/model/
code
Java
package com.bnpparibas.certis.automationhub.model;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "CERTIFICATE_PROFILE")
public class CertificateProfileEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PROFILE_NAME", unique = true, nullable = false)
    private String profileName;

    @OneToMany(mappedBy = "certificateProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<SanTypeRuleEntity> sanTypeRules = new HashSet<>();

    // Getters, Setters, constructeur par défaut, equals() et hashCode()
}
FICHIER 3 : Entité SanTypeRuleEntity.java (Nouvelle Classe)
Rôle : Représente une règle min/max pour un type de SAN dans la table SAN_TYPE_RULE.
Emplacement : src/main/java/com/bnpparibas/certis/automationhub/model/
code
Java
package com.bnpparibas.certis.automationhub.model;

import com.bnpparibas.certis.certificate.request.dto.SanTypeEnum; // Réutiliser votre Enum existant
import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "SAN_TYPE_RULE")
public class SanTypeRuleEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROFILE_ID", nullable = false)
    private CertificateProfileEntity certificateProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "SAN_TYPE", nullable = false)
    private SanTypeEnum sanType;

    @Column(name = "MIN_VALUE", nullable = false)
    private Integer minValue;

    @Column(name = "MAX_VALUE", nullable = false)
    private Integer maxValue;

    @Column(name = "EDITABLE_BY_REQUESTER")
    private Boolean editableByRequester;
    
    // Getters, Setters, constructeur par défaut
}
FICHIER 4 : Repository CertificateProfileRepository.java (Nouvelle Interface)
Rôle : Interface Spring Data JPA pour accéder à la table CERTIFICATE_PROFILE.
Emplacement : src/main/java/com/bnpparibas/certis/automationhub/repository/
code
Java
package com.bnpparibas.certis.automationhub.repository;

import com.bnpparibas.certis.automationhub.model.CertificateProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CertificateProfileRepository extends JpaRepository<CertificateProfileEntity, Long> {
    Optional<CertificateProfileEntity> findByProfileName(String profileName);
}
FICHIER 5 : Service CertificateProfileService.java (Nouvelle Classe)
Rôle : Contient la logique pour appeler l'API externe et sauvegarder les règles en base.
Emplacement : src/main/java/com/bnpparibas/certis/automationhub/service/
code
Java
package com.bnpparibas.certis.automationhub.service;

import // ... tous les imports nécessaires (DTOs, entités, repositories, RestTemplate)
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class CertificateProfileService {

    @Value("${external.api.url.profiles}")
    private String externalApiBaseUrl;
    
    private final RestTemplate restTemplate;
    private final CertificateProfileRepository certificateProfileRepository;
    
    // Constructeur pour l'injection

    @Transactional
    public void fetchAndSaveSanProfileRules(String profileName) {
        String apiUrl = externalApiBaseUrl + "/" + profileName;
        ExternalSanProfileResponse response = restTemplate.getForObject(apiUrl, ExternalSanProfileResponse.class);
        
        // Logique pour récupérer ou créer le CertificateProfileEntity
        // Boucle sur `response.getSans()` pour créer/mettre à jour les `SanTypeRuleEntity`
        // Application des règles du ticket Jira (si min non présent -> 0, si max non présent -> 250, etc.)
        // Sauvegarde de l'entité CertificateProfileEntity (qui sauvegardera les règles en cascade)
    }
}
(NOTE : Vous aurez besoin des DTOs ExternalSanProfileResponse et ExternalSanTypeRuleDto pour que ce service fonctionne)
FICHIER 6 : Tâche SyncSanProfilesTask.java (Nouvelle Classe)
Rôle : Déclenche périodiquement la synchronisation des profils.
Emplacement : src/main/java/com/bnpparibas/certis/api/tasks/
code
Java
package com.bnpparibas.certis.api.tasks;

import com.bnpparibas.certis.automationhub.service.CertificateProfileService;
import com.bnpparibas.certis.model.enums.CertificateProfileType;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SyncSanProfilesTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncSanProfilesTask.class);
    private final CertificateProfileService profileService;
    private Set<CertificateProfileType> profilesToSync = EnumSet.noneOf(CertificateProfileType.class);

    public SyncSanProfilesTask(CertificateProfileService profileService) {
        this.profileService = profileService;
    }

    @Value("${certis.profiles.to-sync}")
    public void setProfilesToSync(List<String> profileNames) {
        if (profileNames != null && !profileNames.isEmpty()) {
            this.profilesToSync = profileNames.stream()
                .map(CertificateProfileType::fromValue)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CertificateProfileType.class)));
        }
    }

    @Scheduled(cron = "${certis.profiles.sync-cron:0 0 2 * * ?}")
    @SchedulerLock(name = "SyncSanProfilesTask_lock")
    public void runTask() {
        this.syncAllSanProfileRules();
    }

    private void syncAllSanProfileRules() {
        LOGGER.info("Début de la tâche de synchronisation des profils de SANs.");
        // Logique de boucle sur `profilesToSync` et appel à `profileService.fetchAndSaveSanProfileRules()`
        // avec gestion des erreurs (try/catch).
    }
}
FICHIER 7 : Service SanServiceImpl.java (Classe existante à modifier)
Rôle : Utilise les nouvelles règles pour la validation.
Emplacement : src/main/java/com/bnpparibas/certis/certificate/request/service/impl/
code
Java
// ... imports existants
import com.bnpparibas.certis.automationhub.model.CertificateProfileEntity;
import com.bnpparibas.certis.automationhub.repository.CertificateProfileRepository;
// ...

@Service
public class SanServiceImpl implements SanService {

    // ... autres dépendances
    private final CertificateProfileRepository certificateProfileRepository;

    // MODIFIER le constructeur pour injecter le nouveau repository
    public SanServiceImpl(/* ... autres dépendances */, CertificateProfileRepository certificateProfileRepository) {
        // ...
        this.certificateProfileRepository = certificateProfileRepository;
    }
    
    // === À SUPPRIMER ===
    // private final Integer EXT_SSL_LIMIT = 250;
    // private final Integer INT_SSL_SRVR_LIMIT = 249;
    // Et toutes les anciennes méthodes : verifySansLimitForInternalCertificates, etc.

    @Override
    public void validateSansPerRequest(RequestDto requestDto) throws CertisRequestException {
        // ...
        // ANCIEN CODE À SUPPRIMER
        // this.verifySansLimitForInternalCertificates(requestDto);
        
        // NOUVEL APPEL
        this.verifySansLimitsDynamically(requestDto);
        
        // ... conserver les autres validations (format, etc.)
    }

    // === NOUVELLE MÉTHODE PRIVÉE ===
    private void verifySansLimitsDynamically(RequestDto requestDto) throws CertisRequestException {
        // Logique complète pour :
        // 1. Récupérer le nom du profil depuis requestDto.
        // 2. Charger les règles via `certificateProfileRepository.findByProfileName()`.
        // 3. Compter les SANs par type dans la requête.
        // 4. Comparer les comptes aux règles (min/max) et lancer une exception si non respecté.
    }
    
    // ... reste de la classe inchangé
}
FICHIER 8 : Configuration application.properties (ou .yml) (Fichier existant à modifier)
Rôle : Configurer la tâche et l'URL de l'API externe.
Emplacement : src/main/resources/
code
Properties
# URL de l'API externe pour récupérer les profils de certificats
external.api.url.profiles=https://automation.staging.echonet.net.intra/api/v1/certificate/profiles

# Planification CRON pour la tâche de synchronisation des profils
certis.profiles.sync-cron=0 0 2 * * ?

# Liste des profils à synchroniser (doivent correspondre aux valeurs de l'Enum CertificateProfileType)
certis.profiles.to-sync=SSL_SRVR,SSL_CLI_SRVR,SSL_CLI,Appli-SSL-Client-Server
FICHIER 9 : Configuration de l'Application (Fichiers existants à vérifier/modifier)
Classe principale (CertisApp.java) : Doit avoir l'annotation @EnableScheduling.
Classe de configuration (AppConfig.java) : Doit fournir un bean RestTemplate.
/////////////////// Test ///////////////////////

Model
Tester un scheduler (@Scheduled) est un peu différent des tests de service classiques, car on ne veut pas attendre que le temps passe réellement.
Il y a deux approches principales pour tester un scheduler :
Tester la logique métier de la tâche (Recommandé) : On teste la méthode qui contient la logique (syncAllSanProfileRules()) directement, sans se soucier de l'annotation @Scheduled.
Tester que le scheduler est bien configuré (Avancé) : On peut vérifier que Spring a bien configuré la tâche pour qu'elle s'exécute avec la bonne planification CRON.
L'approche 1 est la plus importante et la plus utile. L'approche 2 est plus un test d'intégration qui vérifie votre configuration Spring.
Je vais vous fournir le code pour l'approche 1, car elle teste ce qui compte vraiment : le comportement de votre tâche.
Test de la Logique Métier du Scheduler (SyncSanProfilesTask)
Ce test vérifie que lorsque la tâche s'exécute, elle appelle correctement le CertificateProfileService pour chaque profil configuré et gère bien les cas d'erreur.
Emplacement : src/test/java/com/bnpparibas/certis/api/tasks/SyncSanProfilesTaskTest.java
code
Java
package com.bnpparibas.certis.api.tasks;

import com.bnpparibas.certis.automationhub.service.CertificateProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncSanProfilesTaskTest {

    @Mock
    private CertificateProfileService profileService;

    @InjectMocks
    private SyncSanProfilesTask syncTask;

    @BeforeEach
    void setUp() {
        // Comme la liste `profilesToSync` est injectée via @Value,
        // on doit la définir manuellement dans notre test.
        // ReflectionTestUtils est un utilitaire de Spring pour manipuler les champs privés.
        ReflectionTestUtils.setField(syncTask, "profilesToSync",
                Arrays.asList("SSL_SRVR", "SSL_CLI_SRVR", "INVALID_PROFILE"));
    }

    @Test
    void whenTaskRuns_thenServiceIsCalledForEachProfile() throws Exception {
        // ARRANGE
        // On configure les mocks pour qu'ils ne fassent rien (comportement par défaut)
        // ou pour qu'ils se comportent comme attendu.
        
        // Pour les profils valides, la méthode s'exécute sans erreur.
        doNothing().when(profileService).fetchAndSaveSanProfileRules("SSL_SRVR");
        doNothing().when(profileService).fetchAndSaveSanProfileRules("SSL_CLI_SRVR");
        
        // Pour le profil invalide, on simule une exception.
        doThrow(new RuntimeException("API Error")).when(profileService).fetchAndSaveSanProfileRules("INVALID_PROFILE");
        
        // ACT
        // On appelle directement la méthode publique qui contient la logique,
        // PAS celle annotée @Scheduled. Ici, c'est `runTask` qui appelle `syncAllSanProfileRules`.
        syncTask.runTask();

        // ASSERT
        // On vérifie que le service a été appelé exactement une fois pour chaque profil dans la liste.
        verify(profileService, times(1)).fetchAndSaveSanProfileRules("SSL_SRVR");
        verify(profileService, times(1)).fetchAndSaveSanProfileRules("SSL_CLI_SRVR");
        verify(profileService, times(1)).fetchAndSaveSanProfileRules("INVALID_PROFILE");

        // On vérifie que le service a été appelé 3 fois au total.
        verify(profileService, times(3)).fetchAndSaveSanProfileRules(anyString());
    }

    @Test
    void whenProfileListIsEmpty_thenServiceIsNeverCalled() {
        // ARRANGE
        // On reconfigure la liste pour qu'elle soit vide.
        ReflectionTestUtils.setField(syncTask, "profilesToSync", Collections.emptyList());

        // ACT
        syncTask.runTask();

        // ASSERT
        // On vérifie que le service n'a JAMAIS été appelé.
        verify(profileService, never()).fetchAndSaveSanProfileRules(anyString());
    }
}
Explication de ce test
@ExtendWith(MockitoExtension.class) : Active l'utilisation de Mockito pour créer des mocks.
@Mock et @InjectMocks : On "mocke" (simule) le CertificateProfileService car nous ne voulons pas tester sa logique ici, mais seulement vérifier que notre scheduler l'appelle correctement. @InjectMocks crée une instance de SyncSanProfilesTask et y injecte le mock.
ReflectionTestUtils.setField(...) : C'est la partie la plus importante. Comme la liste des profils est injectée par @Value dans l'application réelle, nous devons simuler cette injection dans notre test. Cet utilitaire Spring nous permet de forcer une valeur dans un champ (même privé) de notre objet de test.
doNothing().when(...) et doThrow(...).when(...) : On configure le comportement de notre mock. On dit à Mockito : "Quand la méthode fetchAndSaveSanProfileRules est appelée avec "SSL_SRVR", ne fais rien. Quand elle est appelée avec "INVALID_PROFILE", lance une exception." Cela nous permet de tester que notre scheduler continue son travail même si un appel échoue.
syncTask.runTask() : On appelle directement la méthode de la tâche. On ne teste pas la planification, on teste la logique qui serait exécutée par le planificateur.
verify(..., times(1)) et verify(..., never()) : Ce sont les assertions de Mockito. On vérifie que les méthodes de notre mock ont été appelées le bon nombre de fois, avec les bons arguments. Cela prouve que notre boucle et notre logique de gestion des erreurs fonctionnent.
Pourquoi cette approche est la meilleure ?
Rapide et Fiable : Le test s'exécute en quelques millisecondes et n'a pas de dépendances externes (temps, réseau, base de données).
Ciblé : Il teste la logique de la tâche elle-même, pas le framework Spring. C'est le principe du test unitaire.
Facile à Maintenir : Si vous ajoutez de la logique à votre tâche (par exemple, envoyer un email en cas d'erreur), vous n'avez qu'à ajouter des verify() à ce test pour vérifier que le service d'email est bien appelé.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
