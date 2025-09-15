
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
///////////////////////////////////////////////////////////////////////////// 
Absolument. Votre intuition est excellente et c'est la bonne manière de faire : au lieu de deviner la liste des profils, il faut demander à l'API de tous les lister. C'est beaucoup plus robuste et efficace.
En analysant vos images, on peut déduire la solution complète.
Réponse Directe à votre Question
Quel est le type de retour pour RestTemplate ?
Image 1 montre une liste de profils. Cela implique qu'il existe un endpoint d'API pour récupérer cette liste, probablement .../api/v1/certificate/profiles (sans nom de profil à la fin).
Image 2 montre la structure JSON pour un seul profil.
Selon les conventions des API REST, un endpoint qui liste des ressources retourne un tableau JSON ([...]) où chaque élément est un objet ressource.
Donc, quand vous appellerez restTemplate.getForObject() sur l'URL de la liste, le type de retour attendu sera un tableau de l'objet que vous avez vu dans l'image 2.
En Java, cela se traduit par ExternalProfileDto[].
Voici le plan d'action complet pour implémenter cette solution, qui est bien meilleure que la précédente.
Étape 1 : Modifier vos DTOs pour correspondre à la réponse de l'API
Votre DTO ExternalSanProfileResponse n'était pas tout à fait correct. D'après l'image 2, le sans est une propriété à l'intérieur de l'objet profil. Nous allons créer des DTOs qui correspondent exactement à cette structure.
Fichier 1 : ExternalProfileDto.java (Le DTO pour un profil complet)
(Ce DTO remplace votre ancien ExternalSanProfileResponse)
code
Java
package com.bnpparibas.certis.automationhub.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// Ignore les propriétés inconnues pour ne pas avoir d'erreur si l'API en ajoute
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalProfileDto {

    // On a besoin du nom pour le retrouver en base !
    private String name;
    
    // Et de la liste des règles de SANs
    private List<ExternalSanRuleDto> sans;
    
    // Ajoutez d'autres champs de l'API si vous en avez besoin (ex: description)
    // private String description;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ExternalSanRuleDto> getSans() {
        return sans;
    }

    public void setSans(List<ExternalSanRuleDto> sans) {
        this.sans = sans;
    }
}
Fichier 2 : ExternalSanRuleDto.java (Le DTO pour une règle de SAN)
(Ce DTO remplace votre ancien ExternalSanTypeRuleDto)
code
Java
package com.bnpparibas.certis.automationhub.dto.external;

import com.bnpparibas.certis.certificate.request.dto.SanTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalSanRuleDto {

    private SanTypeEnum type;
    private Boolean editableByRequester;
    private Integer min;
    private Integer max;

    // Getters and Setters pour tous les champs...
}
Étape 2 : Modifier votre Service CertificateProfileService
Nous allons créer une nouvelle méthode publique pour le scheduler et une méthode privée pour la logique de traitement afin d'éviter la duplication de code.
code
Java
package com.bnpparibas.certis.automationhub.service;

// ... tous vos imports ...
import com.bnpparibas.certis.automationhub.dto.external.ExternalProfileDto;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;

@Service
public class CertificateProfileService {
    
    // ... vos dépendances (RestTemplate, Repository) ...

    /**
     * NOUVELLE méthode que le scheduler appellera.
     * Elle ne prend aucun paramètre et synchronise TOUS les profils.
     */
    @Transactional
    public void syncAllProfilesFromApi() {
        // L'URL de base, SANS nom de profil
        String listApiUrl = externalApiBaseUrl; 
        
        LOGGER.info("Appel de l'API pour lister tous les profils depuis : {}", listApiUrl);
        
        // C'est ici que l'on récupère le TABLEAU de profils
        ExternalProfileDto[] allProfiles = restTemplate.getForObject(listApiUrl, ExternalProfileDto[].class);
        
        if (allProfiles == null || allProfiles.length == 0) {
            LOGGER.warn("Aucun profil n'a été retourné par l'API externe.");
            return;
        }

        LOGGER.info("{} profils ont été récupérés depuis l'API. Début du traitement...", allProfiles.length);

        // On boucle sur chaque profil retourné par l'API
        Arrays.stream(allProfiles).forEach(this::processAndSaveProfile);
        
        LOGGER.info("Traitement de tous les profils terminé.");
    }
    
    /**
     * Méthode privée qui contient la logique de traitement pour UN profil.
     * Elle est appelée par la nouvelle méthode de synchronisation.
     * @param profileData Le DTO d'un profil récupéré depuis l'API.
     */
    private void processAndSaveProfile(ExternalProfileDto profileData) {
        if (profileData == null || profileData.getName() == null || profileData.getName().isEmpty()) {
            LOGGER.warn("Un profil retourné par l'API est invalide (pas de nom), il est ignoré.");
            return;
        }

        String profileName = profileData.getName();
        
        // 1. Récupérer ou créer le CertificateProfileEntity
        CertificateProfileEntity profileEntity = certificateProfileRepository.findByProfileName(profileName)
                .orElseGet(() -> {
                    LOGGER.info("Le profil '{}' est nouveau, création de l'entité.", profileName);
                    CertificateProfileEntity newProfile = new CertificateProfileEntity();
                    newProfile.setProfileName(profileName);
                    return newProfile;
                });

        // 2. Vider les anciennes règles pour les remplacer par les nouvelles
        profileEntity.getSanTypeRules().clear();
        
        // 3. Traiter et ajouter les nouvelles règles
        if (profileData.getSans() != null) {
            for (ExternalSanRuleDto ruleDto : profileData.getSans()) {
                SanTypeRuleEntity ruleEntity = new SanTypeRuleEntity();
                
                // Appliquer les règles du ticket Jira
                Integer minVal = ruleDto.getMin() != null ? ruleDto.getMin() : 0;
                Integer maxVal = ruleDto.getMax() != null ? ruleDto.getMax() : 250;
                if (Boolean.FALSE.equals(ruleDto.getEditableByRequester())) {
                    maxVal = 0;
                }

                ruleEntity.setSanType(ruleDto.getType());
                ruleEntity.setMinValue(minVal);
                ruleEntity.setMaxValue(maxVal);
                ruleEntity.setEditableByRequester(ruleDto.getEditableByRequester());
                
                // Lier la règle au profil parent
                ruleEntity.setCertificateProfile(profileEntity);
                profileEntity.getSanTypeRules().add(ruleEntity);
            }
        }
        
        // 4. Sauvegarder le profil (et ses règles en cascade)
        certificateProfileRepository.save(profileEntity);
        LOGGER.debug("Profil '{}' sauvegardé avec {} règles.", profileName, profileEntity.getSanTypeRules().size());
    }
}
Étape 3 : Simplifier le Scheduler SyncSanProfilesTask
Maintenant, le scheduler devient extrêmement simple. Il n'a plus besoin d'une liste de profils dans la configuration. Il n'a qu'une seule chose à faire : appeler la nouvelle méthode du service.
code
Java
package com.bnpparibas.certis.api.tasks;

// ... imports ...

@Component
public class SyncSanProfilesTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncSanProfilesTask.class);
    private final CertificateProfileService profileService;

    public SyncSanProfilesTask(CertificateProfileService profileService) {
        this.profileService = profileService;
    }

    @Scheduled(cron = "${certis.profiles.sync-cron:0 0 2 * * ?}")
    @SchedulerLock(name = "SyncSanProfilesTask_lock")
    public void runTask() {
        LOGGER.info("Début de la tâche planifiée de synchronisation de tous les profils de SANs.");
        try {
            // Un seul appel pour tout synchroniser !
            profileService.syncAllProfilesFromApi();
            LOGGER.info("Tâche de synchronisation des profils terminée avec succès.");
        } catch (Exception e) {
            LOGGER.error("Une erreur critique est survenue durant la tâche de synchronisation des profils.", e);
        }
    }
}
Étape 4 : Mettre à jour la Configuration
Vous pouvez maintenant supprimer la ligne certis.profiles.to-sync de votre fichier application.properties, elle n'est plus nécessaire !
Avantages de cette nouvelle approche
Efficacité : Un seul appel à l'API pour tout récupérer.
Robustesse : Si un administrateur ajoute un nouveau profil dans Horizon, votre scheduler le découvrira et le synchronisera automatiquement à la prochaine exécution. Vous n'avez plus besoin de penser à mettre à jour votre fichier de configuration.
Simplicité : La configuration est plus simple et le code du scheduler est plus direct.
/////////////////////////// resumer 2///////////////////
Expand to view model thoughts

chevron_right
Oui, votre demande est exactement la bonne architecture à suivre. C'est le principe de la "Séparation des responsabilités" (Separation of Concerns).
Voici un résumé clair des rôles et le code final pour chaque composant :
Le Scheduler (SyncSanProfilesTask) : Son seul rôle est d'être le déclencheur. Il est comme un manager qui dit : "Il est 2h du matin, il est temps de faire la mise à jour". Il ne sait pas comment faire la mise à jour, il délègue simplement le travail.
Le Service (CertificateProfileService) : C'est l'expert qui fait tout le travail. Il sait comment :
Contacter l'API externe pour récupérer les données.
Traiter ces données (appliquer les règles, transformer les DTOs en entités).
Interagir avec la base de données pour mettre à jour les informations.
Cette séparation rend votre code beaucoup plus propre, plus facile à tester et à maintenir.
Le Code Final pour Implémenter ce Flux
1. Le Scheduler (Le Déclencheur)
Cette classe est simple. Elle ne fait qu'appeler le service à une heure précise.
Fichier : src/main/java/com/bnpparibas/certis/api/tasks/SyncSanProfilesTask.java
code
Java
package com.bnpparibas.certis.api.tasks;

