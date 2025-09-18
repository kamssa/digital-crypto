Étape 1 : Mettre à jour les dépendances de SanServiceImpl
Pour que le service puisse extraire et décoder le CSR, il a besoin de nouveaux outils.
Fichier à modifier : SanServiceImpl.java
Action : Injecter FileManageService et CertificateCsrDecoder via le constructeur.
code
Java
// Dans SanServiceImpl.java

@Service
public class SanServiceImpl implements SanService {
    // ... (dépendances existantes comme automationHubProfileService, sanTypeRuleRepository...)

    // --- NOUVELLES DÉPENDANCES à ajouter ---
    private final FileManageService fileManageService;
    private final CertificateCsrDecoder csrDecoder;

    // --- METTRE À JOUR LE CONSTRUCTEUR ---
    public SanServiceImpl(AutomationHubProfileService automationHubProfileService, 
                          SanTypeRuleRepository sanTypeRuleRepository,
                          FileManageService fileManageService, // Ajouter
                          CertificateCsrDecoder csrDecoder,   // Ajouter
                          /* ... autres dépendances ... */) {
        // ... (assignations existantes)
        this.fileManageService = fileManageService;
        this.csrDecoder = csrDecoder;
    }
    
    // ...
}
Explication : On donne au SanServiceImpl les capacités techniques pour lire les fichiers et décoder les CSRs, ce qui le rend autonome.
Étape 2 : Remplacer l'ancienne logique de validation
Le point d'entrée de la validation, validateSansPerRequest, doit être simplifié pour appeler notre nouvelle méthode.
Fichier à modifier : SanServiceImpl.java
Action : Nettoyer la méthode validateSansPerRequest et supprimer tout l'ancien code (constantes et méthodes de validation statiques).
code
Java
// Dans SanServiceImpl.java

@Service
public class SanServiceImpl implements SanService {
    // ... (dépendances et constructeur mis à jour)

    // ===============================================================================
    // --- PARTIE À SUPPRIMER ---
    // Supprimez toutes les anciennes constantes de limites (ex: INT_SSL_SRVR_LIMIT)
    // et les anciennes méthodes de validation (ex: verifySansLimitForInternalCertificates).
    // ===============================================================================


    /**
     * Méthode principale de validation, maintenant simplifiée.
     * Elle orchestre les différentes étapes de validation des SANs.
     */
    @Override
    public void validateSansPerRequest(RequestDto requestDto) throws CertisRequestException {
        // Cette méthode devient le chef d'orchestre

        // 1. Validation dynamique des limites (min/max/types autorisés)
        // C'est notre nouvelle méthode autonome.
        this.verifySansLimitsDynamically(requestDto);

        // 2. Conserver les autres validations nécessaires qui ne sont pas liées aux limites.
        // Par exemple, la validation du format, ou des vérifications sur Refweb.
        // this.verifySanFormats(requestDto);
        // this.validateSansOnRefweb(requestDto);
    }
    
