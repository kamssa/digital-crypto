
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