import com.bnpparibas.certis.automationhub.service.CertificateProfileService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncSanProfilesTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncSanProfilesTask.class);
    
    // On injecte le service qui va faire le travail
    private final CertificateProfileService profileService;

    public SyncSanProfilesTask(CertificateProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Tâche planifiée qui se déclenche selon la configuration CRON.
     * Son unique responsabilité est d'appeler le service pour lancer la synchronisation.
     */
    @Scheduled(cron = "${certis.profiles.sync-cron:0 0 2 * * ?}")
    @SchedulerLock(name = "SyncSanProfilesTask_lock")
    public void runTask() {
        LOGGER.info("Début de la tâche planifiée de synchronisation de TOUS les profils de SANs.");
        
        try {
            // DÉLÉGATION : Le scheduler demande au service de faire tout le travail.
            profileService.syncAllProfilesFromApi();
            
            LOGGER.info("Tâche de synchronisation des profils terminée avec succès.");
        } catch (Exception e) {
            // En cas d'erreur majeure (ex: l'API ne répond pas du tout), on la loggue.
            // La gestion fine des erreurs par profil est déjà dans le service.
            LOGGER.error("Une erreur critique est survenue durant la tâche de synchronisation des profils.", e);
        }
    }
}
2. Le Service (L'Expert)
Cette classe contient toute la logique complexe : appel à l'API et mise à jour de la base de données. L'annotation @Transactional est cruciale ici : elle garantit que soit tous les profils sont mis à jour, soit aucun s'il y a une erreur, ce qui évite d'avoir une base de données dans un état incohérent.
Fichier : src/main/java/com/bnpparibas/certis/automationhub/service/CertificateProfileService.java
code
Java
package com.bnpparibas.certis.automationhub.service;

import com.bnpparibas.certis.automationhub.dto.external.ExternalProfileDto;
import com.bnpparibas.certis.automationhub.dto.external.ExternalSanRuleDto;
import com.bnpparibas.certis.automationhub.model.CertificateProfileEntity;
import com.bnpparibas.certis.automationhub.model.SanTypeRuleEntity;
import com.bnpparibas.certis.automationhub.repository.CertificateProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.util.Arrays;

@Service
public class CertificateProfileService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateProfileService.class);

    private final RestTemplate restTemplate;
    private final CertificateProfileRepository certificateProfileRepository;

    @Value("${external.api.url.profiles}")
    private String externalApiBaseUrl;

    public CertificateProfileService(RestTemplate restTemplate, CertificateProfileRepository certificateProfileRepository) {
        this.restTemplate = restTemplate;
        this.certificateProfileRepository = certificateProfileRepository;
    }

    /**
     * Méthode principale qui orchestre la récupération et la mise à jour de tous les profils.
     * L'annotation @Transactional assure que l'opération est atomique.
     */
    @Transactional
    public void syncAllProfilesFromApi() {
        LOGGER.info("Appel de l'API pour lister tous les profils depuis : {}", externalApiBaseUrl);
        
        // 1. RÉCUPÉRER LES DONNÉES depuis l'API externe
        ExternalProfileDto[] allProfilesFromApi = restTemplate.getForObject(externalApiBaseUrl, ExternalProfileDto[].class);
        
        if (allProfilesFromApi == null || allProfilesFromApi.length == 0) {
            LOGGER.warn("Aucun profil n'a été retourné par l'API externe. La synchronisation est terminée.");
            return;
        }

        LOGGER.info("{} profils ont été récupérés. Début de la mise à jour en base de données...", allProfilesFromApi.length);

        // 2. METTRE À JOUR LA BASE DE DONNÉES
        // On boucle sur chaque profil retourné par l'API pour le traiter
        Arrays.stream(allProfilesFromApi).forEach(this::processAndSaveSingleProfile);
        
        LOGGER.info("Mise à jour de tous les profils en base de données terminée.");
    }
    
    /**
     * Logique pour traiter et sauvegarder UN seul profil.
     */
    private void processAndSaveSingleProfile(ExternalProfileDto profileData) {
        if (profileData == null || profileData.getName() == null || profileData.getName().isEmpty()) {
            LOGGER.warn("Un profil retourné par l'API est invalide (pas de nom), il est ignoré.");
            return;
        }

        String profileName = profileData.getName();
        
        // On cherche le profil dans notre BDD, ou on en crée un nouveau s'il n'existe pas.
        CertificateProfileEntity profileEntity = certificateProfileRepository.findByProfileName(profileName)
                .orElseGet(() -> {
                    LOGGER.info("Le profil '{}' est nouveau, création de l'entité.", profileName);
                    CertificateProfileEntity newProfile = new CertificateProfileEntity();
                    newProfile.setProfileName(profileName);
                    return newProfile;
                });

        // On supprime les anciennes règles pour les remplacer par les nouvelles (garantit la fraîcheur des données)
        profileEntity.getSanTypeRules().clear();
        
        // On traite et on ajoute les nouvelles règles reçues de l'API
        if (profileData.getSans() != null) {
            for (ExternalSanRuleDto ruleDto : profileData.getSans()) {
                // ... (Logique de création de SanTypeRuleEntity avec les règles min/max du ticket) ...
                // ... (Lier la règle au profil parent) ...
                // ... (Ajouter la règle à la collection du profil) ...
            }
        }
        
        // La sauvegarde est gérée par @Transactional à la fin de la méthode `syncAllProfilesFromApi`
        // mais un save explicite est aussi possible et parfois plus clair.
        certificateProfileRepository.save(profileEntity);
    }
}
Schéma du Flux de Travail
Voici comment les deux classes collaborent :
code
Code
+--------------------------+        +-----------------------------------+
|                          |        |                                   |
| SyncSanProfilesTask      |------->|  CertificateProfileService        |
| (Le Scheduler / Manager) |        |  (Le Service / L'Expert)          |
|                          |        |                                   |
+--------------------------+        +-----------------------------------+
           |                                  |                |
           | 1. Déclenche la tâche            |                |
           |    (tous les jours à 2h)         |                |
           |                                  |                |
           | 2. Appelle syncAllProfilesFromApi() |                |
           +------------------------------------->                |
                                            |                |
                                            | 3. Appelle l'API externe avec RestTemplate
                                            +-------------------------------> [ API EXTERNE ]
                                                                             
                                            | 4. Reçoit les données
                               [ API EXTERNE ] ------------------------------>

                                            | 5. Traite les données et met à jour
                                            |    la BDD via le Repository
                                            +-------------------------------> [ Base de Données ]
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
///////////////////
Fichier : ExternalSanRuleDto.java
Rôle : Représente une seule règle de SAN (avec son type, min, max, etc.) telle qu'elle est définie dans le JSON de l'API externe. C'est un simple conteneur de données.
Emplacement : src/main/java/com/bnpparibas/certis/automationhub/dto/external/ (ou un autre package dto)
code
Java
package com.bnpparibas.certis.automationhub.dto.external;

import com.bnpparibas.certis.certificate.request.dto.SanTypeEnum; // Assurez-vous d'importer votre Enum existant
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * DTO représentant une règle de SAN (Subject Alternative Name)
 * telle que retournée par l'API externe des profils de certificats.
 * Cette classe est utilisée par Jackson pour désérialiser la réponse JSON.
 */
// @JsonIgnoreProperties(ignoreUnknown = true) est très important :
// Il permet à votre application de ne pas planter si l'API externe ajoute de nouvelles
// propriétés que vous ne gérez pas encore dans ce DTO.
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalSanRuleDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Le type de SAN. Ex: CN, DNSNAME, IPADDRESS...
     * La désérialisation se basera sur la valeur textuelle du JSON pour trouver la bonne constante de l'Enum.
     */
    @JsonProperty("type")
    private SanTypeEnum type;

    /**
     * Indique si cette valeur de SAN peut être éditée par le demandeur.
     * C'est une information cruciale pour la logique métier (si false, max = 0).
     */
    @JsonProperty("editableByRequester")
    private Boolean editableByRequester;

    /**
     * Le nombre minimum de SANs de ce type autorisés.
     * Peut être null dans la réponse JSON.
     */
    @JsonProperty("min")
    private Integer min;

    /**
     * Le nombre maximum de SANs de ce type autorisés.
     * Peut être null dans la réponse JSON.
     */
    @JsonProperty("max")
    private Integer max;
    
    // Ajoutez d'autres champs si vous en avez besoin, par exemple :
    // @JsonProperty("editableByApprover")
    // private Boolean editableByApprover;

    // --- Constructeurs ---

    /**
     * Constructeur par défaut requis pour la désérialisation.
     */
    public ExternalSanRuleDto() {
    }

    // --- Getters et Setters ---
    // Ils sont indispensables pour que la librairie de désérialisation (Jackson)
    // puisse peupler les champs de l'objet.

    public SanTypeEnum getType() {
        return type;
    }

    public void setType(SanTypeEnum type) {
        this.type = type;
    }

    public Boolean getEditableByRequester() {
        return editableByRequester;
    }

    public void setEditableByRequester(Boolean editableByRequester) {
        this.editableByRequester = editableByRequester;
    }

    public Integer getMin() {
        return min;
    }

    public void setMin(Integer min) {
        this.min = min;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }

    // --- toString() (Utile pour le débogage) ---

    @Override
    public String toString() {
        return "ExternalSanRuleDto{" +
                "type=" + type +
                ", editableByRequester=" + editableByRequester +
                ", min=" + min +
                ", max=" + max +
                '}';
    }
}
Points Clés de ce Fichier
@JsonIgnoreProperties(ignoreUnknown = true) : C'est une protection très importante. Elle rend votre application plus résiliente aux changements de l'API externe.
@JsonProperty("...") : Cette annotation mappe explicitement un champ JSON à un champ Java. C'est une bonne pratique, même si les noms sont identiques, car cela rend le code plus clair et vous protège si vous décidez de renommer vos variables Java.
Types Wrapper (Integer, Boolean) : Comme discuté précédemment, on utilise les classes Wrapper au lieu des types primitifs (int, boolean). C'est essentiel car les champs dans le JSON peuvent être absents (null), et un type primitif ne peut pas représenter cet état.
Serializable : C'est une bonne pratique pour les DTOs, bien que ce ne soit pas toujours strictement nécessaire selon l'usage.
SanTypeEnum : J'ai supposé que votre SanTypeEnum existant correspond aux valeurs textuelles du JSON ("CN", "RFC822NAME", etc.). Si ce n'est pas le cas, il faudra peut-être ajouter une logique de conversion personnalisée, mais en général, Jackson gère très bien la désérialisation des Enums.
Getters et Setters : Ils sont absolument nécessaires pour que Jackson puisse créer une instance de l'objet et y injecter les valeurs lues depuis le JSON.