    // ... (la suite du code est juste en dessous)
}
Explication : On fait le ménage. L'ancienne logique complexe est remplacée par un appel unique à notre nouvelle méthode intelligente.
Étape 3 : Le Cœur de la Solution - La méthode verifySansLimitsDynamically
Voici le code complet de la nouvelle méthode autonome, prête à être copiée-collée dans SanServiceImpl.
Fichier à modifier : SanServiceImpl.java
Action : Ajouter cette nouvelle méthode privée.
code
Java
/**
     * Valide les SANs d'une requête en récupérant et fusionnant les SANs
     * depuis la requête elle-même ET depuis le fichier CSR, puis en les comparant
     * aux règles dynamiques stockées en base de données.
     *
     * @param requestDto L'objet contenant la demande de certificat à valider.
     * @throws CertisRequestException Si une règle de validation est violée.
     */
    private void verifySansLimitsDynamically(RequestDto requestDto) throws CertisRequestException {
        
        // --- PARTIE 1 : VÉRIFICATIONS PRÉLIMINAIRES ---
        if (requestDto.getCertificate() == null || requestDto.getCertificate().getType() == null) {
            // Pas de type de certificat, on ne peut pas trouver les règles.
            // On suppose qu'une autre validation gère ce cas d'erreur.
            return; 
        }

        // --- PARTIE 2 : RÉCUPÉRATION ET FUSION DE TOUS LES SANS ---
        LOGGER.debug("Début de la récupération et fusion des SANs depuis la requête et le CSR.");
        Set<San> finalUniqueSans = new LinkedHashSet<>();

        // 2a. Récupérer les SANs saisis directement par l'utilisateur dans la requête.
        if (requestDto.getCertificate().getSans() != null) {
            finalUniqueSans.addAll(requestDto.getCertificate().getSans());
        }

        // 2b. Extraire le CSR, le décoder et y récupérer les SANs.
        try {
            String csr = this.fileManageService.extractCsr(requestDto, Boolean.TRUE);
            if (csr != null && !csr.isEmpty()) {
                List<San> sansFromCsr = this.csrDecoder.extractSansWithTypesFromCsr(csr);
                if (sansFromCsr != null) {
                    finalUniqueSans.addAll(sansFromCsr);
                }
            }
        } catch (Exception e) {
            // Si le décodage du CSR échoue, c'est une erreur bloquante.
            LOGGER.error("Erreur lors de l'extraction des SANs depuis le fichier CSR.", e);
            throw new CertisRequestException("Le fichier CSR est invalide ou illisible.", HttpStatus.BAD_REQUEST);
        }
        
        // On convertit notre Set de SANs uniques en une List pour la suite du traitement.
        List<San> sansInRequest = new ArrayList<>(finalUniqueSans);
        LOGGER.info("Validation dynamique effectuée sur un total de {} SANs (fusion de la requête et du CSR).", sansInRequest.size());


        // --- PARTIE 3 : TROUVER LE PROFIL TECHNIQUE CORRESPONDANT ---
        Long typeId = requestDto.getCertificate().getType().getId();
        Long subTypeId = (requestDto.getCertificate().getSubType() != null) ? requestDto.getCertificate().getSubType().getId() : null;

        try {
            // On utilise le service "traducteur" pour trouver le profil technique (AutomationHubProfile)
            // à partir du profil public (CertificateType) choisi par l'utilisateur.
            AutomationHubProfile profile = automationHubProfileService.findProfileEntityByTypeAndSubType(typeId, subTypeId);
            
            // --- PARTIE 4 : CHARGER LES RÈGLES ET APPLIQUER LA VALIDATION ---
            
            // 4a. On charge les règles de notre base de données pour le profil trouvé.
            List<SanTypeRule> rules = sanTypeRuleRepository.findByAutomationHubProfile(profile);
            
            // 4b. On applique la logique de validation (le code que nous avons déjà finalisé ensemble).
            if (rules.isEmpty()) {
                if (!sansInRequest.isEmpty()) {
                    throw new CertisRequestException("error.san.not_allowed_for_profile", new Object[]{profile.getProfileName()}, HttpStatus.BAD_REQUEST);
                }
                return; // Cas valide : pas de règles et pas de SANs.
            }

            Map<SanType, Long> sanCountsByType = sansInRequest.stream()
                    .filter(san -> san.getType() != null)
                    .collect(Collectors.groupingBy(San::getType, Collectors.counting()));

            for (SanType requestedSanType : sanCountsByType.keySet()) {
                if (rules.stream().noneMatch(rule -> rule.getType().equals(requestedSanType))) {
                    throw new CertisRequestException("error.san.type.unauthorized", new Object[]{requestedSanType.name(), profile.getProfileName()}, HttpStatus.BAD_REQUEST);
                }
            }

            for (SanTypeRule rule : rules) {
                long countInRequest = sanCountsByType.getOrDefault(rule.getType(), 0L);
                if (countInRequest > rule.getMaxValue()) {
                    throw new CertisRequestException("error.san.max.violation", new Object[]{rule.getType().name(), rule.getMaxValue()}, HttpStatus.BAD_REQUEST);
                }
                if (countInRequest < rule.getMinValue()) {
                    throw new CertisRequestException("error.san.min.violation", new Object[]{rule.getType().name(), rule.getMinValue()}, HttpStatus.BAD_REQUEST);
                }
            }

        } catch (FailedToRetrieveProfileException e) {
            LOGGER.error("Impossible de mapper le type de certificat ({}/{}) à un profil technique.", typeId, subTypeId, e);
            throw new CertisRequestException("Le profil de certificat sélectionné n'est pas valide ou n'a pas de règles de SANs configurées.", HttpStatus.BAD_REQUEST);
        }
    }
Explication : Cette méthode est maintenant le "couteau suisse" de la validation des SANs. Elle orchestre la récupération des données, leur fusion, la recherche du bon contexte (le profil), et enfin l'application des règles. En la plaçant dans SanServiceImpl, vous respectez bien le principe de responsabilité unique.
//////////////////////////////////////////////////////////////////////////////////
code
Java
// Dans l'interface AutomationHubProfileService

public interface AutomationHubProfileService {
    // ... (vos méthodes existantes comme `getProfileByTypeAndSubType`)

    // --- AJOUTEZ LA SIGNATURE DE LA NOUVELLE MÉTHODE CI-DESSOUS ---