//////////////////////////////// explication detille du service/////////////////////////////////
Voici le code complet de la classe CertificateProfileService, avec des explications très détaillées pour chaque section, chaque ligne importante et chaque décision de conception.
Fichier : CertificateProfileService.java
Rôle principal : Orchestrer la synchronisation des règles de profils de certificats. C'est l'expert qui sait comment parler à l'API externe et comment mettre à jour la base de données locale.
Déclenchement : Cette classe est conçue pour être appelée par le SyncSanProfilesTask (le scheduler).
Emplacement : src/main/java/com/bnpparibas/certis/automationhub/service/
code
Java
// Le package où se trouve votre service
package com.bnpparibas.certis.automationhub.service;

// --- Imports ---
// On importe toutes les classes nécessaires pour que le service fonctionne.
import com.bnpparibas.certis.automationhub.dto.external.ExternalProfileDto;
import com.bnpparibas.certis.automationhub.dto.external.ExternalSanRuleDto;
import com.bnpparibas.certis.automationhub.model.CertificateProfileEntity;
import com.bnpparibas.certis.automationhub.model.SanTypeRuleEntity;
import com.bnpparibas.certis.automationhub.repository.CertificateProfileRepository;
import com.bnpparibas.certis.automationhub.repository.SanTypeRuleRepository; // Import pour gérer les règles de SAN
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service chargé de la gestion des profils de certificats.
 * Sa responsabilité principale est de synchroniser les règles de profils
 * depuis une API externe vers la base de données locale.
 */
@Service // Annotation qui déclare cette classe comme un "Service" Spring. Spring va en créer une instance (un "bean") et la gérer.
public class CertificateProfileService {
    
    // Un logger pour écrire des messages dans les logs de l'application. C'est essentiel pour le débogage.
    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateProfileService.class);

    // --- Dépendances ---
    // Ce sont les "outils" dont notre service a besoin pour travailler. Spring les injectera automatiquement.

    private final RestTemplate restTemplate; // L'outil pour faire des appels HTTP vers l'API externe.
    private final CertificateProfileRepository certificateProfileRepository; // L'outil pour parler à la table CERTIFICATE_PROFILE.
    private final SanTypeRuleRepository sanTypeRuleRepository; // L'outil pour parler à la table SAN_TYPE_RULE.

    // La valeur de cette variable sera injectée depuis le fichier application.properties.
    // C'est ici qu'on configure l'URL de base de l'API externe.
    @Value("${external.api.url.profiles}")
    private String externalApiBaseUrl;

    /**
     * Constructeur du service.
     * C'est la méthode recommandée pour l'injection de dépendances.
     * Spring va automatiquement trouver les beans correspondants et les passer en paramètres.
     * @param restTemplate Le RestTemplate spécifique pour l'automation hub.
     * @param certificateProfileRepository Le repository pour les profils.
     * @param sanTypeRuleRepository Le repository pour les règles de SAN.
     */
    public CertificateProfileService(
            @Qualifier("automationHubRestTemplate") RestTemplate restTemplate, // @Qualifier est crucial car vous avez plusieurs RestTemplate
            CertificateProfileRepository certificateProfileRepository,
            SanTypeRuleRepository sanTypeRuleRepository) {
        this.restTemplate = restTemplate;
        this.certificateProfileRepository = certificateProfileRepository;
        this.sanTypeRuleRepository = sanTypeRuleRepository;
    }

    /**
     * Méthode principale orchestrant la synchronisation de TOUS les profils.
     * C'est cette méthode qui sera appelée par le scheduler.
     * 
     * @Transactional : C'est une annotation très puissante. Elle dit à Spring de démarrer une transaction
     * avec la base de données avant d'exécuter cette méthode. Si la méthode se termine sans erreur,
     * la transaction est "commit" (toutes les modifications sont sauvegardées). Si une erreur (Exception)
     * se produit, la transaction est "rollback" (toutes les modifications sont annulées).
     * Cela garantit que votre base de données reste dans un état cohérent.
     */
    @Transactional
    public void syncAllProfilesFromApi() {
        LOGGER.info("Appel de l'API externe pour lister tous les profils depuis l'URL : {}", externalApiBaseUrl);
        
        // 1. RÉCUPÉRER LES DONNÉES DEPUIS L'API EXTERNE
        // On utilise restTemplate pour faire un appel GET sur l'URL de base.
        // On s'attend à recevoir un tableau de profils, donc le type de retour est ExternalProfileDto[].
        ExternalProfileDto[] allProfilesFromApi = restTemplate.getForObject(externalApiBaseUrl, ExternalProfileDto[].class);
        
        // C'est une bonne pratique de vérifier si la réponse n'est pas vide.
        if (allProfilesFromApi == null || allProfilesFromApi.length == 0) {
            LOGGER.warn("Aucun profil n'a été retourné par l'API externe. La synchronisation est terminée.");
            return; // On arrête la méthode ici car il n'y a rien à faire.
        }

        LOGGER.info("{} profils ont été récupérés de l'API. Début de la mise à jour en base de données...", allProfilesFromApi.length);

        // On transforme le tableau en Set de noms pour une recherche plus efficace plus tard.
        Set<String> profileNamesFromApi = Arrays.stream(allProfilesFromApi)
                                                .map(ExternalProfileDto::getName)
                                                .collect(Collectors.toSet());

        // 2. METTRE À JOUR LA BASE DE DONNÉES
        // On boucle sur chaque profil retourné par l'API pour le traiter et le sauvegarder.
        Arrays.stream(allProfilesFromApi).forEach(this::processAndSaveSingleProfile);
        
        // 3. (Optionnel mais recommandé) DÉSACTIVER LES ANCIENS PROFILS
        // On cherche les profils qui sont dans notre base mais qui n'ont PAS été retournés par l'API.
        // Cela signifie qu'ils ont été supprimés ou désactivés dans le système source (Horizon).
        deactivateOrphanedProfiles(profileNamesFromApi);
        
        LOGGER.info("Mise à jour de tous les profils en base de données terminée.");
    }
    
    /**
     * Logique pour traiter et sauvegarder UN seul profil reçu de l'API.
     * Cette méthode est privée car elle n'est qu'un détail d'implémentation de la méthode principale.
     * @param profileData Le DTO d'un profil contenant les données de l'API.
     */
    private void processAndSaveSingleProfile(ExternalProfileDto profileData) {
        // Validation simple pour ignorer les données invalides.
        if (profileData == null || profileData.getName() == null || profileData.getName().isEmpty()) {
            LOGGER.warn("Un profil retourné par l'API est invalide (nom manquant) et est ignoré.");
            return;
        }

        String profileName = profileData.getName();
        
        // On cherche le profil dans notre BDD. S'il n'existe pas, on en crée un nouveau.
        // C'est une opération "upsert" (update or insert).
        CertificateProfileEntity profileEntity = certificateProfileRepository.findByProfileName(profileName)
                .orElseGet(() -> {
                    LOGGER.info("Le profil '{}' est nouveau. Création d'une nouvelle entité.", profileName);
                    CertificateProfileEntity newProfile = new CertificateProfileEntity();
                    newProfile.setProfileName(profileName);
                    return newProfile;
                });
        
        // Si le profil existait mais était désactivé, on le réactive.
        profileEntity.setActive(true);

        // On utilise un Set pour stocker les nouvelles règles.
        Set<SanTypeRuleEntity> newRules = new HashSet<>();
        
        // On traite les règles de SANs si la liste n'est pas vide.
        if (profileData.getSans() != null) {
            for (ExternalSanRuleDto ruleDto : profileData.getSans()) {
                SanTypeRuleEntity ruleEntity = new SanTypeRuleEntity();
                
                // --- Logique métier du ticket Jira ---
                Integer minVal = ruleDto.getMin() != null ? ruleDto.getMin() : 0;
                Integer maxVal = ruleDto.getMax() != null ? ruleDto.getMax() : 250;
                if (Boolean.FALSE.equals(ruleDto.getEditableByRequester())) {
                    maxVal = 0;
                }
                // --- Fin de la logique métier ---

                ruleEntity.setSanType(ruleDto.getType());
                ruleEntity.setMinValue(minVal);
                ruleEntity.setMaxValue(maxVal);
                ruleEntity.setEditableByRequester(ruleDto.getEditableByRequester());
                
                // C'est crucial : on lie la règle à son profil parent.
                // Cela va remplir la colonne de clé étrangère PROFILE_ID.
                ruleEntity.setCertificateProfile(profileEntity);
                newRules.add(ruleEntity);
            }
        }
        
        // On remplace complètement l'ancienne collection de règles par la nouvelle.
        // `orphanRemoval=true` dans `CertificateProfileEntity` s'assurera que les anciennes règles
        // qui ne sont plus dans la collection seront supprimées de la base de données.
        profileEntity.getSanTypeRules().clear();
        profileEntity.getSanTypeRules().addAll(newRules);
        
        // JPA est intelligent. Grâce à @Transactional, il sauvegardera automatiquement les changements
        // à la fin de la méthode. Mais un `save` explicite ne fait pas de mal et rend le code plus clair.
        certificateProfileRepository.save(profileEntity);
        LOGGER.debug("Profil '{}' sauvegardé/mis à jour avec {} règles.", profileName, profileEntity.getSanTypeRules().size());
    }

    /**
     * Méthode utilitaire pour gérer les profils qui existent dans notre base
     * mais qui ne sont plus retournés par l'API. Au lieu de les supprimer,
     * il est plus sûr de simplement les marquer comme inactifs.
     * @param activeProfileNamesFromApi Le Set des noms de profils qui sont actuellement valides selon l'API.
     */
    private void deactivateOrphanedProfiles(Set<String> activeProfileNamesFromApi) {
        LOGGER.debug("Recherche des profils à désactiver...");
        
        // On récupère tous les profils de notre base.
        List<CertificateProfileEntity> allProfilesInDb = certificateProfileRepository.findAll();
        
        for (CertificateProfileEntity profileInDb : allProfilesInDb) {
            // Si un profil de notre base n'est PAS dans la liste de l'API ET qu'il est actuellement actif...
            if (!activeProfileNamesFromApi.contains(profileInDb.getProfileName()) && profileInDb.isActive()) {
                // ...alors on le désactive.
                LOGGER.info("Le profil '{}' n'est plus retourné par l'API. Il va être désactivé.", profileInDb.getProfileName());
                profileInDb.setActive(false);
                certificateProfileRepository.save(profileInDb); // On sauvegarde le changement de statut.
            }
        }
    }
}
//////////////////// erreur////////////////////
Oui, c'est une autre erreur très instructive ! Vous avez bien fait de créer un @Bean pour tester le service au démarrage, c'est une excellente technique de débogage.
Diagnostic de l'Erreur
L'erreur est très claire et se trouve à la fin des logs :
org.springframework.web.client.ResourceAccessException: I/O error on GET request for "https://automation.staging.echonet.net.intra/api/v1/certificate/profiles": automation.staging.echonet.net.intra; nested exception is java.net.UnknownHostException: automation.staging.echonet.net.intra
Traduction en français simple :
C'est une erreur réseau. Votre application a essayé de contacter le serveur automation.staging.echonet.net.intra, mais votre ordinateur n'a pas réussi à trouver son adresse IP.
L'exception java.net.UnknownHostException est la cause racine et signifie littéralement "Hôte inconnu".
Analogie : C'est comme si vous tapiez une adresse dans votre GPS, et le GPS vous répondait : "Je ne connais pas cette rue, elle n'existe pas dans ma carte". Votre ordinateur demande à son "annuaire" (le système DNS) : "Quelle est l'adresse IP de automation.staging.echonet.net.intra ?", et l'annuaire répond : "Je ne connais pas ce nom".
Pourquoi cette erreur se produit-elle ?
Le nom de domaine se termine par .intra, ce qui indique qu'il s'agit d'un serveur sur le réseau interne de votre entreprise. Il n'est pas accessible depuis l'internet public.
Les causes les plus probables sont :
Vous n'êtes pas connecté au réseau de l'entreprise (Cause la plus fréquente). Si vous travaillez de chez vous ou d'un autre endroit, vous devez être connecté via un VPN pour que votre ordinateur puisse accéder aux ressources internes.
Problème de Proxy. De nombreuses entreprises exigent que le trafic passe par un proxy. Votre application Java (et donc le RestTemplate) doit peut-être être configurée pour utiliser ce proxy.
Pare-feu (Firewall). Un pare-feu sur votre machine ou sur le réseau de l'entreprise pourrait bloquer la connexion.
Faute de frappe dans l'URL. Il est toujours bon de vérifier si l'URL dans votre fichier application.properties est parfaitement correcte.
Solutions à Essayer (dans l'ordre)
1. Vérifiez votre connexion VPN (Le plus probable)
Êtes-vous bien connecté au VPN de votre entreprise ? Si ce n'est pas le cas, connectez-vous et relancez l'application. C'est la solution dans 95% des cas pour ce type d'erreur.
2. Testez la connectivité manuellement
Ouvrez une ligne de commande (Terminal, PowerShell, CMD) et essayez de "pinger" le serveur :
code
Bash
ping automation.staging.echonet.net.intra
Si la commande ping répond "hôte inconnu" ou "ping request could not find host", cela confirme que le problème est bien lié à votre connectivité réseau (VPN, etc.) et non à votre code Java.
3. Configurez le Proxy (si votre entreprise en utilise un)
Si votre entreprise utilise un proxy, vous devez le spécifier au démarrage de votre application Java. Vous pouvez le faire en ajoutant des arguments à la JVM dans votre configuration de lancement IntelliJ :
Allez dans "Edit Configurations..."
Dans le champ "VM options", ajoutez :
code
Code
-Dhttps.proxyHost=proxy.votre-entreprise.com -Dhttps.proxyPort=8080
(Remplacez l'hôte et le port par ceux de votre entreprise).
Solution de Contournement pour le Développement (Très Utile !)
Pour pouvoir continuer à développer même si vous n'avez pas accès au réseau, vous pouvez temporairement "mocker" (simuler) la réponse de l'API.
Modifiez votre CertisApp pour créer un RestTemplate "bidon" lorsque vous êtes dans un profil de développement.
Ouvrez votre fichier application.properties et ajoutez cette ligne :
code
Properties
spring.profiles.active=dev
Créez une nouvelle classe de configuration pour le développement :
code
Java
// Dans un package de configuration
package com.bnpparibas.certis.config;

import com.bnpparibas.certis.automationhub.dto.external.ExternalProfileDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Configuration
@Profile("dev") // Cette configuration ne sera active que si le profil "dev" est activé
public class DevRestTemplateConfig {

    @Bean
    public RestTemplate automationHubRestTemplate() {
        System.out.println("--- ATTENTION : UTILISATION D'UN RESTTEMPLATE MOCKÉ POUR LE DÉVELOPPEMENT ---");
        
        // On crée un mock de RestTemplate
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        
        // On crée une fausse réponse
        ExternalProfileDto[] fakeResponse = new ExternalProfileDto[1];
        ExternalProfileDto fakeProfile = new ExternalProfileDto();
        fakeProfile.setName("PROFIL_DE_TEST_MOCKE");
        // Ajoutez quelques règles de SANs si vous voulez tester la logique de sauvegarde
        fakeResponse[0] = fakeProfile;

        // On dit au mock : "quand on t'appelle avec cette URL, retourne la fausse réponse"
        when(mockRestTemplate.getForObject(any(String.class), eq(ExternalProfileDto[].class)))
            .thenReturn(fakeResponse);
            
        return mockRestTemplate;
    }
}
Avec cette configuration, lorsque vous lancerez votre application avec le profil "dev", elle n'essaiera plus de contacter le vrai serveur. Elle utilisera le RestTemplate mocké qui retourne des données de test, ce qui vous permettra de travailler sur le reste de votre code sans être bloqué par des problèmes de réseau.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.

////////////////
spring.profiles.active=dev
/////////////////// sql /////////////////////////
Requêtes SQL pour Oracle SQL Developer
Voici les deux scripts à exécuter. Je vous recommande d'exécuter celui pour CERTIFICATE_PROFILE en premier, car SAN_TYPE_RULE en dépend.
1. Création de la table CERTIFICATE_PROFILE
code
SQL
-- Suppression de la table si elle existe déjà, pour pouvoir relancer le script facilement
DROP TABLE CERTIFICATE_PROFILE CASCADE CONSTRAINTS;

-- Création de la table pour stocker les profils
CREATE TABLE CERTIFICATE_PROFILE (
    -- Clé primaire technique
    ID NUMBER(19,0) GENERATED BY DEFAULT ON NULL AS IDENTITY PRIMARY KEY,

    -- Nom du profil (unique et obligatoire)
    PROFILE_NAME VARCHAR2(255 CHAR) NOT NULL UNIQUE,
    
    -- Statut d'activité du profil (1 pour true, 0 pour false)
    IS_ACTIVE NUMBER(1,0) DEFAULT 1 NOT NULL CHECK (IS_ACTIVE IN (0,1))
);

-- Ajout de commentaires pour la documentation de la base de données
COMMENT ON TABLE CERTIFICATE_PROFILE IS 'Stocke les profils de certificats (ex: SSL_SRVR) et leur statut.';
COMMENT ON COLUMN CERTIFICATE_PROFILE.ID IS 'Identifiant technique unique de la table.';
COMMENT ON COLUMN CERTIFICATE_PROFILE.PROFILE_NAME IS 'Nom unique et fonctionnel du profil de certificat.';
COMMENT ON COLUMN CERTIFICATE_PROFILE.IS_ACTIVE IS 'Indicateur d''activité du profil (1 = actif, 0 = inactif).';
2. Création de la table SAN_TYPE_RULE
code
SQL
-- Suppression de la table si elle existe déjà
DROP TABLE SAN_TYPE_RULE CASCADE CONSTRAINTS;

-- Création de la table pour stocker les règles de SAN par profil
CREATE TABLE SAN_TYPE_RULE (
    -- Clé primaire technique
    ID NUMBER(19,0) GENERATED BY DEFAULT ON NULL AS IDENTITY PRIMARY KEY,
    
    -- Clé étrangère vers la table des profils (obligatoire)
    PROFILE_ID NUMBER(19,0) NOT NULL,
    
    -- Le type de SAN concerné par la règle (ex: CN, DNSNAME)
    SAN_TYPE_ENUM VARCHAR2(255 CHAR) NOT NULL,
    
    -- Valeur minimale requise
    MIN_VALUE NUMBER(10,0) NOT NULL,
    
    -- Valeur maximale autorisée
    MAX_VALUE NUMBER(10,0) NOT NULL,
    
    -- Contrainte de clé étrangère qui lie cette table à CERTIFICATE_PROFILE
    CONSTRAINT FK_SANRULE_TO_PROFILE 
        FOREIGN KEY (PROFILE_ID) 
        REFERENCES CERTIFICATE_PROFILE (ID) 
        ON DELETE CASCADE, -- Si on supprime un profil, ses règles partent avec

    -- Contrainte d'unicité pour s'assurer qu'il n'y a qu'une seule règle
    -- pour un type de SAN donné au sein d'un même profil.
    CONSTRAINT UQ_PROFILE_ID_AND_SAN_TYPE 
        UNIQUE (PROFILE_ID, SAN_TYPE_ENUM)
);

-- Ajout de commentaires
COMMENT ON TABLE SAN_TYPE_RULE IS 'Stocke les règles de validation (min/max) pour chaque type de SAN associé à un profil.';
COMMENT ON COLUMN SAN_TYPE_RULE.ID IS 'Identifiant technique unique de la table.';
COMMENT ON COLUMN SAN_TYPE_RULE.PROFILE_ID IS 'Référence vers l''ID du profil parent dans la table CERTIFICATE_PROFILE.';
COMMENT ON COLUMN SAN_TYPE_RULE.SAN_TYPE_ENUM IS 'Le type de SAN (Subject Alternative Name) concerné par la règle.';
COMMENT ON COLUMN SAN_TYPE_RULE.MIN_VALUE IS 'Le nombre minimum de SANs de ce type requis pour ce profil.';
COMMENT ON COLUMN SAN_TYPE_RULE.MAX_VALUE IS 'Le nombre maximum de SANs de ce type autorisé pour ce profil.';
Comment utiliser ces scripts dans Oracle SQL Developer
Ouvrez une nouvelle "Feuille de calcul" (Query Worksheet) pour votre connexion CERTIS_QUAL.
Copiez-collez le premier bloc de code (CREATE TABLE CERTIFICATE_PROFILE).
Sélectionnez tout le bloc de code et cliquez sur l'icône "Exécuter le script" (la feuille de papier avec une flèche verte) ou appuyez sur F5.
Une fois que c'est fait, copiez-collez le deuxième bloc de code (CREATE TABLE SAN_TYPE_RULE).
Sélectionnez ce deuxième bloc et exécutez-le de la même manière.
Après ces deux étapes, vos tables seront créées dans la base de données CERTIS_QUAL, prêtes à être utilisées par votre application. Vous pouvez vérifier leur existence en rafraîchissant le noeud "Tables" dans l'explorateur de connexions sur la gauche.
/////////////////////////
La Solution : Corriger vos Classes DTO
Pour que la désérialisation fonctionne, vos classes DTO doivent être un miroir parfait des champs JSON qui vous intéressent. Voici le code exact et complet pour vos deux DTOs, qui fonctionnera avec le JSON que vous avez montré.
Fichier 1 : ExternalProfileDto.java (Le DTO pour un profil)
C'est très probablement dans ce fichier que se trouve l'erreur. Assurez-vous qu'il ressemble exactement à ceci.
code
Java
package com.bnpparibas.certis.automationhub.dto.external; // Assurez-vous que le package est correct

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Ignore les nombreux champs du JSON que nous ne voulons pas mapper.
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalProfileDto {

    // On mappe le champ JSON "name" à notre variable Java "name"
    @JsonProperty("name")
    private String name;

    // C'EST LA PARTIE LA PLUS IMPORTANTE
    // On mappe explicitement le champ JSON "sans" à notre variable Java "sans"
    @JsonProperty("sans")
    private List<ExternalSanRuleDto> sans;

    // --- Getters et Setters ---
    // Ils sont OBLIGATOIRES pour que Jackson puisse fonctionner.

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ExternalSanRuleDto> getSans() {
        return sans;
    }

    // Ce setter est CRUCIAL. Sans lui, la liste "sans" sera toujours null.
    public void setSans(List<ExternalSanRuleDto> sans) {
        this.sans = sans;
    }
}
Fichier 2 : ExternalSanRuleDto.java (Le DTO pour une règle de SAN)
Assurez-vous également que ce fichier est correct, avec tous ses getters et setters.
code
Java
package com.bnpparibas.certis.automationhub.dto.external; // Assurez-vous que le package est correct

import com.bnpparibas.certis.certificate.request.dto.SanTypeEnum; // L'Enum que vous utilisez déjà
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalSanRuleDto {

    @JsonProperty("type")
    private SanTypeEnum type;

    @JsonProperty("editableByRequester")
    private Boolean editableByRequester;

    @JsonProperty("min")
    private Integer min;

    @JsonProperty("max")
    private Integer max;

    // --- Getters et Setters OBLIGATOIRES ---

    public SanTypeEnum getType() {
        return type;
    }

    public void setType(SanTypeEnum type) {
        this.type = type;
    }

    public Boolean getEditableByRequester() {
        return editableByRequester;
    }

    public void setEditableByRequester(Boolean editableByRequester) {
        this.editableByRequester = editableByRequester;
    }

    public Integer getMin() {
        return min;
    }

    public void setMin(Integer min) {
        this.min = min;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }

    @Override
    public String toString() {
        // Un toString() est très utile pour le débogage !
        return "ExternalSanRuleDto{" +
                "type=" + type +
                ", min=" + min +
                ", max=" + max +
                '}';
    }
}
Étapes à suivre
Vérifiez et remplacez le contenu de vos deux fichiers DTO (ExternalProfileDto.java et ExternalSanRuleDto.java) par le code que je viens de vous fournir. Faites particulièrement attention au setter setSans dans ExternalProfileDto.
Relancez votre application.
Regardez la sortie de votre console. Votre System.out.println("Test 1:" + fromApi); ne devrait plus afficher [null, null, null, ...]. Il devrait maintenant afficher la vraie liste des règles de SANs, quelque chose comme :
code
Code
Test 1:[[ExternalSanRuleDto{type=DNSNAME, min=0, max=3}, ExternalSanRuleDto{type=IPADDRESS, min=0, max=3}], [ExternalSanRuleDto{type=CN, min=1, max=1}], ...]
(grâce au toString() que nous avons ajouté).
C'est un problème de mapping très classique lorsqu'on interagit avec des APIs externes. Une fois que vos DTOs sont correctement alignés avec le JSON, tout fonctionnera parfaitement.
/////////////////////
Supprimez l'interface ExternalSanRuleDto.java.
Renommez votre classe d'implémentation ExternalSanRuleDtoImpl.java en ExternalSanRuleDto.java.
Modifiez sa déclaration pour qu'elle n'implémente plus rien :
code
Java
// Fichier renommé : ExternalSanRuleDto.java

// On enlève "implements ExternalSanRuleDto"
public class ExternalSanRuleDto implements Serializable { // Juste Serializable, c'est bien

    // ... tous vos champs, @JsonProperty, getters et setters ...

}
Faites de même pour ExternalProfileDto s'il suit le même modèle (interface + impl).
En faisant cela, vous simplifiez énormément votre code. Quand Jackson verra List<ExternalSanRuleDto>, il saura qu'il doit créer des instances de la classe ExternalSanRuleDto, et tout fonctionnera parfaitement.
Solution B : Utiliser des annotations Jackson pour lier l'interface à l'implémentation (Plus complexe)
Si, pour une raison très spécifique, vous devez absolument conserver l'interface, vous devez dire explicitement à Jackson quelle classe utiliser pour l'implémentation.
Dans votre interface ExternalSanRuleDto.java :
code
Java
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = ExternalSanRuleDtoImpl.class)
public interface ExternalSanRuleDto {
    // ... signatures des getters ...
}
L'annotation @JsonDeserialize(as = ...) dit à Jackson : "Quand tu as besoin de créer un objet de ce type d'interface, utilise cette classe concrète."
Dans votre classe ExternalSanRuleDtoImpl.java :
Assurez-vous qu'elle implémente bien l'interface et qu'elle a un constructeur par défaut.
Conclusion :
Je vous recommande très fortement la Solution A. Elle est plus simple, plus lisible et suit les conventions standard pour les DTOs. La complexité d'une interface n'est pas justifiée ici et c'est la source de votre problème de désérialisation.
///////////////////////////// revu ticket ////////////////////////////////////////////
La Solution Complète
Voici un plan d'action qui s'intègre à votre projet existant et résout le ticket de manière propre et robuste.
Étape 1 : Le Vrai Point de Départ : AUTOMATIONHUB_PROFILE
Le scheduler ne devrait pas appeler l'API directement. Il devrait d'abord lire la liste des profils qui existent déjà dans votre table AUTOMATIONHUB_PROFILE et ensuite, pour chacun d'eux, aller chercher les détails sur l'API d'Horizon.
Étape 2 : Adapter le Service
Votre CertificateProfileService (que nous avons créé) est parfait, mais il est un peu redondant avec le service existant AutomationHubProfileService. Nous allons fusionner les logiques.
La nouvelle logique sera dans AutomationHubProfileServiceImpl (le service existant).
Modifications de Code Détaillées
1. Supprimer la nouvelle classe CertificateProfileService.java
Nous allons intégrer sa logique dans le service existant. Vous pouvez supprimer cette classe et son interface pour ne pas créer de confusion.
2. Modifier la Tâche SyncSanProfilesTask.java (le Scheduler)
Le scheduler va maintenant utiliser le service AutomationHubProfileService existant pour obtenir la liste des profils à synchroniser.
code
Java
package com.bnpparibas.certis.api.tasks;

import com.bnpparibas.certis.automationhub.service.AutomationHubProfileService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncSanProfilesTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncSanProfilesTask.class);
    
    // On injecte le service existant qui gère les profils de l'Automation Hub
    private final AutomationHubProfileService automationHubProfileService;

    public SyncSanProfilesTask(AutomationHubProfileService automationHubProfileService) {
        this.automationHubProfileService = automationHubProfileService;
    }

    /**
     * Tâche planifiée qui déclenche la mise à jour des règles de SANs pour tous les profils
     * connus dans la base de données locale (table AUTOMATIONHUB_PROFILE).
     */
    @Scheduled(cron = "${certis.profiles.sync-cron:0 0 2 * * ?}")
    @SchedulerLock(name = "SyncSanProfilesTask_lock")
    public void runTask() {
        LOGGER.info("Début de la tâche planifiée de synchronisation des règles de SANs.");
        try {
            // Le scheduler demande au service de tout synchroniser.
            automationHubProfileService.syncAllSanRulesFromHorizonApi();
            
            LOGGER.info("Tâche de synchronisation des règles de SANs terminée avec succès.");
        } catch (Exception e) {
            LOGGER.error("Une erreur critique est survenue durant la tâche de synchronisation des règles de SANs.", e);
        }
    }
}
3. Enrichir le Service AutomationHubProfileServiceImpl.java (Le Cœur de la Solution)
C'est ici que nous allons ajouter la nouvelle logique. On va ajouter une nouvelle méthode publique syncAllSanRulesFromHorizonApi et une méthode privée pour traiter chaque profil.
code
Java
// Dans le fichier AutomationHubProfileServiceImpl.java