    /**
     * Récupère l'ENTITÉ JPA AutomationHubProfile correspondante pour un type et sous-type de certificat.
     * Cette méthode est utilisée en interne par d'autres services qui ont besoin de l'objet Entité.
     *
     * @param typeId L'ID du CertificateType.
     * @param subTypeId L'ID du CertificateSubType (peut être null).
     * @return L'entité AutomationHubProfile correspondante.
     * @throws FailedToRetrieveProfileException si aucun mapping n'est trouvé en base de données.
     */
    AutomationHubProfile findProfileEntityByTypeAndSubType(Long typeId, Long subTypeId) throws FailedToRetrieveProfileException;
}
Étape 2 : Implémenter la nouvelle méthode dans le Service
Maintenant, ouvrez la classe d'implémentation pour y ajouter le code de cette nouvelle méthode. La logique sera très similaire à celle de getProfileByTypeAndSubType, mais au lieu de retourner un DTO, elle retournera directement l'entité.
Fichier : AutomationHubProfileServiceImpl.java
code
Java
@Service
public class AutomationHubProfileServiceImpl implements AutomationHubProfileService {
    
    // ... (vos dépendances existantes comme certisTypeToAutomationHubProfileDao et automationHubProfileMapper)

    /**
     * Votre méthode existante, qui retourne un DTO. On n'y touche pas.
     */
    @Override
    public AutomationHubProfileDto getProfileByTypeAndSubType(Long typeId, Long subTypeId) throws FailedToRetrieveProfileException {
        AutomationHubProfile automationHubProfile = findProfileEntityByTypeAndSubType(typeId, subTypeId);
        return automationHubProfileMapper.toDto(automationHubProfile);
    }
    
    // --- AJOUTEZ LA NOUVELLE MÉTHODE CI-DESSOUS ---

    /**
     * Implémentation de la nouvelle méthode qui retourne l'entité.
     * Elle utilise le DAO de la table de mapping pour faire la "traduction".
     */
    @Override
    public AutomationHubProfile findProfileEntityByTypeAndSubType(Long typeId, Long subTypeId) throws FailedToRetrieveProfileException {
        
        // On suppose ici que votre DAO "certisTypeToAutomationHubProfileDao"
        // a une méthode pour trouver l'entité de mapping CertisTypeToAutomationHubProfile.
        // Si la méthode n'existe pas, il faudra la créer dans l'interface du DAO.
        
        CertisTypeToAutomationHubProfile mapping = certisTypeToAutomationHubProfileDao
                .findByCertificateTypeIdAndCertificateSubTypeId(typeId, subTypeId)
                .orElseThrow(() -> new FailedToRetrieveProfileException(String.valueOf(typeId), String.valueOf(subTypeId)));
        
        // Une fois qu'on a trouvé l'objet de mapping, on retourne le profil technique qui lui est associé.
        // C'est l'entité que notre SanServiceImpl pourra utiliser.
        return mapping.getAutomationHubProfile();
    }
}
Étape 3 (Si nécessaire) : Créer la méthode dans le DAO
Il est possible que la méthode findByCertificateTypeIdAndCertificateSubTypeId n'existe pas encore dans votre interface CertisTypeToAutomationHubProfileDao. Si c'est le cas, ajoutez-la. Spring Data JPA s'occupera de l'implémenter pour vous.
Fichier : CertisTypeToAutomationHubProfileDao.java
code
Java
// Dans l'interface CertisTypeToAutomationHubProfileDao

public interface CertisTypeToAutomationHubProfileDao extends JpaRepository<CertisTypeToAutomationHubProfile, Long> {
    
    // Spring Data JPA va automatiquement générer la requête SQL pour cette méthode
    // en se basant sur les noms des champs dans l'entité CertisTypeToAutomationHubProfile.
    Optional<CertisTypeToAutomationHubProfile> findByCertificateTypeIdAndCertificateSubTypeId(Long certificateTypeId, Long certificateSubTypeId);

}
Résumé
Vous ajoutez une nouvelle "capacité" à votre AutomationHubProfileService en lui demandant de pouvoir retourner l'entité AutomationHubProfile brute.
Vous implémentez cette capacité en utilisant la logique de mapping qui existe déjà dans votre application.
Votre SanServiceImpl peut maintenant utiliser cette nouvelle capacité pour obtenir l'entité dont il a besoin pour sa validation, sans avoir à connaître les détails complexes du mapping.
C'est une excellente façon de réutiliser le code et de respecter l'architecture de votre projet. Une fois ces modifications faites, l'erreur "cannot find symbol" sur la méthode findProfileEntityByTypeAndSubType disparaîtra.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
//////////////////////////////////////////////////////
Tâche 1 : Intégrer la validation dans RequestServiceImpl
L'objectif est d'appeler notre SanService au bon moment lors de la création d'une requête.
Fichier à modifier : RequestServiceImpl.java
code
Java
package com.bnpparibas.certis.certificate.request.service.impl;

// ... (tous vos imports existants)
import com.bnpparibas.certis.certificate.request.service.SanService; // <-- Assurez-vous d'importer l'interface

@Service
public class RequestServiceImpl implements RequestService {

    // ... (toutes vos dépendances existantes comme RequestDao, etc.)