// ... autres imports existants ...
import com.bnpparibas.certis.automationhub.dto.external.ExternalProfileDto;
import com.bnpparibas.certis.automationhub.dto.external.ExternalSanRuleDto;
import com.bnpparibas.certis.certificate.request.model.CertificateProfile; // Votre nouvelle entité
import com.bnpparibas.certis.certificate.request.model.SanTypeRule;     // Votre nouvelle entité
import com.bnpparibas.certis.certificate.request.repository.CertificateProfileRepository; // Le nouveau repository
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Set;
import java.util.HashSet;


@Service
@RequiredArgsConstructor // Si vous utilisez Lombok, sinon créez le constructeur
public class AutomationHubProfileServiceImpl implements AutomationHubProfileService {

    // --- Champs existants ---
    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationHubProfileServiceImpl.class);
    private final CertisTypeToAutomationHubProfileDao certisTypeToAutomationHubProfileDao;
    private final AutomationHubProfileMapper automationHubProfileMapper;
    
    // --- NOUVEAUX Champs à ajouter ---
    @Qualifier("automationHubRestTemplate")
    private final RestTemplate restTemplate;
    
    private final CertificateProfileRepository certificateProfileRepository; // Le repository pour la NOUVELLE table

    @Value("${external.api.url.profiles}") // La même propriété que nous avons définie
    private String horizonApiBaseUrl;


    // --- Méthodes existantes (getProfileByTypeAndSubType, etc.) restent inchangées ---
    // ...

    // --- NOUVELLE Méthode Publique pour le Scheduler ---
    @Override
    @Transactional
    public void syncAllSanRulesFromHorizonApi() {
        LOGGER.info("Début de la synchronisation des règles de SANs depuis Horizon.");

        // 1. On récupère la liste des profils à traiter depuis NOTRE base de données.
        List<AutomationHubProfile> profilesToSync = certisTypeToAutomationHubProfileDao.findAll();
        
        if (profilesToSync.isEmpty()) {
            LOGGER.warn("Aucun profil trouvé dans la table AUTOMATIONHUB_PROFILE. La synchronisation est annulée.");
            return;
        }
        
        LOGGER.info("{} profils vont être traités.", profilesToSync.size());

        // 2. Pour chaque profil de notre base, on va chercher les détails sur l'API Horizon.
        for (AutomationHubProfile internalProfile : profilesToSync) {
            try {
                processSingleProfile(internalProfile);
            } catch (Exception e) {
                // On log l'erreur pour un profil mais on continue avec les autres.
                LOGGER.error("Échec de la synchronisation pour le profil '{}'. Cause: {}", internalProfile.getProfileName(), e.getMessage());
            }
        }
    }

    // --- NOUVELLE Méthode Privée pour la logique de traitement ---
    private void processSingleProfile(AutomationHubProfile internalProfile) {
        String profileName = internalProfile.getProfileName();
        String apiUrl = horizonApiBaseUrl + "/" + profileName;

        LOGGER.info("Récupération des règles pour le profil '{}' depuis l'URL: {}", profileName, apiUrl);

        // On appelle l'API pour UN seul profil. La réponse est un objet, pas un tableau.
        ExternalProfileDto horizonProfileData = restTemplate.getForObject(apiUrl, ExternalProfileDto.class);

        if (horizonProfileData == null) {
            LOGGER.warn("Aucune donnée retournée par Horizon pour le profil '{}'.", profileName);
            return;
        }

        // On récupère ou on crée l'entité dans notre NOUVELLE table CERTIFICATE_PROFILE
        CertificateProfile sanProfile = certificateProfileRepository.findByProfileName(profileName)
            .orElseGet(() -> {
                LOGGER.info("Création d'une nouvelle entrée dans CERTIFICATE_PROFILE pour '{}'", profileName);
                CertificateProfile newProfile = new CertificateProfile();
                newProfile.setProfileName(profileName);
                return newProfile;
            });

        sanProfile.setActive(true); // On s'assure qu'il est actif
        
        // On remplace les anciennes règles par les nouvelles
        sanProfile.getSanTypeRules().clear();
        Set<SanTypeRule> newRules = new HashSet<>();

        if (horizonProfileData.getSans() != null) {
            for (ExternalSanRuleDto ruleDto : horizonProfileData.getSans()) {
                SanTypeRule ruleEntity = new SanTypeRule();

                // Logique du ticket Jira (min/max par défaut, etc.)
                Integer minVal = ruleDto.getMin() != null ? ruleDto.getMin() : 0;
                Integer maxVal = ruleDto.getMax() != null ? ruleDto.getMax() : 250;
                if (Boolean.FALSE.equals(ruleDto.getEditableByRequester())) {
                    maxVal = 0;
                }

                ruleEntity.setSanTypeEnum(ruleDto.getType()); // J'ai vu que vous aviez renommé le champ
                ruleEntity.setMinValue(minVal);
                ruleEntity.setMaxValue(maxVal);
                
                ruleEntity.setCertificateProfile(sanProfile); // On lie la règle à son parent
                newRules.add(ruleEntity);
            }
        }
        
        sanProfile.getSanTypeRules().addAll(newRules);
        
        // On sauvegarde l'entité dans la nouvelle table
        certificateProfileRepository.save(sanProfile);
        LOGGER.info("Profil '{}' mis à jour avec {} règles de SANs.", profileName, newRules.size());
    }
}
4. Interface AutomationHubProfileService.java
N'oubliez pas d'ajouter la signature de la nouvelle méthode à l'interface.
code
Java
// Dans le fichier AutomationHubProfileService.java

public interface AutomationHubProfileService {
    // ... méthodes existantes ...

    /**
     * Lance la synchronisation complète des règles de SANs depuis l'API Horizon
     * pour tous les profils connus en base de données.
     */
    void syncAllSanRulesFromHorizonApi();
}
Résumé de cette Solution
Réutilisation Maximale : On s'appuie sur les services, DAOs et entités qui existent déjà dans votre projet.
Source de Vérité Interne : Le point de départ est votre table AUTOMATIONHUB_PROFILE, ce qui est logique et robuste.
Enrichissement des Données : On "enrichit" les profils de votre base avec les règles de SANs récupérées depuis l'API externe, et on stocke ce résultat dans les nouvelles tables CERTIFICATE_PROFILE et SAN_TYPE_RULE.
Logique Centralisée : Toute la nouvelle logique est ajoutée au service AutomationHubProfileServiceImpl, qui est le bon endroit pour cela.
Simplicité du Scheduler : Le scheduler reste très simple, son seul rôle est de déclencher le processus.
Cette approche est beaucoup plus intégrée et respectueuse de l'architecture de votre projet.
//////////////////////////////////////////
Étape 1 : Créer ou Modifier la classe AutomationHubClient.java
Cette classe sera responsable de tous les appels HTTP vers l'API d'Horizon.
Emplacement : ...certis.automationhub.client (comme sur votre capture d'écran)
code
Java
package com.bnpparibas.certis.automationhub.client;

import com.bnpparibas.certis.automationhub.dto.external.ExternalProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Client HTTP pour communiquer avec l'API d'Horizon (Automation Hub).
 * Centralise tous les appels réseau vers cette API.
 */
@Component // On déclare cette classe comme un bean Spring
public class AutomationHubClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationHubClient.class);

    private final RestTemplate restTemplate;

    @Value("${external.api.url.profiles}")
    private String horizonApiBaseUrl;
    
    // Le constructeur est parfait comme sur votre image, on l'utilise pour l'injection
    public AutomationHubClient(@Qualifier("automationHubRestTemplate") RestTemplate restTemplate,
                               /* ... injectez ici les autres dépendances dont ce client pourrait avoir besoin,
                                      mais PAS les services ou repositories métier ... */ ) {
        this.restTemplate = restTemplate;
    }

    /**
     * NOUVELLE MÉTHODE
     * Récupère les détails d'un profil spécifique depuis l'API d'Horizon.
     * 
     * @param profileName Le nom du profil à récupérer.
     * @return Un ExternalProfileDto contenant les données du profil, ou null si le profil n'est pas trouvé ou en cas d'erreur.
     */
    public ExternalProfileDto fetchProfileDetailsFromHorizon(String profileName) {
        String apiUrl = horizonApiBaseUrl + "/" + profileName;
        
        LOGGER.info("Appel de l'API Horizon pour le profil '{}' sur l'URL: {}", profileName, apiUrl);
        
        try {
            // On appelle l'API pour UN seul profil. La réponse est un objet, pas un tableau.
            return restTemplate.getForObject(apiUrl, ExternalProfileDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            // L'API a retourné une erreur 404 (Not Found), ce qui est une information métier !
            LOGGER.warn("Le profil '{}' n'a pas été trouvé dans Horizon (erreur 404).", profileName);
            return null; // On retourne null pour indiquer que le profil n'existe pas
        } catch (Exception e) {
            // Pour toutes les autres erreurs (réseau, serveur indisponible...), on logue une erreur et on propage l'exception
            LOGGER.error("Erreur lors de l'appel à l'API Horizon pour le profil '{}'", profileName, e);
            // On pourrait relancer une exception personnalisée ici
            throw e; 
        }
    }
    
    // ... autres méthodes du client si vous en avez (ex: pour révoquer un certificat, etc.)
}
Étape 2 : Simplifier le Service AutomationHubProfileServiceImpl.java
Maintenant, le service n'a plus besoin de connaître RestTemplate ou l'URL de l'API. Son rôle est purement métier : orchestrer la logique de synchronisation en utilisant le AutomationHubClient pour obtenir les données.
code
Java
// Dans le fichier AutomationHubProfileServiceImpl.java