    // --- AJOUTER LA DÉPENDANCE VERS SANSERVICE ---
    @Autowired
    private SanService sanService;

    // ...

    /**
     * C'est dans la méthode qui crée une nouvelle requête de certificat
     * que nous devons insérer notre logique de validation.
     */
    @Override
    public RequestDto createRequest(RequestDto requestDto) throws Exception { // ou le type d'exception que vous utilisez
        
        // ... (votre code existant pour initialiser, extraire le CSR, etc.)

        // --- DÉBUT DE L'INTÉGRATION DE LA VALIDATION ---
        
        // Ici, on suppose que votre logique de fusion des SANs (depuis la requête et le CSR)
        // a déjà eu lieu et que la liste finale est dans requestDto.getCertificate().getSans().
        // Si ce n'est pas le cas, il faudrait d'abord faire la fusion.
        
        LOGGER.info("Début de la validation dynamique des limites de SANs...");
        
        // On appelle la méthode principale de validation du SanService.
        // Tout le travail complexe que nous avons fait (fusion, traduction de profil, validation)
        // est maintenant caché derrière cet appel unique.
        sanService.validateSansPerRequest(requestDto);
        
        LOGGER.info("Validation dynamique des SANs réussie.");

        // --- FIN DE L'INTÉGRATION DE LA VALIDATION ---

        // Le reste de votre logique de création de requête continue ici.
        // Si la validation a échoué, une exception aura déjà été levée et ce code ne sera pas atteint.
        
        // ... (sauvegarde de l'entité Request, etc.)
        
        return savedRequestDto;
    }
    
    // ... (le reste de vos méthodes dans RequestServiceImpl)
}
Explication :
On injecte SanService.
Dans la méthode createRequest (ou une méthode similaire comme submitRequest), avant de sauvegarder quoi que ce soit en base, on appelle sanService.validateSansPerRequest(requestDto).
C'est aussi simple que ça ! Si la validation échoue, une CertisRequestException sera levée, ce qui interrompra le processus et retournera une erreur à l'utilisateur. Si elle réussit, le code continue son exécution normale.
Tâche 2 : Créer l'endpoint pour le Front-end
Ici, nous créons un DTO de réponse, ajoutons une méthode au service AutomationHubProfileService, et créons le controller.
Fichier 1 : SanRuleResponseDto.java (Nouveau DTO de réponse)
Emplacement : Dans un package DTO approprié, par exemple com.bnpparibas.certis.dto
code
Java
package com.bnpparibas.certis.dto;

import com.bnpparibas.certis.certificate.request.enums.SanType;

/**
 * DTO simple pour retourner les règles de SANs au front-end.
 */
public class SanRuleResponseDto {

    private SanType type;
    private Integer min;
    private Integer max;

    // --- Getters et Setters ---
    public SanType getType() { return type; }
    public void setType(SanType type) { this.type = type; }
    public Integer getMin() { return min; }
    public void setMin(Integer min) { this.min = min; }
    public Integer getMax() { return max; }
    public void setMax(Integer max) { this.max = max; }
}
Fichier 2 : AutomationHubProfileServiceImpl.java (Ajouter la méthode de récupération)
Fichier à modifier : AutomationHubProfileServiceImpl.java
code
Java
// ... (imports existants)
import com.bnpparibas.certis.dto.SanRuleResponseDto; // Le nouveau DTO
import com.bnpparibas.certis.automationhub.model.SanTypeRule;
import java.util.stream.Collectors;

@Service
public class AutomationHubProfileServiceImpl implements AutomationHubProfileService {

    // ... (dépendances et méthodes existantes)

    // --- NOUVELLE MÉTHODE PUBLIQUE POUR LE CONTROLLER ---
    
    /**
     * Récupère les règles de SANs pour un profil technique donné.
     * @param profileName Le nom du profil technique (ex: "SSL_SRVR").
     * @return Une liste de DTOs contenant les règles.
     */
    public List<SanRuleResponseDto> getSanRulesForProfile(String profileName) {
        // 1. On cherche le profil dans la table AUTOMATIONHUB_PROFILE
        AutomationHubProfile profile = automationHubProfileDao.findByProfileName(profileName)
            .orElseThrow(() -> new ResourceNotFoundException("Profil non trouvé : " + profileName)); // Lance 404 si non trouvé

        // 2. On récupère les règles associées depuis la table SAN_TYPE_RULE
        List<SanTypeRule> ruleEntities = sanTypeRuleRepository.findByAutomationHubProfile(profile);

        // 3. On convertit les Entités en DTOs pour le front-end
        return ruleEntities.stream()
            .map(this::convertToSanRuleDto)
            .collect(Collectors.toList());
    }