// ... imports ...
import com.bnpparibas.certis.automationhub.client.AutomationHubClient; // <-- NOUVEL IMPORT

@Service
@RequiredArgsConstructor
public class AutomationHubProfileServiceImpl implements AutomationHubProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationHubProfileServiceImpl.class);

    // --- Anciennes dépendances à SUPPRIMER ---
    // @Qualifier("automationHubRestTemplate")
    // private final RestTemplate restTemplate;
    // @Value("${external.api.url.profiles}") 
    // private String horizonApiBaseUrl;

    // --- NOUVELLE Dépendance ---
    private final AutomationHubClient automationHubClient;

    // --- Dépendances métier existantes (à conserver) ---
    private final CertisTypeToAutomationHubProfileDao certisTypeToAutomationHubProfileDao;
    private final CertificateProfileRepository certificateProfileRepository;
    private final AutomationHubProfileMapper automationHubProfileMapper;
    
    // Assurez-vous que votre constructeur injecte bien le nouveau AutomationHubClient !

    @Override
    @Transactional
    public void syncAllSanRulesFromHorizonApi() {
        LOGGER.info("Début de la synchronisation des règles de SANs depuis Horizon.");

        List<AutomationHubProfile> profilesToSync = certisTypeToAutomationHubProfileDao.findAll();
        
        if (profilesToSync.isEmpty()) {
            LOGGER.warn("Aucun profil trouvé dans la table AUTOMATIONHUB_PROFILE. La synchronisation est annulée.");
            return;
        }
        
        LOGGER.info("{} profils vont être traités.", profilesToSync.size());

        for (AutomationHubProfile internalProfile : profilesToSync) {
            try {
                // On appelle la méthode privée pour traiter chaque profil
                processSingleProfile(internalProfile);
            } catch (Exception e) {
                LOGGER.error("Échec de la synchronisation pour le profil '{}'. Cause: {}", internalProfile.getProfileName(), e.getMessage());
            }
        }
    }

    /**
     * La méthode privée est maintenant beaucoup plus simple.
     * Elle délègue l'appel réseau au AutomationHubClient.
     */
    private void processSingleProfile(AutomationHubProfile internalProfile) {
        String profileName = internalProfile.getProfileName();

        // ÉTAPE 1 : OBTENIR LES DONNÉES
        // On appelle notre nouveau client pour récupérer les informations d'Horizon
        ExternalProfileDto horizonProfileData = automationHubClient.fetchProfileDetailsFromHorizon(profileName);

        // Si le client retourne null (ex: 404), on arrête le traitement pour ce profil
        if (horizonProfileData == null) {
            LOGGER.warn("Aucune donnée disponible sur Horizon pour le profil '{}'. Ce profil sera ignoré.", profileName);
            // Optionnel : vous pourriez vouloir désactiver le profil ici
            // deactivateProfile(profileName);
            return;
        }

        // ÉTAPE 2 : LOGIQUE MÉTIER DE SAUVEGARDE (ce code ne change pas)
        CertificateProfile sanProfile = certificateProfileRepository.findByProfileName(profileName)
            .orElseGet(() -> {
                LOGGER.info("Création d'une nouvelle entrée dans CERTIFICATE_PROFILE pour '{}'", profileName);
                CertificateProfile newProfile = new CertificateProfile();
                newProfile.setProfileName(profileName);
                return newProfile;
            });

        sanProfile.setActive(true);
        
        Set<SanTypeRule> newRules = new HashSet<>();
        if (horizonProfileData.getSans() != null) {
            for (ExternalSanRuleDto ruleDto : horizonProfileData.getSans()) {
                SanTypeRule ruleEntity = new SanTypeRule();

                Integer minVal = ruleDto.getMin() != null ? ruleDto.getMin() : 0;
                Integer maxVal = ruleDto.getMax() != null ? ruleDto.getMax() : 250;
                if (Boolean.FALSE.equals(ruleDto.getEditableByRequester())) {
                    maxVal = 0;
                }

                ruleEntity.setSanTypeEnum(ruleDto.getType());
                ruleEntity.setMinValue(minVal);
                ruleEntity.setMaxValue(maxVal);
                
                ruleEntity.setCertificateProfile(sanProfile);
                newRules.add(ruleEntity);
            }
        }
        
        sanProfile.getSanTypeRules().clear();
        sanProfile.getSanTypeRules().addAll(newRules);
        
        certificateProfileRepository.save(sanProfile);
        LOGGER.info("Profil '{}' mis à jour avec {} règles de SANs.", profileName, newRules.size());
    }
}
Avantages de cette nouvelle architecture
Séparation Claire des Responsabilités :
AutomationHubClient : Gère le "comment" on parle à l'API (HTTP, JSON, URLs, erreurs réseau).
AutomationHubProfileServiceImpl : Gère le "quoi" on fait avec les données (logique métier, interaction avec la base de données).
Testabilité Améliorée : Il est maintenant très facile de tester votre service. Il suffit de "mocker" le AutomationHubClient et de lui faire retourner les données que vous voulez, sans avoir besoin de simuler un RestTemplate.
Réutilisabilité : Si un autre service a besoin de récupérer des informations de profil depuis Horizon, il peut maintenant réutiliser AutomationHubClient sans avoir à connaître les détails de l'API.
///////////////////////// resumer final //////////////////////////////////

Model
Absolument. Voici le code complet de la classe SanServiceImpl avec la méthode verifySansLimitsDynamically intégrée.
Cette classe utilise les règles que AutomationHubProfileServiceImpl a sauvegardées pour valider les nouvelles demandes de certificats.
Fichier : SanServiceImpl.java (Version finale)
Rôle : Valider les demandes de certificats entrants en se basant sur les règles dynamiques stockées en base de données.
Emplacement : ...certis.certificate.request.service.impl
code
Java
package com.bnpparibas.certis.certificate.request.service.impl;

// --- Imports existants ---
import com.bnpparibas.certis.certificate.request.dto.RequestDto;
import com.bnpparibas.certis.certificate.request.dto.San;
import com.bnpparibas.certis.certificate.request.dto.SanTypeEnum;
import com.bnpparibas.certis.certificate.request.service.SanService;
import com.bnpparibas.certis.exception.CertisRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

// --- NOUVEAUX Imports ---
import com.bnpparibas.certis.automationhub.model.AutomationHubProfile; // L'entité existante que vous utilisez
import com.bnpparibas.certis.automationhub.model.SanTypeRule;         // Votre nouvelle entité de règles
import com.bnpparibas.certis.automationhub.dao.AutomationHubProfileDao; // Le DAO/Repository pour l'entité de profil existante

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SanServiceImpl implements SanService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SanServiceImpl.class);

    // --- NOUVELLE Dépendance ---
    // On a besoin du DAO/Repository pour accéder aux profils et à leurs règles.
    // D'après vos images, c'est AutomationHubProfileDao qui est utilisé.
    private final AutomationHubProfileDao automationHubProfileDao;

    // --- Conservez vos autres dépendances existantes si nécessaire ---
    // private final UrlRacineService urlRacineService;

    /**
     * Constructeur pour l'injection de dépendances.
     * @param automationHubProfileDao Le DAO pour accéder aux profils et à leurs règles.
     */
    public SanServiceImpl(AutomationHubProfileDao automationHubProfileDao /*, ... autres dépendances */) {
        this.automationHubProfileDao = automationHubProfileDao;
        // ...
    }

    // =======================================================================================
    // --- À SUPPRIMER ---
    // Supprimez toutes les anciennes constantes de limites (EXT_SSL_LIMIT, INT_SSL_SRVR_LIMIT, etc.)
    // et les anciennes méthodes de validation des limites (verifySansLimitForInternalCertificates, etc.).
    // =======================================================================================

    /**
     * Méthode principale de validation des SANs pour une requête donnée.
     * C'est le point d'entrée appelé par d'autres services (comme RequestServiceImpl).
     */
    @Override
    public void validateSansPerRequest(RequestDto requestDto) throws CertisRequestException {
        // Conservez cette vérification si vous l'avez
        if (this.skipValidationIfDataMissing(requestDto)) {
            return;
        }

        // --- DÉBUT DE LA LOGIQUE DE VALIDATION DYNAMIQUE ---

        // On appelle la nouvelle méthode qui contient toute la logique.
        this.verifySansLimitsDynamically(requestDto);

        // --- FIN DE LA LOGIQUE DE VALIDATION DYNAMIQUE ---

        // Conservez les autres validations qui ne sont pas liées aux limites min/max.
        // Par exemple, la validation du format des SANs.
        // this.verifySanFormats(requestDto); 
    }

    /**
     * NOUVELLE méthode qui valide une requête par rapport aux règles dynamiques
     * stockées dans les tables AUTOMATIONHUB_PROFILE et SAN_TYPE_RULE.
     * C'est le "garde du corps" qui vérifie chaque nouvelle demande.
     */
    private void verifySansLimitsDynamically(RequestDto requestDto) throws CertisRequestException {
        // On vérifie que les informations nécessaires sont présentes dans la requête.
        if (requestDto.getCertificate() == null || requestDto.getCertificate().getType() == null || requestDto.getCertificate().getType().getName() == null) {
            LOGGER.warn("Validation des limites de SANs ignorée : le type de certificat (profil) est manquant dans la requête.");
            return; 
        }

        // On récupère le nom du profil depuis la requête (ex: "SSL_SRVR").
        String profileName = requestDto.getCertificate().getType().getName();
        List<San> sansInRequest = requestDto.getCertificate().getSans();

        // Étape 1 : Charger les règles depuis la base de données pour ce profil.
        // On utilise le DAO existant pour trouver le profil par son nom.
        Optional<AutomationHubProfile> profileOpt = automationHubProfileDao.findByProfileName(profileName);
        if (!profileOpt.isPresent()) {
            // Si le profil n'existe pas dans notre base, c'est une erreur.
            LOGGER.error("Validation échouée : Le profil de certificat '{}' est inconnu de notre système.", profileName);
            throw new CertisRequestException("error.profile.unknown", new Object[]{profileName}, HttpStatus.BAD_REQUEST);
        }
        
        // On récupère la collection de règles associées à ce profil.
        // D'après votre entité, la collection s'appelle "sans".
        Set<SanTypeRule> rules = profileOpt.get().getSans();

        if (CollectionUtils.isEmpty(rules)) {
            LOGGER.warn("Aucune règle de SAN n'est définie en base pour le profil '{}'.", profileName);
            // Comportement à définir : doit-on accepter ou rejeter ?
            // Si des SANs sont présents dans la requête alors qu'aucune règle n'existe, il est plus sûr de rejeter.
            if (!CollectionUtils.isEmpty(sansInRequest)) {
                throw new CertisRequestException("error.san.not_allowed_for_profile", new Object[]{profileName}, HttpStatus.BAD_REQUEST);
            }
            return; // S'il n'y a pas de règles et pas de SANs dans la requête, c'est OK.
        }

        // Étape 2 : Compter le nombre de SANs par type dans la requête de l'utilisateur.
        Map<SanTypeEnum, Long> sanCountsByType = sansInRequest.stream()
                .filter(san -> san.getSanType() != null)
                .collect(Collectors.groupingBy(San::getSanType, Collectors.counting()));

        // Étape 3 : Vérifier si la requête contient des types de SANs non autorisés.
        for (SanTypeEnum requestedSanType : sanCountsByType.keySet()) {
            boolean isTypeAllowedInProfile = rules.stream()
                    .anyMatch(rule -> rule.getSanTypeEnum().equals(requestedSanType));

            if (!isTypeAllowedInProfile) {
                LOGGER.error("Validation échouée : le type de SAN '{}' n'est pas autorisé pour le profil '{}'", requestedSanType, profileName);
                throw new CertisRequestException("error.san.type.unauthorized", new Object[]{requestedSanType.name(), profileName}, HttpStatus.BAD_REQUEST);
            }
        }

        // Étape 4 : Valider les limites MIN et MAX pour chaque règle du profil.
        for (SanTypeRule rule : rules) {
            SanTypeEnum sanType = rule.getSanTypeEnum();
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
        
        LOGGER.info("Validation des limites de SANs réussie pour la requête avec le profil '{}'.", profileName);
    }

    // Conservez vos autres méthodes utilitaires si elles existent
    private boolean skipValidationIfDataMissing(RequestDto requestDto) {
        // ... votre code existant ici ...
        return false;
    }
}
Comment l'intégrer ?
Remplacez le contenu de votre SanServiceImpl.java par ce code.
Adaptez le constructeur pour qu'il inclue toutes les dépendances dont la classe a besoin. J'ai mis en commentaire UrlRacineService car je ne sais pas si vous en avez toujours besoin.
Supprimez les anciennes constantes et méthodes de validation des limites pour ne pas laisser de code mort.
Assurez-vous que le DAO/Repository AutomationHubProfileDao est bien injecté. D'après vos images, il est déjà déclaré, donc Spring devrait le trouver sans problème.
Ajoutez les clés d'erreur (error.profile.unknown, etc.) dans vos fichiers de messages (messages.properties) pour que les messages d'erreur soient clairs pour l'utilisateur.
Une fois cela fait, votre solution sera complète. Le scheduler mettra à jour les règles, et ce service les utilisera pour valider les nouvelles demandes.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