    // Méthode de conversion privée. Vous pouvez aussi utiliser une classe Mapper (MapStruct).
    private SanRuleResponseDto convertToSanRuleDto(SanTypeRule entity) {
        SanRuleResponseDto dto = new SanRuleResponseDto();
        dto.setType(entity.getType());
        dto.setMin(entity.getMinValue());
        dto.setMax(entity.getMaxValue());
        return dto;
    }
}
(N'oubliez pas d'ajouter la signature de getSanRulesForProfile à l'interface AutomationHubProfileService)
Fichier 3 : RequestController.java (Ajouter le nouvel endpoint)
Fichier à modifier : RequestController.java
code
Java
// ... (imports existants)
import com.bnpparibas.certis.dto.SanRuleResponseDto; // Le nouveau DTO
import com.bnpparibas.certis.automationhub.service.AutomationHubProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping(value = "/request")
public class RequestController {

    // ... (dépendances et endpoints existants)
    
    // --- NOUVELLE DÉPENDANCE ---
    private final AutomationHubProfileService automationHubProfileService;

    // --- METTRE À JOUR LE CONSTRUCTEUR ---
    public RequestController(/* ... autres services */, AutomationHubProfileService automationHubProfileService) {
        // ...
        this.automationHubProfileService = automationHubProfileService;
    }
    
    // --- NOUVEL ENDPOINT POUR LE FRONT-END ---

    /**
     * Endpoint GET pour récupérer les règles de SANs pour un profil de certificat.
     * Le front-end appellera cette API lorsqu'un utilisateur sélectionne un type de certificat
     * pour savoir quelles contraintes de SANs afficher.
     *
     * @param profileName Le nom du profil technique (ex: "SSL_SRVR")
     * @return Une liste de règles de SANs.
     */
    @GetMapping("/profiles/{profileName}/san-rules")
    public ResponseEntity<List<SanRuleResponseDto>> getSanRulesForProfile(@PathVariable String profileName) {
        // On délègue toute la logique au service.
        List<SanRuleResponseDto> rules = automationHubProfileService.getSanRulesForProfile(profileName);
        
        // Même si la liste est vide (pas de règles ou profil inconnu), on retourne OK avec une liste vide.
        // C'est souvent plus simple à gérer pour le front-end qu'une erreur 404.
        return ResponseEntity.ok(rules);
    }
}
Avec ces modifications, votre backend est maintenant complet pour ce ticket. Vous avez :
Intégré la validation dynamique dans votre flux de création.
Exposé un endpoint sécurisé et propre pour que le front-end puisse récupérer les règles.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
/////////////////////////////////////////
Fichier à modifier : RequestController.java
code
Java
// Dans RequestController.java

@RestController
@RequestMapping(value = "/request")
public class RequestController {

    // ... (dépendances et constructeur existants, y compris AutomationHubProfileService)

    /**
     * NOUVEL ENDPOINT AMÉLIORÉ
     * Récupère les règles de SANs en se basant sur les IDs du type et sous-type de certificat.
     * C'est l'endpoint que le front-end appellera.
     *
     * @param typeId L'ID du CertificateType sélectionné par l'utilisateur.
     * @param subTypeId L'ID du CertificateSubType (optionnel).
     * @return Une liste de règles de SANs.
     */
    @GetMapping("/certificate-types/{typeId}/san-rules")
    public ResponseEntity<List<SanRuleResponseDto>> getSanRulesForCertificateType(
            @PathVariable Long typeId,
            @RequestParam(required = false) Long subTypeId) {
        
        // On délègue la logique au service, qui saura faire la "traduction".
        List<SanRuleResponseDto> rules = automationHubProfileService.getSanRulesByCertificateType(typeId, subTypeId);
        
        return ResponseEntity.ok(rules);
    }
}
Explication des changements :
URL : L'URL est maintenant /request/certificate-types/{typeId}/san-rules. C'est très clair et RESTful.
@PathVariable Long typeId : On récupère l'ID du type directement depuis l'URL.
@RequestParam(required = false) Long subTypeId : On récupère l'ID du sous-type comme un paramètre de requête optionnel. Le front-end appellera donc /.../san-rules?subTypeId=5 s'il y a un sous-type.
Étape 2 : Le Service (AutomationHubProfileServiceImpl.java)
On crée la nouvelle méthode getSanRulesByCertificateType qui va orchestrer la traduction et la récupération des règles.
Fichier à modifier : AutomationHubProfileServiceImpl.java
code
Java
// Dans AutomationHubProfileServiceImpl.java

@Service
public class AutomationHubProfileServiceImpl implements AutomationHubProfileService {

    // ... (dépendances et méthodes existantes)

    /**
     * NOUVELLE MÉTHODE PUBLIQUE
     * Trouve le profil technique correspondant à un type/sous-type,
     * puis récupère et convertit ses règles de SANs en DTOs.
     *
     * @param typeId L'ID du CertificateType.
     * @param subTypeId L'ID du CertificateSubType (peut être null).
     * @return Une liste de DTOs de règles, ou une liste vide si aucun profil/règle n'est trouvé.
     */
    public List<SanRuleResponseDto> getSanRulesByCertificateType(Long typeId, Long subTypeId) {
        try {
            // 1. On réutilise notre méthode de "traduction" pour trouver le profil technique.
            AutomationHubProfile profile = this.findProfileEntityByTypeAndSubType(typeId, subTypeId);
            
            // 2. On récupère les règles associées à ce profil technique.
            List<SanTypeRule> ruleEntities = sanTypeRuleRepository.findByAutomationHubProfile(profile);

            // 3. On convertit les entités en DTOs pour les retourner au controller.
            return ruleEntities.stream()
                    .map(this::convertToSanRuleDto)
                    .collect(Collectors.toList());

        } catch (FailedToRetrieveProfileException e) {
            // Si aucun profil technique ne correspond au type/sous-type,
            // on loggue l'information et on retourne une liste vide.
            // C'est plus simple à gérer pour le front-end qu'une erreur 404.
            LOGGER.warn("Aucun profil technique trouvé pour le typeId {} / subTypeId {}. Retour d'une liste de règles vide.", typeId, subTypeId);
            return Collections.emptyList();
        }
    }
    
    // La méthode de conversion privée convertToSanRuleDto(...) reste la même.
    private SanRuleResponseDto convertToSanRuleDto(SanTypeRule entity) {
        // ...
    }

    // La méthode findProfileEntityByTypeAndSubType(...) que nous avons créée reste la même.
    @Override
    public AutomationHubProfile findProfileEntityByTypeAndSubType(Long typeId, Long subTypeId) throws FailedToRetrieveProfileException {
        // ...
    }
}
(N'oubliez pas d'ajouter la signature de getSanRulesByCertificateType à l'interface AutomationHubProfileService)
Avantages de cette nouvelle approche
Logique pour le Front-end : L'API est maintenant conçue du point de vue du client (le front-end), ce qui est une excellente pratique. Le front n'a pas besoin de connaître les détails de votre mapping interne.
Robustesse : Toute la logique de "traduction" est centralisée et cachée dans le backend. Si un jour vous changez la façon dont les types sont mappés aux profils, le front-end n'aura pas besoin d'être modifié.
Code Propre : Le controller reste très simple et délègue tout le travail au service, ce qui est le bon design.
C'est une excellente suggestion de votre part. Cette version est bien meilleure.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
////////////////////////////
[
  {
    "type": "CN",
    "min": 1,
    "max": 1
  },
  {
    "type": "DNSNAME",
    "min": 0,
    "max": 10
  }
]
///////////////////////////////// resumer /////////////////////////////////////
Tâche 1 : Intégrer la Validation Dynamique
Fichier 1 : SanServiceImpl.java (Version finale)
Rôle : Le service de validation des SANs, maintenant autonome et dynamique.
code
Java
package com.bnpparibas.certis.certificate.request.service.impl;

// --- Imports ---
import com.bnpparibas.certis.automationhub.dao.SanTypeRuleRepository;
import com.bnpparibas.certis.automationhub.model.AutomationHubProfile;
import com.bnpparibas.certis.automationhub.model.SanTypeRule;
import com.bnpparibas.certis.automationhub.service.AutomationHubProfileService;
import com.bnpparibas.certis.certificate.request.csr.CertificateCsrDecoder;
import com.bnpparibas.certis.certificate.request.dto.RequestDto;
import com.bnpparibas.certis.certificate.request.dto.San;
import com.bnpparibas.certis.certificate.request.enums.SanType;
import com.bnpparibas.certis.certificate.request.service.FileManageService;
import com.bnpparibas.certis.certificate.request.service.SanService;
import com.bnpparibas.certis.exception.CertisRequestException;
import com.bnpparibas.certis.exception.FailedToRetrieveProfileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SanServiceImpl implements SanService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SanServiceImpl.class);

    // --- Dépendances nécessaires ---
    private final AutomationHubProfileService automationHubProfileService;
    private final SanTypeRuleRepository sanTypeRuleRepository;
    private final FileManageService fileManageService;
    private final CertificateCsrDecoder csrDecoder;

    public SanServiceImpl(AutomationHubProfileService automationHubProfileService,
                          SanTypeRuleRepository sanTypeRuleRepository,
                          FileManageService fileManageService,
                          CertificateCsrDecoder csrDecoder) {
        this.automationHubProfileService = automationHubProfileService;
        this.sanTypeRuleRepository = sanTypeRuleRepository;
        this.fileManageService = fileManageService;
        this.csrDecoder = csrDecoder;
    }
    
    @Override
    public void validateSansPerRequest(RequestDto requestDto) throws CertisRequestException {
        // Cette méthode est maintenant le point d'entrée unique.
        // On supprime l'ancien code (constantes, if/else, anciennes méthodes).
        this.verifySansLimitsDynamically(requestDto);

        // Conservez d'autres validations si nécessaire (ex: format, Refweb)
        // this.verifySanFormats(requestDto);
    }

    /**
     * Valide les SANs d'une requête en fusionnant ceux de la requête et du CSR,
     * puis en les comparant aux règles dynamiques stockées en base de données.
     */
    private void verifySansLimitsDynamically(RequestDto requestDto) throws CertisRequestException {
        
        if (requestDto.getCertificate() == null || requestDto.getCertificate().getType() == null) {
            return;
        }

        // --- PARTIE 1 : RÉCUPÉRATION ET FUSION DE TOUS LES SANS ---
        Set<San> finalUniqueSans = new LinkedHashSet<>();
        if (requestDto.getCertificate().getSans() != null) {
            finalUniqueSans.addAll(requestDto.getCertificate().getSans());
        }
        try {
            String csr = this.fileManageService.extractCsr(requestDto, Boolean.TRUE);
            if (csr != null && !csr.isEmpty()) {
                List<San> sansFromCsr = this.csrDecoder.extractSansWithTypesFromCsr(csr);
                if (sansFromCsr != null) {
                    finalUniqueSans.addAll(sansFromCsr);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Erreur lors de l'extraction des SANs depuis le CSR.", e);
            throw new CertisRequestException("Le fichier CSR est invalide ou illisible.", HttpStatus.BAD_REQUEST);
        }
        List<San> sansInRequest = new ArrayList<>(finalUniqueSans);
        LOGGER.info("Validation dynamique sur un total de {} SANs fusionnés.", sansInRequest.size());

        // --- PARTIE 2 : TROUVER LE PROFIL TECHNIQUE CORRESPONDANT ---
        Long typeId = requestDto.getCertificate().getType().getId();
        Long subTypeId = (requestDto.getCertificate().getSubType() != null) ? requestDto.getCertificate().getSubType().getId() : null;

        try {
            AutomationHubProfile profile = automationHubProfileService.findProfileEntityByTypeAndSubType(typeId, subTypeId);
            
            // --- PARTIE 3 : CHARGER LES RÈGLES ET APPLIQUER LA VALIDATION ---
            List<SanTypeRule> rules = sanTypeRuleRepository.findByAutomationHubProfile(profile);
            
            if (rules.isEmpty()) {
                if (!sansInRequest.isEmpty()) {
                    throw new CertisRequestException("error.san.not_allowed_for_profile", new Object[]{profile.getProfileName()}, HttpStatus.BAD_REQUEST);
                }
                return;
            }

            Map<SanType, Long> sanCountsByType = sansInRequest.stream()
                    .filter(san -> san.getType() != null)
                    .collect(Collectors.groupingBy(San::getType, Collectors.counting()));

            for (SanType requestedSanType : sanCountsByType.keySet()) {
                if (rules.stream().noneMatch(rule -> rule.getType().equals(requestedSanType))) {
                    throw new CertisRequestException("error.san.type.unauthorized", new Object[]{requestedSanType.name(), profile.getProfileName()}, HttpStatus.BAD_REQUEST);
                }
            }

            for (SanTypeRule rule : rules) {
                long countInRequest = sanCountsByType.getOrDefault(rule.getType(), 0L);
                if (countInRequest > rule.getMaxValue()) {
                    throw new CertisRequestException("error.san.max.violation", new Object[]{rule.getType().name(), rule.getMaxValue()}, HttpStatus.BAD_REQUEST);
                }
                if (countInRequest < rule.getMinValue()) {
                    throw new CertisRequestException("error.san.min.violation", new Object[]{rule.getType().name(), rule.getMinValue()}, HttpStatus.BAD_REQUEST);
                }
            }
        } catch (FailedToRetrieveProfileException e) {
            LOGGER.error("Impossible de mapper le type de certificat ({}/{}) à un profil technique.", typeId, subTypeId, e);
            throw new CertisRequestException("Le profil de certificat sélectionné n'est pas valide ou n'a pas de règles de SANs configurées.", HttpStatus.BAD_REQUEST);
        }
    }
}
Tâche 2 : Créer l'API GET pour le Front-end
Fichier 2 : SanRuleResponseDto.java (Nouveau)
Rôle : Un objet simple pour transporter les données des règles vers le front-end.
code
Java
package com.bnpparibas.certis.dto; // Ou un autre package DTO

import com.bnpparibas.certis.certificate.request.enums.SanType;

public class SanRuleResponseDto {
    private SanType type;
    private Integer min;
    private Integer max;

    // Getters et Setters
    public SanType getType() { return type; }
    public void setType(SanType type) { this.type = type; }
    public Integer getMin() { return min; }
    public void setMin(Integer min) { this.min = min; }
    public Integer getMax() { return max; }
    public void setMax(Integer max) { this.max = max; }
}
Fichier 3 : AutomationHubProfileServiceImpl.java (Méthodes ajoutées)
Rôle : Ajouter les méthodes nécessaires pour que le controller puisse récupérer les règles.
code
Java
// Dans AutomationHubProfileServiceImpl.java

// ... (imports)
import com.bnpparibas.certis.dto.SanRuleResponseDto;
import java.util.stream.Collectors;
import java.util.Collections;

@Service
public class AutomationHubProfileServiceImpl implements AutomationHubProfileService {
    // ... (dépendances et méthodes de synchronisation existantes)

    // --- NOUVELLES MÉTHODES POUR L'ENDPOINT ---

    @Override
    public List<SanRuleResponseDto> getSanRulesByCertificateType(Long typeId, Long subTypeId) {
        try {
            AutomationHubProfile profile = this.findProfileEntityByTypeAndSubType(typeId, subTypeId);
            List<SanTypeRule> ruleEntities = sanTypeRuleRepository.findByAutomationHubProfile(profile);
            return ruleEntities.stream()
                    .map(this::convertToSanRuleDto)
                    .collect(Collectors.toList());
        } catch (FailedToRetrieveProfileException e) {
            LOGGER.warn("Aucun profil technique trouvé pour le typeId {} / subTypeId {}. Retour d'une liste de règles vide.", typeId, subTypeId);
            return Collections.emptyList();
        }
    }

    @Override
    public AutomationHubProfile findProfileEntityByTypeAndSubType(Long typeId, Long subTypeId) throws FailedToRetrieveProfileException {
        return certisTypeToAutomationHubProfileDao.findProfileByTypeAndSubType(typeId, subTypeId)
            .orElseThrow(() -> new FailedToRetrieveProfileException(String.valueOf(typeId), String.valueOf(subTypeId)));
    }

    private SanRuleResponseDto convertToSanRuleDto(SanTypeRule entity) {
        SanRuleResponseDto dto = new SanRuleResponseDto();
        dto.setType(entity.getType());
        dto.setMin(entity.getMinValue());
        dto.setMax(entity.getMaxValue());
        return dto;
    }
}
Fichier 4 : RequestController.java (Endpoint ajouté)
Rôle : Exposer la nouvelle fonctionnalité au monde extérieur (le front-end).
code
Java
package com.bnpparibas.certis.api.controller; // Ou le package de votre controller

import com.bnpparibas.certis.automationhub.service.AutomationHubProfileService;
import com.bnpparibas.certis.dto.SanRuleResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/request") // Votre préfixe existant
public class RequestController {

    // ... (dépendances existantes)
    private final AutomationHubProfileService automationHubProfileService;

    // Mettez à jour votre constructeur pour injecter le service
    public RequestController(AutomationHubProfileService automationHubProfileService, /* ... */) {
        this.automationHubProfileService = automationHubProfileService;
        // ...
    }

    // --- NOUVEL ENDPOINT ---
    @GetMapping("/certificatetypes/{typeId}/san-rules")
    public ResponseEntity<List<SanRuleResponseDto>> getSanRulesForCertificateType(
            @PathVariable Long typeId,
            @RequestParam(required = false) Long subTypeId) {
        
        List<SanRuleResponseDto> rules = automationHubProfileService.getSanRulesByCertificateType(typeId, subTypeId);
        return ResponseEntity.ok(rules);
    }
    
    // ... (vos autres endpoints existants)
}
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
/////////////////////// ajout subtype ////////////////////////////
// Dans RequestController.java

@RestController
@RequestMapping(value = "/request")
public class RequestController {

    // ... (dépendances et constructeur)

    /**
     * Endpoint GET pour récupérer les règles de SANs en se basant sur les IDs du type et (optionnellement) du sous-type de certificat.
     *
     * Appel possible SANS sous-type : /api/v1/request/certificate-types/3/san-rules
     * Appel possible AVEC sous-type  : /api/v1/request/certificate-types/3/san-rules?subTypeId=5
     *
     * @param typeId L'ID du CertificateType sélectionné par l'utilisateur.
     * @param subTypeId L'ID du CertificateSubType (optionnel).
     * @return Une liste de règles de SANs.
     */
    @GetMapping("/certificate-types/{typeId}/san-rules")
    public ResponseEntity<List<SanRuleResponseDto>> getSanRulesForCertificateType(
            @PathVariable Long typeId,
            @RequestParam(required = false) Long subTypeId) { // required = false gère déjà le cas où le paramètre est absent
        
        // On délègue la logique au service. Le service recevra subTypeId = null si le front ne l'envoie pas.
        List<SanRuleResponseDto> rules = automationHubProfileService.getSanRulesByCertificateType(typeId, subTypeId);
        
        return ResponseEntity.ok(rules);
    }
}
///////////////////////////
Insomnia va automatiquement construire l'URL complète pour vous : http://localhost:8080/request/certificatetypes/3/san-rules?subTypeId=5.