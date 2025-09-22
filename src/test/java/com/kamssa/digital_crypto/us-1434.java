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
/////////////////// nouvelle regle //////////////////////////
Résumé de la nouvelle logique :
Le CN est roi : Un des SANs de type CN (Common Name) doit obligatoirement être identique à la valeur du champ Common Name du certificat.
Pré-remplissage : Ce SAN "miroir" du CN est considéré comme implicite. Il doit être déduit du nombre total de SANs que l'utilisateur peut saisir.
Interface grisée : Le front-end doit afficher ce SAN "miroir" comme étant pré-rempli et non-modifiable.
Cela impacte deux domaines : la validation backend (pour forcer la règle) et l'API pour le front-end (pour lui donner les informations nécessaires pour griser le champ).
Voici la solution complète.
Solution - Partie 1 : Modification de la Validation Backend (SanServiceImpl)
Nous devons renforcer notre méthode verifySansLimitsDynamically pour qu'elle vérifie cette nouvelle règle.
Fichier à modifier : SanServiceImpl.java
Action : Ajouter une vérification explicite que l'un des SANs de type CN correspond bien au commonName de la requête.
code
Java
/**
     * Version MISE À JOUR de la méthode de validation.
     * Elle inclut maintenant la vérification que le Common Name est bien présent dans les SANs.
     */
    private void verifySansLimitsDynamically(RequestDto requestDto) throws CertisRequestException {
        
        // --- ÉTAPE 1 : VÉRIFICATIONS PRÉLIMINAIRES ---
        if (requestDto.getCertificate() == null || requestDto.getCertificate().getType() == null) {
            return;
        }
        
        // On récupère le Common Name, qui est maintenant essentiel pour la validation.
        String commonName = requestDto.getCertificate().getCommonName();
        if (commonName == null || commonName.trim().isEmpty()) {
            throw new CertisRequestException("Le champ Common Name (CN) est obligatoire pour valider les SANs.", HttpStatus.BAD_REQUEST);
        }

        // --- ÉTAPE 2 : FUSION DE TOUS LES SANS (inchangé) ---
        // (Le code pour fusionner les SANs de la requête et du CSR reste ici)
        List<San> sansInRequest = new ArrayList<>(/* ... fusion des SANs ... */);

        // --- ÉTAPE 3 : TROUVER LE PROFIL TECHNIQUE (inchangé) ---
        // (Le code pour trouver le 'profile' via le service reste ici)
        AutomationHubProfile profile = //...
        
        // --- ÉTAPE 4 : NOUVELLE VALIDATION SPÉCIFIQUE AU CN ---
        
        // On vérifie que la liste des SANs contient bien un SAN de type CN
        // dont la valeur est égale au Common Name du certificat.
        boolean cnAsSanIsPresent = sansInRequest.stream()
                .anyMatch(san -> san.getType() == SanType.CN && commonName.equalsIgnoreCase(san.getValue()));
        
        if (!cnAsSanIsPresent) {
            LOGGER.error("Validation échouée : La liste des SANs doit contenir une entrée de type CN correspondant au Common Name '{}'.", commonName);
            throw new CertisRequestException("error.san.cn_missing", new Object[]{commonName}, HttpStatus.BAD_REQUEST);
        }

        // --- ÉTAPE 5 : VALIDATION DES LIMITES MIN/MAX (inchangé) ---
        // Le reste de la logique de validation des comptes min/max reste exactement la même.
        // Elle s'assurera que le nombre total de CNs (incluant celui qui matche le Common Name)
        // respecte bien les limites min/max définies dans les règles.
        
        List<SanTypeRule> rules = sanTypeRuleRepository.findByAutomationHubProfile(profile);
        // ... (Le reste du code de validation avec les boucles for est identique)
    }
Solution - Partie 2 : Modification de l'API pour le Front-End
L'ancien DTO SanRuleResponseDto n'est plus suffisant. Il ne dit pas au front-end qu'une des places est "réservée". Nous allons donc créer une structure de réponse plus riche.
Fichier 1 : PredefinedSanDto.java (Nouveau DTO)
Ce DTO représente un SAN qui doit être pré-rempli et non-éditable.
code
Java
package com.bnpparibas.certis.dto;

import com.bnpparibas.certis.certificate.request.enums.SanType;

public class PredefinedSanDto {
    private SanType type;
    private String value; // Une valeur spéciale comme "{COMMON_NAME}"
    private boolean editable = false; // Toujours false

    // Getters et Setters
}
Fichier 2 : ProfileRulesResponseDto.java (Nouveau DTO de réponse)
Ce DTO sera le nouvel objet de réponse de notre endpoint. Il contient à la fois les règles et les SANs prédéfinis.
code
Java
package com.bnpparibas.certis.dto;

import java.util.List;

public class ProfileRulesResponseDto {
    private List<SanRuleResponseDto> rules; // Les règles min/max
    private List<PredefinedSanDto> predefinedSans; // Les SANs pré-remplis

    // Getters et Setters
}
Fichier 3 : AutomationHubProfileServiceImpl.java (Méthode de récupération modifiée)
On modifie la méthode getSanRulesByCertificateType pour qu'elle construise ce nouvel objet de réponse.
code
Java
// Dans AutomationHubProfileServiceImpl.java

    /**
     * NOUVELLE VERSION de la méthode pour le controller.
     * Elle retourne maintenant un objet complexe contenant les règles ET les SANs prédéfinis.
     */
    public ProfileRulesResponseDto getSanRulesByCertificateType(Long typeId, Long subTypeId) {
        ProfileRulesResponseDto response = new ProfileRulesResponseDto();
        response.setRules(new ArrayList<>());
        response.setPredefinedSans(new ArrayList<>());

        try {
            AutomationHubProfile profile = this.findProfileEntityByTypeAndSubType(typeId, subTypeId);
            List<SanTypeRule> ruleEntities = sanTypeRuleRepository.findByAutomationHubProfile(profile);

            // 1. On convertit les règles en DTOs (comme avant)
            List<SanRuleResponseDto> ruleDtos = ruleEntities.stream()
                    .map(this::convertToSanRuleDto)
                    .collect(Collectors.toList());
            response.setRules(ruleDtos);

            // 2. NOUVELLE LOGIQUE : On cherche si un SAN de type CN est obligatoire.
            ruleEntities.stream()
                .filter(rule -> rule.getType() == SanType.CN && rule.getMinValue() >= 1)
                .findFirst()
                .ifPresent(cnRule -> {
                    // Si on trouve une règle qui exige au moins un CN...
                    PredefinedSanDto predefinedCn = new PredefinedSanDto();
                    predefinedCn.setType(SanType.CN);
                    // On utilise un placeholder que le front-end comprendra.
                    predefinedCn.setValue("{COMMON_NAME}");
                    
                    response.getPredefinedSans().add(predefinedCn);
                });

            return response;

        } catch (FailedToRetrieveProfileException e) {
            LOGGER.warn("Aucun profil technique trouvé pour le typeId {} / subTypeId {}.", typeId, subTypeId);
            return response; // Retourne un objet vide
        }
    }
Fichier 4 : RequestController.java (Endpoint mis à jour)
On change simplement le type de retour de l'endpoint.
code
Java
// Dans RequestController.java

    /**
     * Endpoint mis à jour pour retourner la nouvelle structure de réponse.
     */
    @GetMapping("/certificatetypes/{typeId}/san-rules")
    public ResponseEntity<ProfileRulesResponseDto> getSanRulesForCertificateType(
            @PathVariable Long typeId,
            @RequestParam(required = false) Long subTypeId) {
        
        ProfileRulesResponseDto response = automationHubProfileService.getSanRulesByCertificateType(typeId, subTypeId);
        
        return ResponseEntity.ok(response);
    }
Comment le Front-End va utiliser ça
Le front-end appelle l'API et reçoit la réponse ProfileRulesResponseDto.
Il affiche les règles (min/max) à l'utilisateur comme avant.
Il regarde la liste predefinedSans. S'il y trouve un objet, il sait qu'il doit :
Créer une ligne dans la liste des SANs.
Mettre son type à CN.
Observer le champ Common Name du formulaire. Dès que l'utilisateur tape quelque chose, il copie la valeur dans ce SAN prédéfini.
"Griser" cette ligne pour que l'utilisateur ne puisse pas la modifier ou la supprimer.
Le nombre de SANs que l'utilisateur peut ajouter est maintenant (max pour ce type) - (nombre de SANs prédéfinis pour ce type).
///////////////////////////////////////// version corriger ////////////////////////

Model
Vous avez absolument raison ! C'est une excellente remarque. Ma proposition précédente était incomplète sur ce point.
Déduire simplement le nombre de SANs prédéfinis du max est la bonne approche pour le front-end, mais le backend doit aussi fournir le max ajusté pour que le front n'ait aucun calcul complexe à faire.
La logique est la suivante :
Si un SAN de type CN est prédéfini (parce que min >= 1), alors la règle pour le type CN que nous envoyons au front-end doit avoir son max diminué de 1.
Voici la version corrigée et bien meilleure de la méthode dans AutomationHubProfileServiceImpl.
Code Corrigé de AutomationHubProfileServiceImpl.java (avec max ajusté)
code
Java
// Dans AutomationHubProfileServiceImpl.java

    /**
     * VERSION FINALE ET CORRIGÉE
     * Retourne les règles de SANs, en AJUSTANT les limites pour les SANs qui sont prédéfinis.
     */
    public ProfileRulesResponseDto getSanRulesByCertificateType(Long typeId, Long subTypeId) {
        ProfileRulesResponseDto response = new ProfileRulesResponseDto();
        
        try {
            AutomationHubProfile profile = this.findProfileEntityByTypeAndSubType(typeId, subTypeId);
            List<SanTypeRule> ruleEntities = sanTypeRuleRepository.findByAutomationHubProfile(profile);

            // --- NOUVELLE LOGIQUE D'AJUSTEMENT ---
            
            // 1. On détermine d'abord s'il y a un CN prédéfini.
            boolean isCnPredefined = ruleEntities.stream()
                .anyMatch(rule -> rule.getType() == SanType.CN && rule.getMinValue() >= 1);

            // 2. On prépare la liste des SANs prédéfinis (comme avant).
            List<PredefinedSanDto> predefinedSans = new ArrayList<>();
            if (isCnPredefined) {
                PredefinedSanDto predefinedCn = new PredefinedSanDto();
                predefinedCn.setType(SanType.CN);
                predefinedCn.setValue("{COMMON_NAME}");
                predefinedSans.add(predefinedCn);
            }
            
            // 3. On convertit les règles en DTOs, en appliquant l'ajustement.
            List<SanRuleResponseDto> adjustedRules = ruleEntities.stream()
                .map(ruleEntity -> {
                    SanRuleResponseDto dto = new SanRuleResponseDto();
                    dto.setType(entity.getType());
                    
                    int min = entity.getMinValue();
                    int max = entity.getMaxValue();

                    // --- C'EST LA CORRECTION CRUCIALE ---
                    // Si le type de la règle est CN ET qu'un CN est prédéfini...
                    if (isCnPredefined && entity.getType() == SanType.CN) {
                        // ... alors l'utilisateur doit fournir 1 CN de moins au minimum,
                        // et il peut en fournir 1 de moins au maximum.
                        min = Math.max(0, min - 1); // Le min ne peut pas être négatif
                        max = Math.max(0, max - 1); // Le max ne peut pas être négatif
                    }
                    
                    dto.setMin(min);
                    dto.setMax(max);
                    
                    return dto;
                })
                .collect(Collectors.toList());

            // On remplit l'objet de réponse final
            response.setRules(adjustedRules);
            response.setPredefinedSans(predefinedSans);
            
            return response;

        } catch (FailedToRetrieveProfileException e) {
            // En cas d'erreur, on retourne un objet vide.
            response.setRules(Collections.emptyList());
            response.setPredefinedSans(Collections.emptyList());
            return response;
        }
    }
Comment le Front-End va utiliser cette nouvelle réponse
Le code du front-end devient maintenant extrêmement simple. Il n'a plus de calcul à faire.
Scénario :
Règle en base : Pour CN, min=1, max=2.
Réponse de l'API :
code
JSON
{
  "rules": [
    { "type": "CN", "min": 0, "max": 1 },
    { "type": "DNSNAME", "min": 0, "max": 5 }
  ],
  "predefinedSans": [
    { "type": "CN", "value": "{COMMON_NAME}", "editable": false }
  ]
}
Le front-end fait ceci :
Il voit predefinedSans et affiche une ligne grisée pour le CN, liée au champ "Common Name".
Il regarde rules.
Pour le CN, il voit min: 0, max: 1. Il sait donc que l'utilisateur peut ajouter entre 0 et 1 autre CN de son choix.
Pour le DNSNAME, il voit min: 0, max: 5. Il sait que l'utilisateur peut ajouter entre 0 et 5 DNSNAME.
Le front-end n'a plus à se poser la question "est-ce que je dois déduire quelque chose ?". Toute la logique métier est centralisée dans le backend, ce qui est beaucoup plus robuste.
Vous aviez parfaitement raison de soulever ce point. Cette version est bien plus propre et plus facile à utiliser pour l'équipe front-end.
////////////////////////////// cas ou tous les cas ///////////////////////////////
Oui, vous avez tout à fait raison. Il faut gérer tous les cas de figure pour que la logique soit parfaitement robuste. Votre exemple min=0, max=1 est très pertinent.
Analysons tous les cas possibles pour la règle du CN et définissons le comportement attendu.
Analyse des Cas de Figure
Soit (min, max) la règle pour le type CN stockée dans votre base de données.
Cas 1 : min = 0 (Le CN n'est pas obligatoire en tant que SAN)
Règle en BDD : min=0, max=0
Comportement attendu : Aucun SAN de type CN n'est autorisé. Il n'y a pas de SAN prédéfini.
Réponse API : predefinedSans est vide. La règle pour CN est min: 0, max: 0.
Règle en BDD : min=0, max=1 (ou plus)
Comportement attendu : L'utilisateur peut ajouter des SANs de type CN, mais aucun n'est obligatoire ou prédéfini. La règle "le CN doit être dans les SANs" ne s'applique pas.
Réponse API : predefinedSans est vide. La règle pour CN reste min: 0, max: 1.
Conclusion pour min = 0 : Si le min est zéro, la règle du "CN miroir" ne s'applique pas. Il n'y a pas de SAN prédéfini et les règles min/max ne sont pas modifiées.
Cas 2 : min >= 1 (Le CN est obligatoire en tant que SAN)
C'est ici que la logique d'ajustement s'applique.
Règle en BDD : min=1, max=1
Comportement attendu : Exactement un SAN de type CN est requis, et il doit être le Common Name. L'utilisateur ne peut en ajouter aucun autre.
Réponse API :
predefinedSans: contient le CN miroir.
rules: la règle pour CN devient min: 0, max: 0. (1-1=0, 1-1=0)
Règle en BDD : min=1, max=2
Comportement attendu : Au moins un SAN de type CN est requis. L'un d'eux est obligatoirement le Common Name. L'utilisateur peut en ajouter un seul autre de son choix.
Réponse API :
predefinedSans: contient le CN miroir.
rules: la règle pour CN devient min: 0, max: 1. (1-1=0, 2-1=1)
Règle en BDD : min=2, max=5
Comportement attendu : Au moins deux SANs de type CN sont requis. L'un est le Common Name. L'utilisateur doit en ajouter au moins un autre, et peut en ajouter jusqu'à quatre autres.
Réponse API :
predefinedSans: contient le CN miroir.
rules: la règle pour CN devient min: 1, max: 4. (2-1=1, 5-1=4)
Le Code Final qui Gère Tous les Cas
Le code que je vous ai fourni dans la réponse précédente gère déjà tous ces cas de figure correctement. Analysons-le à nouveau avec cette grille de lecture.
Fichier : AutomationHubProfileServiceImpl.java
code
Java
public ProfileRulesResponseDto getSanRulesByCertificateType(Long typeId, Long subTypeId) {
        ProfileRulesResponseDto response = new ProfileRulesResponseDto();
        
        try {
            AutomationHubProfile profile = this.findProfileEntityByTypeAndSubType(typeId, subTypeId);
            List<SanTypeRule> ruleEntities = sanTypeRuleRepository.findByAutomationHubProfile(profile);

            // --- Étape 1 : On détermine si un CN est prédéfini ---
            // Cette condition ne sera vraie que si une règle pour CN existe ET que son min >= 1.
            // C'est exactement la logique que nous avons définie.
            boolean isCnPredefined = ruleEntities.stream()
                .anyMatch(rule -> rule.getType() == SanType.CN && rule.getMinValue() >= 1);

            // --- Étape 2 : On construit la liste des SANs prédéfinis ---
            List<PredefinedSanDto> predefinedSans = new ArrayList<>();
            if (isCnPredefined) {
                PredefinedSanDto predefinedCn = new PredefinedSanDto();
                predefinedCn.setType(SanType.CN);
                predefinedCn.setValue("{COMMON_NAME}");
                predefinedSans.add(predefinedCn);
            }
            
            // --- Étape 3 : On construit les règles ajustées pour le front-end ---
            List<SanRuleResponseDto> adjustedRules = ruleEntities.stream()
                .map(entity -> {
                    SanRuleResponseDto dto = new SanRuleResponseDto();
                    dto.setType(entity.getType());
                    
                    int min = entity.getMinValue();
                    int max = entity.getMaxValue();

                    // L'ajustement ne se produit que si le CN est prédéfini ET que la règle actuelle concerne le CN.
                    // Si min=0 pour le CN, `isCnPredefined` sera false et ce bloc ne sera jamais exécuté.
                    if (isCnPredefined && entity.getType() == SanType.CN) {
                        // On décrémente min et max de 1.
                        // Math.max(0, ...) garantit que le résultat ne sera jamais négatif.
                        min = Math.max(0, min - 1); 
                        max = Math.max(0, max - 1); 
                    }
                    
                    dto.setMin(min);
                    dto.setMax(max);
                    
                    return dto;
                })
                .collect(Collectors.toList());

            // --- Étape 4 : On assemble la réponse finale ---
            response.setRules(adjustedRules);
            response.setPredefinedSans(predefinedSans);
            
            return response;

        } catch (FailedToRetrieveProfileException e) {
            // Gestion d'erreur
            response.setRules(Collections.emptyList());
            response.setPredefinedSans(Collections.emptyList());
            return response;
        }
    }
Conclusion :
Le code que nous avons écrit est déjà correct et gère bien tous les cas de figure. La clé est la variable booléenne isCnPredefined. 
Elle n'est true que si min >= 1 pour le CN, ce qui déclenche la logique de "réservation" et d'ajustement. Si min = 0, isCnPredefined 
sera false et les règles sont retournées telles quelles, ce qui est le comportement attendu.
//////////////////pris en compte des nouvelles regle///////////////////////////////
Absolument. Voici une récapitulation complète de la solution finale, intégrant toutes vos clarifications.
Cette solution est conçue pour être la plus claire et la plus robuste possible, en centralisant la logique métier dans le backend pour simplifier le travail du front-end.
Fichier 1 : ProfileRulesResponseDto.java (Nouveau DTO de réponse)
Ce DTO est l'objet que notre API retournera. Il est composé de deux listes : une pour les règles min/max et une pour les SANs qui doivent être pré-remplis.
Emplacement : Dans un package DTO, ex: com.bnpparibas.certis.dto
code
Java
package com.bnpparibas.certis.dto;

import java.util.List;

public class ProfileRulesResponseDto {

    private List<SanRuleResponseDto> rules;
    private List<PredefinedSanDto> predefinedSans;

    // Getters et Setters
    public List<SanRuleResponseDto> getRules() { return rules; }
    public void setRules(List<SanRuleResponseDto> rules) { this.rules = rules; }
    public List<PredefinedSanDto> getPredefinedSans() { return predefinedSans; }
    public void setPredefinedSans(List<PredefinedSanDto> predefinedSans) { this.predefinedSans = predefinedSans; }
}
Fichier 2 : PredefinedSanDto.java (Nouveau DTO)
Ce DTO décrit un SAN pré-rempli.
Emplacement : Au même endroit que ProfileRulesResponseDto.
code
Java
package com.bnpparibas.certis.dto;

import com.bnpparibas.certis.certificate.request.enums.SanType;

public class PredefinedSanDto {

    private SanType type;
    private String value; // Contient un placeholder comme "{COMMON_NAME}"
    private boolean editable = false; // Toujours false pour indiquer au front de le griser

    // Getters et Setters
    public SanType getType() { return type; }
    public void setType(SanType type) { this.type = type; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public boolean isEditable() { return editable; }
    public void setEditable(boolean editable) { this.editable = editable; }
}
Fichier 3 : AutomationHubProfileServiceImpl.java (La Logique Principale)
Voici la méthode complète et finale qui implémente toutes vos règles.
Action : Remplacez votre méthode getSanRulesByCertificateType par celle-ci.
code
Java
// Dans AutomationHubProfileServiceImpl.java

    /**
     * Méthode finale qui récupère les règles de SANs pour un type/sous-type donné,
     * en appliquant la logique métier spécifique au Common Name (CN).
     *
     * LA RÈGLE MÉTIER IMPLÉMENTÉE :
     * Si la règle pour le type de SAN "CN" a un `min` de 1, alors :
     *   1. Un SAN de type CN est considéré comme "prédéfini" et sa valeur sera le Common Name du certificat.
     *   2. Les limites min/max pour le type CN retournées à l'API sont décrémentées de 1.
     * Dans tous les autres cas (min=0 pour CN, ou pour les autres types de SAN), les règles sont retournées telles quelles.
     */
    public ProfileRulesResponseDto getSanRulesByCertificateType(Long typeId, Long subTypeId) {
        ProfileRulesResponseDto response = new ProfileRulesResponseDto();
        
        try {
            // ÉTAPE 1 : Récupérer le profil technique et ses règles depuis la base de données.
            AutomationHubProfile profile = this.findProfileEntityByTypeAndSubType(typeId, subTypeId);
            List<SanTypeRule> ruleEntities = sanTypeRuleRepository.findByAutomationHubProfile(profile);

            // --- Logique d'ajustement des règles ---
            
            // ÉTAPE 2 : Déterminer si un SAN de type CN doit être prédéfini.
            // On cherche la règle spécifique au type CN dans la liste.
            Optional<SanTypeRule> cnRuleOptional = ruleEntities.stream()
                .filter(rule -> rule.getType() == SanType.CN)
                .findFirst();

            boolean isCnPredefined = false;
            // La condition est très stricte : uniquement si la règle pour CN existe ET que son min est 1.
            if (cnRuleOptional.isPresent() && cnRuleOptional.get().getMinValue() == 1) {
                isCnPredefined = true;
            }

            // ÉTAPE 3 : Préparer la liste des SANs prédéfinis pour le front-end.
            List<PredefinedSanDto> predefinedSans = new ArrayList<>();
            if (isCnPredefined) {
                PredefinedSanDto predefinedCn = new PredefinedSanDto();
                predefinedCn.setType(SanType.CN);
                // On envoie un "placeholder" que le front-end saura interpréter.
                predefinedCn.setValue("{COMMON_NAME}");
                predefinedCn.setEditable(false); // Explicitement non éditable
                predefinedSans.add(predefinedCn);
            }
            
            // ÉTAPE 4 : Préparer la liste des règles (min/max) ajustées pour le front-end.
            List<SanRuleResponseDto> adjustedRules = ruleEntities.stream()
                .map(entity -> {
                    SanRuleResponseDto dto = new SanRuleResponseDto();
                    dto.setType(entity.getType());
                    
                    int min = entity.getMinValue();
                    int max = entity.getMaxValue();

                    // On applique l'ajustement SEULEMENT si le CN est prédéfini ET que la règle actuelle est celle du CN.
                    if (isCnPredefined && entity.getType() == SanType.CN) {
                        // On décrémente car une place est déjà "réservée" par le SAN prédéfini.
                        // Math.max(0, ...) est une sécurité pour que les valeurs ne deviennent pas négatives.
                        min = Math.max(0, min - 1); 
                        max = Math.max(0, max - 1); 
                    }
                    
                    dto.setMin(min);
                    dto.setMax(max);
                    
                    return dto;
                })
                .collect(Collectors.toList());

            // ÉTAPE 5 : Assembler l'objet de réponse final.
            response.setRules(adjustedRules);
            response.setPredefinedSans(predefinedSans);
            
            return response;

        } catch (FailedToRetrieveProfileException e) {
            // Si le profil n'est pas trouvé, on retourne un objet vide, ce qui est simple à gérer pour le front.
            LOGGER.warn("Aucun profil technique trouvé pour le typeId {} / subTypeId {}. Retour d'une réponse vide.", typeId, subTypeId);
            response.setRules(Collections.emptyList());
            response.setPredefinedSans(Collections.emptyList());
            return response;
        }
    }
N'oubliez pas le Controller
Le controller doit être mis à jour pour retourner le nouveau type d'objet ProfileRulesResponseDto.
Fichier : RequestController.java
code
Java
// Dans RequestController.java

    @GetMapping("/certificatetypes/{typeId}/san-rules")
    public ResponseEntity<ProfileRulesResponseDto> getSanRulesForCertificateType( // <-- Type de retour changé
            @PathVariable Long typeId,
            @RequestParam(required = false) Long subTypeId) {
        
        ProfileRulesResponseDto response = automationHubProfileService.getSanRulesByCertificateType(typeId, subTypeId);
        
        return ResponseEntity.ok(response);
    }
Avec ces fichiers, votre solution est complète, robuste et implémente exactement la logique métier que vous avez définie.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.

/////////////////// front ///////////////////////
Étape 1 : Créer les modèles de données (Interfaces TypeScript)
C'est une bonne pratique de créer des interfaces qui correspondent aux DTOs de votre backend. Créez un nouveau fichier, par exemple san-rules.model.ts, ou ajoutez-les à un fichier de modèles existant.
code
TypeScript
// san-rules.model.ts

// On peut utiliser un type string literal pour SanType pour plus de sécurité
export type SanType = 'CN' | 'DNSNAME' | 'IPADDRESS' | 'RFC822NAME' | 'URI' | 'OTHERNAME_GUID' | 'OTHERNAME_UPN';

export interface SanRuleResponseDto {
  type: SanType;
  min: number;
  max: number;
}

export interface PredefinedSanDto {
  type: SanType;
  value: string; // Contient le placeholder "{COMMON_NAME}"
  editable: boolean; // Sera toujours false
}

export interface ProfileRulesResponseDto {
  rules: SanRuleResponseDto[];
  predefinedSans: PredefinedSanDto[];
}
Étape 2 : Mettre à jour le Service Angular
Dans votre service qui gère les appels à l'API (probablement RequestService ou similaire), ajoutez une nouvelle méthode pour appeler notre endpoint.
Fichier : request.service.ts (ou le nom de votre service)
code
TypeScript
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ProfileRulesResponseDto } from './san-rules.model'; // Importez votre nouveau modèle

@Injectable({
  providedIn: 'root'
})
export class RequestService {

  constructor(private http: HttpClient) { }

  // ... (vos autres méthodes de service existantes)

  /**
   * NOUVELLE MÉTHODE
   * Récupère les règles de SANs pour un type/sous-type de certificat donné.
   * @param typeId L'ID du CertificateType.
   * @param subTypeId L'ID optionnel du CertificateSubType.
   */
  getSanRulesForCertificateType(typeId: number, subTypeId?: number): Observable<ProfileRulesResponseDto> {
    const baseUrl = `/api/v1/request/certificatetypes/${typeId}/san-rules`; // Adaptez si le préfixe est différent
    
    let params = new HttpParams();
    if (subTypeId) {
      params = params.set('subTypeId', subTypeId.toString());
    }

    return this.http.get<ProfileRulesResponseDto>(baseUrl, { params });
  }
}
Étape 3 : Modifier le Composant RequestDetailSectionComponent
C'est ici que se trouve le cœur de la logique.
Fichier : request-detail-section.component.ts
code
TypeScript
// ... (imports existants)
import { FormArray, FormGroup, FormBuilder, Validators } from '@angular/forms';
import { RequestService } from '...'; // Importez votre service
import { ProfileRulesResponseDto, SanRuleResponseDto, PredefinedSanDto, SanType } from '...'; // Importez vos modèles

@Component({
  // ...
})
export class RequestDetailSectionComponent implements OnInit, OnDestroy {
  
  // ... (toutes vos propriétés existantes)

  // --- NOUVELLES PROPRIÉTÉS ---
  sanRules: SanRuleResponseDto[] = [];
  predefinedSans: PredefinedSanDto[] = [];
  
  // Assurez-vous d'injecter votre service dans le constructeur
  constructor(private fb: FormBuilder, private requestService: RequestService, /* ... autres injections */) {
    // ...
  }

  ngOnInit() {
    // ... (votre code ngOnInit existant)

    // On s'abonne aux changements du type de certificat pour déclencher notre logique
    this.listenForCertificateTypeChanges();
    this.listenForCommonNameChanges();
  }
  
  /**
   * NOUVELLE MÉTHODE : Écoute les changements sur le champ du type de certificat.
   */
  private listenForCertificateTypeChanges(): void {
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    
    if (certificateTypeControl) {
      certificateTypeControl.valueChanges
        .pipe(takeUntil(this.onDestroy$)) // Assurez-vous d'avoir un mécanisme pour vous désabonner
        .subscribe(selectedType => {
          if (selectedType && selectedType.id) {
            const subTypeId = this.requestDetailSectionForm.get('certificateSubType')?.value?.id;
            this.fetchAndApplySanRules(selectedType.id, subTypeId);
          } else {
            // Si l'utilisateur dé-sélectionne, on vide les règles et les SANs
            this.clearSanRulesAndControls();
          }
        });
    }
  }

  /**
   * NOUVELLE MÉTHODE : Récupère les règles depuis l'API et met à jour le formulaire.
   */
  private fetchAndApplySanRules(typeId: number, subTypeId?: number): void {
    this.requestService.getSanRulesForCertificateType(typeId, subTypeId)
      .subscribe(response => {
        // 1. On stocke les règles et les SANs prédéfinis
        this.sanRules = response.rules || [];
        this.predefinedSans = response.predefinedSans || [];
        
        // 2. On met à jour le FormArray des SANs
        const sansFormArray = this.requestDetailSectionForm.get('sans') as FormArray;
        sansFormArray.clear(); // On vide les anciens SANs

        // 3. On ajoute les SANs prédéfinis (ex: le CN miroir)
        this.predefinedSans.forEach(predefined => {
          const sanGroup = this.createSanGroup(); // On utilise votre méthode existante
          
          let initialValue = predefined.value;
          if (initialValue === '{COMMON_NAME}') {
            initialValue = this.requestDetailSectionForm.get('certificateName')?.value || '';
          }
          
          sanGroup.patchValue({
            type: predefined.type,
            value: initialValue
          });

          // On désactive le groupe pour que l'utilisateur ne puisse pas le modifier
          sanGroup.disable();
          
          sansFormArray.push(sanGroup);
        });

        // 4. (Optionnel) Ajouter un SAN vide si min > 0 pour un type non prédéfini
        // ...
      });
  }

  /**
   * NOUVELLE MÉTHODE : Gère la mise à jour du SAN prédéfini lorsque le Common Name change.
   */
  private listenForCommonNameChanges(): void {
    const commonNameControl = this.requestDetailSectionForm.get('certificateName');
    if (commonNameControl) {
      commonNameControl.valueChanges
        .pipe(takeUntil(this.onDestroy$))
        .subscribe(cnValue => {
          const sansFormArray = this.requestDetailSectionForm.get('sans') as FormArray;
          // On cherche l'index du SAN prédéfini (celui qui est désactivé)
          const predefinedSanIndex = this.predefinedSans.length > 0 ? 0 : -1; // Simplification : on suppose que c'est le premier

          if (predefinedSanIndex !== -1 && this.predefinedSans[predefinedSanIndex].value === '{COMMON_NAME}') {
            const predefinedSanControl = sansFormArray.at(predefinedSanIndex);
            if (predefinedSanControl) {
              predefinedSanControl.get('value')?.setValue(cnValue, { emitEvent: false });
            }
          }
        });
    }
  }
  
  /**
   * NOUVELLE MÉTHODE : Pour réinitialiser
   */
  private clearSanRulesAndControls(): void {
      this.sanRules = [];
      this.predefinedSans = [];
      const sansFormArray = this.requestDetailSectionForm.get('sans') as FormArray;
      sansFormArray.clear();
      // Peut-être ajouter un SAN vide par défaut
      // sansFormArray.push(this.createSanGroup());
  }

  // --- MÉTHODES EXISTANTES À MODIFIER ---

  /**
   * MODIFIÉ : Vérifie si l'utilisateur peut ajouter un nouveau SAN.
   */
  canAddSan(): boolean {
    const sansFormArray = this.requestDetailSectionForm.get('sans') as FormArray;
    
    // On calcule le nombre total maximum de SANs autorisés
    const totalMax = this.sanRules.reduce((sum, rule) => sum + rule.max, 0);
    const maxWithPredefined = totalMax + this.predefinedSans.length;
    
    // On ne peut ajouter un SAN que si on n'a pas atteint la limite globale.
    return sansFormArray.length < maxWithPredefined;
  }
  
  /**
   * MODIFIÉ : Vérifie si un SAN peut être supprimé.
   * @param index L'index du SAN dans le FormArray.
   */
  canDeleteSan(index: number): boolean {
      const sansFormArray = this.requestDetailSectionForm.get('sans') as FormArray;
      const sanGroup = sansFormArray.at(index) as FormGroup;

      // On ne peut pas supprimer un SAN prédéfini (ceux qui sont désactivés)
      if (sanGroup.disabled) {
          return false;
      }
      
      // Calcule le nombre total minimum de SANs requis
      const totalMin = this.sanRules.reduce((sum, rule) => sum + rule.min, 0);
      const minWithPredefined = totalMin + this.predefinedSans.length;

      // On ne peut supprimer que si le nombre actuel est supérieur au minimum requis
      return sansFormArray.length > minWithPredefined;
  }

  // Votre méthode createSanGroup() reste probablement la même.
}
Résumé du Flux Côté Front-end
Initialisation : Le composant s'initialise et commence à écouter les changements sur le champ certificateType.
Choix de l'utilisateur : L'utilisateur sélectionne un type de certificat.
Appel API : La méthode listenForCertificateTypeChanges détecte le changement, récupère l'ID et appelle fetchAndApplySanRules.
Récupération des règles : fetchAndApplySanRules appelle votre service, qui fait la requête GET à l'endpoint /certificatetypes/{typeId}/san-rules.
Mise à jour du formulaire :
Le service retourne la réponse (ProfileRulesResponseDto).
La méthode vide l'ancien FormArray de SANs.
Elle ajoute les SANs prédéfinis (predefinedSans) en tant que champs désactivés.
La méthode listenForCommonNameChanges s'assure que la valeur de ce SAN désactivé est toujours synchronisée avec le champ "Common Name".
Validation dynamique : Les méthodes canAddSan et canDeleteSan utilisent maintenant les sanRules et predefinedSans reçus de l'API pour décider si les boutons "+" et "-" doivent être actifs, au lieu d'utiliser des valeurs codées en dur.
Cette approche s'intègre proprement dans votre architecture existante et rend votre formulaire de SANs entièrement dynamique et piloté par le backend.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
///////////////////////////////////////// front /////////////////////////////////////////
Résumé du Code Final pour Angular
Étape 1 : Fichier de Modèles (san-rules.model.ts)
Créez ce fichier pour définir la structure des données que vous recevrez de l'API. C'est la base pour un code propre et typé.
Emplacement suggéré : src/app/shared/beans/san-rules.model.ts
code
TypeScript
export type SanType = 'CN' | 'DNSNAME' | 'IPADDRESS' | 'RFC822NAME' | 'URI' | 'OTHERNAME_GUID' | 'OTHERNAME_UPN';

export interface SanRuleResponseDto {
  type: SanType;
  min: number;
  max: number;
}

export interface PredefinedSanDto {
  type: SanType;
  value: string;
  editable: boolean;
}

export interface ProfileRulesResponseDto {
  rules: SanRuleResponseDto[];
  predefinedSans: PredefinedSanDto[];
}
Étape 2 : Fichier de Service (request.service.ts)
Ajoutez la nouvelle méthode à votre service existant pour communiquer avec le backend.
Fichier à modifier : src/app/services/request.service.ts
code
TypeScript
// ... (imports existants)
import { Observable } from 'rxjs';
import { ProfileRulesResponseDto } from '../shared/beans/san-rules.model';

// ...
export class RequestService {
  
  // ... (constructeur et méthodes existantes)

  // --- NOUVELLE MÉTHODE À AJOUTER ---
  getSanRulesForCertificateType(typeId: number, subTypeId?: number): Observable<ProfileRulesResponseDto> {
    const baseUrl = `/api/v1/request/certificatetypes/${typeId}/san-rules`;

    const params: { [param: string]: string } = {};
    if (subTypeId) {
      params['subTypeId'] = subTypeId.toString();
    }

    // On utilise votre 'apiService' existant pour faire l'appel
    return this.apiService.get<ProfileRulesResponseDto>(baseUrl, params);
  }
}
Étape 3 : Fichier du Composant (request-detail-section.component.ts)
C'est ici que se trouve la majorité des changements. On va rendre la gestion des SANs dynamique.
Fichier à modifier : src/app/content/form/request-detail-section/request-detail-section.component.ts
code
TypeScript
// --- Imports à ajouter ou vérifier ---
import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormArray, FormGroup, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil, distinctUntilChanged } from 'rxjs/operators';
import { RequestService } from '...'; // Adaptez le chemin
import { ProfileRulesResponseDto, SanRuleResponseDto, PredefinedSanDto, SanType } from '...'; // Adaptez le chemin

// ...
@Component({
  selector: 'app-request-detail-section',
  // ...
})
export class RequestDetailSectionComponent implements OnInit, OnDestroy {
  
  // ... (toutes vos propriétés existantes : requestDetailSectionForm, etc.)

  // --- NOUVELLES PROPRIÉTÉS ---
  private onDestroy$ = new Subject<void>();
  public sanRules: SanRuleResponseDto[] = []; // public pour être accessible depuis le template HTML
  
  // --- Mettre à jour le constructeur ---
  constructor(private requestService: RequestService, /* ... autres injections existantes */) {
    // ...
  }
  
  // --- Mettre à jour ngOnInit ---
  ngOnInit(): void {
    // ... (votre code ngOnInit existant)

    this.listenForCertificateTypeChanges();
    this.listenForCommonNameChanges();
  }

  // --- Ajouter ngOnDestroy ---
  ngOnDestroy(): void {
    this.onDestroy$.next();
    this.onDestroy$.complete();
  }

  // --- Getter pour les SANs (vous l'avez déjà) ---
  get sans(): FormArray {
    return this.requestDetailSectionForm.get('sans') as FormArray;
  }

  // --- NOUVELLES MÉTHODES PRIVÉES ---

  private listenForCertificateTypeChanges(): void {
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    if (certificateTypeControl) {
      certificateTypeControl.valueChanges
        .pipe(
          takeUntil(this.onDestroy$),
          distinctUntilChanged((prev, curr) => prev?.id === curr?.id) // Évite les appels inutiles
        )
        .subscribe(selectedType => {
          const subTypeControl = this.requestDetailSectionForm.get('certificateSubType');
          const subTypeId = subTypeControl?.value?.id;
          
          if (selectedType && selectedType.id) {
            this.fetchAndApplySanRules(selectedType.id, subTypeId);
          } else {
            this.clearSanRulesAndControls();
          }
        });
    }
  }

  private fetchAndApplySanRules(typeId: number, subTypeId?: number): void {
    this.requestService.getSanRulesForCertificateType(typeId, subTypeId)
      .pipe(takeUntil(this.onDestroy$))
      .subscribe(response => {
        this.sanRules = response.rules || [];
        const predefinedSans = response.predefinedSans || [];
        
        this.sans.clear();

        predefinedSans.forEach(predefined => {
          const sanGroup = this.createSanGroup(); // Votre méthode existante
          
          const initialValue = predefined.value === '{COMMON_NAME}'
            ? this.requestDetailSectionForm.get('certificateName')?.value || ''
            : predefined.value;
          
          sanGroup.patchValue({ type: predefined.type, value: initialValue });
          sanGroup.disable(); // Grise le champ
          this.sans.push(sanGroup);
        });
      });
  }
  
  private listenForCommonNameChanges(): void {
    const commonNameControl = this.requestDetailSectionForm.get('certificateName');
    if (commonNameControl) {
      commonNameControl.valueChanges
        .pipe(takeUntil(this.onDestroy$))
        .subscribe(cnValue => {
          // On cherche s'il y a un SAN prédéfini et désactivé
          const predefinedSanControl = this.sans.controls.find(control => control.disabled);
          if (predefinedSanControl) {
            predefinedSanControl.get('value')?.setValue(cnValue, { emitEvent: false });
          }
        });
    }
  }
  
  private clearSanRulesAndControls(): void {
    this.sanRules = [];
    this.sans.clear();
    // On peut ajouter un champ SAN vide si c'est le comportement par défaut
    // this.addSan(); 
  }

  // --- MÉTHODES PUBLIQUES POUR LE TEMPLATE HTML ---

  // Votre méthode addSan() existe déjà et reste la même
  // addSan(): void { this.sans.push(this.createSanGroup()); }

  // Votre méthode removeSan(index: number) existe déjà et reste la même
  // removeSan(index: number): void { this.sans.removeAt(index); }
  
  /**
   * NOUVELLE MÉTHODE (ou MODIFIÉE) pour le template, pour activer/désactiver le bouton "Ajouter un SAN".
   */
  canAddSan(): boolean {
    if (this.sanRules.length === 0) {
      return false; // Pas de règles, pas d'ajout
    }
    const maxUserSans = this.sanRules.reduce((sum, rule) => sum + rule.max, 0);
    const currentUserSans = this.sans.controls.filter(control => control.enabled).length;
    
    return currentUserSans < maxUserSans;
  }
}
Étape 4 : Adapter le Template HTML (request-detail-section.component.html)
Vous devrez faire de petites modifications dans votre template pour utiliser la nouvelle logique.
Le bouton "Ajouter un SAN" :
Liez sa propriété [disabled] à la nouvelle méthode canAddSan().
code
Html
<button (click)="addSan()" [disabled]="!canAddSan()">Ajouter un SAN</button>
Affichage des champs SANs :
Votre *ngFor qui boucle sur sans.controls est déjà correct.
À l'intérieur de la boucle, vous devrez peut-être ajuster la validation. Par exemple, si vous voulez afficher un message d'erreur "min/max", vous pouvez utiliser la variable sanRules.
Le bouton "Supprimer" pour chaque SAN :
Il est important de ne pas pouvoir supprimer un SAN prédéfini.
Un SAN prédéfini est disabled. Vous pouvez utiliser cette propriété.
code
Html
<!-- À l'intérieur du *ngFor="let sanGroup of sans.controls; let i = index" -->
<button (click)="removeSan(i)" *ngIf="sanGroup.enabled">Supprimer</button>
L'attribut *ngIf="sanGroup.enabled" n'affichera le bouton de suppression que pour les SANs que l'utilisateur a ajoutés lui-même.
Ce résumé vous donne tous les éléments nécessaires pour intégrer la fonctionnalité dans votre code Angular existant de manière propre et robuste.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
//////////////////// back revue /////////////
Absolument. C'est une clarification cruciale, et vous avez tout à fait raison de le souligner. Ma focalisation sur le DNSNAME dans les explications a pu être trompeuse.
La bonne nouvelle, c'est que le code que nous avons finalisé est déjà conçu pour gérer cela parfaitement. La logique est générique pour tous les types de SANs, et il n'y a qu'une petite partie "spéciale" qui ne s'applique qu'au DNSNAME.
Laissez-moi vous le prouver en décomposant le code pour chaque partie du système.
Le Principe de la Solution : Générique par Défaut, Spécifique par Exception
Notre solution est construite sur ce principe :
Logique Générique (pour TOUS les types) : La validation des min/max et l'affichage des comptes s'appliquent à tous les types de SANs (IPADDRESS, URI, RFC822NAME, etc.) de la même manière.
Logique Spécifique (pour DNSNAME uniquement) : Il y a une seule et unique exception à la logique générique : si la règle pour DNSNAME est obligatoire (min >= 1), alors on pré-remplit un champ DNSNAME avec le Common Name et on ajuste les min/max uniquement pour le DNSNAME.
Voici comment le code met cela en pratique.
1. Côté Backend : AutomationHubProfileServiceImpl.java (L'API pour le front)
Cette méthode prépare les règles pour le front-end.
code
Java
public ProfileRulesResponseDto getSanRulesByCertificateType(Long typeId, Long subTypeId) {
    // ...
    List<SanTypeRule> ruleEntities = sanTypeRuleRepository.findByAutomationHubProfile(profile);

    // DÉBUT DE LA LOGIQUE SPÉCIFIQUE (DNSNAME)
    boolean isCnAsDnsPredefined = ruleEntities.stream()
        .anyMatch(rule -> rule.getType() == SanType.DNSNAME && rule.getMinValue() >= 1);
    
    if (isCnAsDnsPredefined) {
        // On crée un SAN prédéfini de type DNSNAME
    }
    // FIN DE LA LOGIQUE SPÉCIFIQUE

    // DÉBUT DE LA LOGIQUE GÉNÉRIQUE (POUR TOUS LES TYPES)
    List<SanRuleResponseDto> adjustedRules = ruleEntities.stream()
        .map(entity -> { // Cette boucle passe sur CHAQUE règle (IPADDRESS, URI, DNSNAME, etc.)
            SanRuleResponseDto dto = new SanRuleResponseDto();
            dto.setType(entity.getType());
            
            int min = entity.getMinValue();
            int max = entity.getMaxValue();

            // La condition "if" ici garantit que l'ajustement ne s'applique QUE si la règle actuelle est celle du DNSNAME.
            // Pour toutes les autres règles (IPADDRESS, URI, etc.), cette condition est fausse,
            // et les min et max de la base de données sont retournés sans modification.
            if (isCnAsDnsPredefined && entity.getType() == SanType.DNSNAME) {
                min = Math.max(0, min - 1); 
                max = Math.max(0, max - 1); 
            }
            
            dto.setMin(min);
            dto.setMax(max);
            
            return dto;
        })
        .collect(Collectors.toList());
    // FIN DE LA LOGIQUE GÉNÉRIQUE
    
    // ...
}
Conclusion pour cette partie : Le code prépare bien les règles pour tous les types et n'applique la modification que sur le DNSNAME si nécessaire.
2. Côté Backend : SanServiceImpl.java (La validation)
Cette méthode valide les demandes entrantes.
code
Java
private void verifySansLimitsDynamically(RequestDto requestDto) throws CertisRequestException {
    // ...
    // Le code qui trouve le profil et charge les `rules` est générique.
    List<SanTypeRule> rules = sanTypeRuleRepository.findByAutomationHubProfile(profile);
    
    // La map `sanCountsByType` compte tous les types de SANs soumis par l'utilisateur.
    Map<SanType, Long> sanCountsByType = ...

    // --- DÉBUT DE LA LOGIQUE GÉNÉRIQUE ---
    
    // Validation 1 : Vérifie les types non autorisés.
    // Cette boucle passe sur TOUS les types soumis par l'utilisateur.
    for (SanType requestedSanType : sanCountsByType.keySet()) {
        // ...
    }

    // Validation 2 : Vérifie les min/max.
    // Cette boucle passe sur TOUTES les règles de la base de données (IPADDRESS, URI, etc.).
    for (SanTypeRule rule : rules) {
        SanType sanTypeInRule = rule.getType();
        long countInRequest = sanCountsByType.getOrDefault(sanTypeInRule, 0L);
        
        // La validation min/max est appliquée à chaque règle, quelle que soit son type.
        if (countInRequest > rule.getMaxValue()) {
            // ... erreur
        }
        if (countInRequest < rule.getMinValue()) {
            // ... erreur
        }
    }
    // --- FIN DE LA LOGIQUE GÉNÉRIQUE ---
}
Conclusion pour cette partie : La validation est entièrement générique. Elle prendra chaque règle de votre table SAN_TYPE_RULE et l'appliquera, que ce soit pour IPADDRESS, OTHERNAME_GUID ou DNSNAME.
3. Côté Frontend : request-detail-section.component.ts (L'affichage)
Cette méthode construit les messages pour l'utilisateur.
code
TypeScript
// Dans request-detail-section.component.ts

/**
 * Cette méthode est 100% générique.
 * Elle fonctionne pour n'importe quel `SanType` qu'on lui passe en paramètre.
 */
public getSanCountMessage(sanType: SanType): string {
    // 1. On trouve la règle pour le type demandé (peut être 'IPADDRESS', 'URI'...)
    const rule = this.sanRules.find(r => r.type === sanType);
    if (!rule) { return ''; }

    // 2. On compte combien de SANs de CE type l'utilisateur a saisis.
    const currentCount = this.sans.controls
      .filter(control => control.enabled && control.get('type')?.value === sanType)
      .length;
      
    // 3. On construit le message. La logique est la même pour tous les types.
    // ...
}
Conclusion pour cette partie : L'affichage est également générique. Le template HTML va simplement boucler sur toutes les règles reçues de l'API (*ngFor="let rule of sanRules") et appeler getSanCountMessage(rule.type) pour chacune, ce qui affichera un statut pour chaque type de SAN autorisé.
Résumé Final :
Vous pouvez être certain que le système respectera bien les règles min et max pour tous les types de SANs définis dans votre base de données. La seule chose "spéciale" qui arrive au DNSNAME est qu'il peut être pré-rempli et que ses limites sont ajustées en conséquence. Pour tous les autres types, les règles de la base de données sont appliquées telles quelles.
/////////////////// ////////////////////

Model
Excellente question. C'est un détail de nommage qui peut prêter à confusion.
Dans le code Angular que nous avons écrit, il y a une méthode :
private listenForCommonNameChanges(): void
Et à l'intérieur de fetchAndApplySanRules, il y a cette ligne :
const initialValue = predefined.value === '{COMMON_NAME}' ? this.requestDetailSectionForm.get('certificateName')?.value || '' : predefined.value;
À quoi sert {COMMON_NAME} ?
"{COMMON_NAME}" n'est pas une variable ou une fonction. C'est une simple chaîne de caractères "magique", un placeholder.
C'est un contrat que nous avons défini entre le backend et le frontend.
Le Backend dit :
"Je vais t'envoyer une liste de SANs prédéfinis. Si l'un d'eux a la valeur {COMMON_NAME}, cela ne veut pas dire que tu dois afficher {COMMON_NAME} à l'utilisateur. Cela signifie que la valeur de ce SAN doit être liée dynamiquement à la valeur du champ Common Name du formulaire."
Le Frontend comprend ce contrat et agit en conséquence :
Dans fetchAndApplySanRules, quand il reçoit un SAN prédéfini, il vérifie : predefined.value === '{COMMON_NAME}'.
Si c'est vrai, il ne met pas {COMMON_NAME} dans le champ. À la place, il va chercher la valeur actuelle du champ Common Name (certificateName) dans le formulaire et l'utilise comme valeur initiale.
Ensuite, la méthode listenForCommonNameChanges s'assure que si l'utilisateur modifie le champ Common Name plus tard, la valeur du SAN prédéfini est mise à jour en temps réel.
Pourquoi utiliser un placeholder ?
On pourrait se demander pourquoi le backend ne retourne pas directement la valeur du Common Name.
La raison est simple : le backend ne connaît pas le Common Name au moment où le front-end demande les règles.
Revoici le flux utilisateur :
L'utilisateur choisit un "Type de Certificat".
À cet instant, le front-end appelle GET .../san-rules. Le formulaire est encore vide, il n'y a pas encore de Common Name de saisi.
Le backend reçoit la requête, calcule les règles, et voit qu'il doit y avoir un SAN prédéfini. Comme il ne connaît pas encore la valeur finale, il renvoie le placeholder {COMMON_NAME} pour dire au front : "Attention, la valeur de ce SAN dépendra d'un autre champ".
Le front-end reçoit cette instruction et met en place la liaison dynamique entre le champ Common Name et le champ du SAN prédéfini.
En résumé : {COMMON_NAME} est un signal, un placeholder envoyé par le backend au frontend. Il sert à indiquer au code Angular : "La valeur de ce champ n'est pas fixe, elle doit être une copie en temps réel de la valeur du champ Common Name du formulaire."
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
/////////////////// les service ////////////////////////////
Étape 1 : Ajouter la nouvelle URL dans Global.ts
Ouvrez ce fichier et ajoutez la nouvelle constante statique. Je vais la nommer WS_SAN_RULES_BY_CERT_TYPE pour rester cohérent avec votre convention de nommage.
Fichier à modifier : src/app/shared/Global.ts
code
TypeScript
// ... (imports existants)
import { environment } from '../../environments/environment';

export class Global {
  
  // ... (toutes vos constantes existantes comme WS_AFFECT_REQUEST, etc.)


  // --- NOUVELLE CONSTANTE À AJOUTER ---
  // (Vous pouvez la placer avec les autres URLs liées aux requêtes)
  public static WS_SAN_RULES_BY_CERT_TYPE =
    // Votre système de mock n'est probablement pas nécessaire ici car c'est une requête GET simple
    // mais je l'inclus par cohérence.
    environment.mock ? `${Global.MOCK_URL}/sanRules.json` :
    `${Global.BASE_URL}/request/certificatetypes/:typeId/san-rules`;

}
Explication :
WS_SAN_RULES_BY_CERT_TYPE : Un nom clair pour notre nouvelle URL.
:typeId : On utilise un placeholder :typeId dans l'URL. Le service se chargera de le remplacer par la vraie valeur.
Étape 2 : Utiliser la nouvelle constante dans RequestService.ts
Maintenant, on met à jour la méthode getSanRulesForCertificateType pour qu'elle utilise cette nouvelle constante. C'est beaucoup plus propre et maintenable.
Fichier à modifier : src/app/services/request.service.ts
code
TypeScript
// ... (imports existants)
import { Global } from '../shared/Global'; // Assurez-vous que l'import est correct

// ...
export class RequestService {
    
    // ... (constructeur et méthodes existantes)

    /**
     * Version MISE À JOUR qui utilise les constantes globales.
     */
    getSanRulesForCertificateType(typeId: number, subTypeId?: number): Observable<ProfileRulesResponseDto> {
        
        // On construit l'URL en remplaçant le placeholder :typeId par la vraie valeur.
        const url = Global.WS_SAN_RULES_BY_CERT_TYPE.replace(':typeId', typeId.toString());

        const params: { [param: string]: string } = {};
        if (subTypeId) {
            params['subTypeId'] = subTypeId.toString();
        }

        // L'appel à l'apiService reste identique.
        return this.apiService.get<ProfileRulesResponseDto>(url, params);
    }
}
Explication de la modification :
On importe la classe Global.
On utilise Global.WS_SAN_RULES_BY_CERT_TYPE comme modèle d'URL.
On utilise la méthode standard de JavaScript replace(':typeId', typeId.toString()) pour insérer la valeur dynamique au bon endroit.
C'est tout ! Votre code est maintenant parfaitement intégré à vos conventions de projet. La prochaine étape reste la modification du composant RequestDetailSectionComponent pour appeler cette nouvelle méthode de service.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
////////////////////////
////////////////////////////// message pour indiquer le nombre de san //////////////////////////////////////////
Étape 1 : Le Code TypeScript (la méthode qui prépare le message)
Nous avons déjà écrit cette méthode. Elle est générique et fonctionnera pour tous les types de SANs. Elle est la clé de tout l'affichage.
Fichier : request-detail-section.component.ts
code
TypeScript
// Assurez-vous que cette méthode est bien présente dans votre composant

  /**
   * Construit un message d'information sur l'état des SANs pour un type donné.
   * Cette méthode sera appelée depuis le template HTML.
   * @param sanType Le type de SAN pour lequel on veut le message (ex: 'DNSNAME', 'IPADDRESS').
   * @returns Une chaîne de caractères à afficher à l'utilisateur.
   */
  public getSanCountMessage(sanType: SanType): string {
    // 1. On trouve la règle correspondante dans les données reçues de l'API.
    const rule = this.sanRules.find(r => r.type === sanType);

    // S'il n'y a pas de règle, on n'affiche rien.
    if (!rule) {
      return '';
    }

    // 2. On compte combien de SANs de ce type l'utilisateur a déjà ajoutés.
    // On ne compte que les champs modifiables (ceux qui ne sont pas prédéfinis).
    const currentCount = this.sans.controls
      .filter(control => control.enabled && control.get('type')?.value === sanType)
      .length;

    const min = rule.min;
    const max = rule.max;

    // 3. On construit le message en fonction des valeurs de min et max.
    if (min > 0) { // Si au moins un est requis
      if (min === max) {
        return `${currentCount} / ${max} requis`; // Ex: "0 / 1 requis"
      }
      return `${currentCount} / ${max} saisis (minimum ${min})`; // Ex: "1 / 5 saisis (minimum 2)"
    } else if (max > 0) { // Si c'est purement optionnel
      return `Optionnel (${currentCount} / ${max} saisis)`; // Ex: "Optionnel (2 / 10 saisis)"
    } else { // min === 0 && max === 0
      return 'Non autorisé pour ce profil';
    }
  }
Étape 2 : Le Code HTML (l'affichage du message)
Maintenant, nous allons utiliser cette méthode dans le template pour afficher une "légende" dynamique des contraintes.
L'endroit idéal est juste avant la liste des champs de saisie des SANs.
Fichier : request-detail-section.component.html
code
Html
<!-- ... (début de votre formulaire) ... -->

<!-- Section des SANs (Subject Alternative Names) -->
<div formArrayName="sans">
    
    <!-- ========================================================== -->
    <!-- === NOUVELLE SECTION : AFFICHAGE DES RÈGLES DYNAMIQUES === -->
    <!-- ========================================================== -->
    
    <!-- On n'affiche cette section que si des règles ont été chargées depuis l'API -->
    <div class="san-rules-summary" *ngIf="sanRules.length > 0">
        
        <p class="summary-title">Contraintes pour les SANs :</p>
        
        <ul class="summary-list">
            <!-- 
              On boucle sur la variable 'sanRules' du composant.
              Pour chaque règle (chaque type de SAN autorisé), on crée une ligne.
            -->
            <li *ngFor="let rule of sanRules">
                
                <!-- On affiche le nom du type de SAN (ex: DNSNAME) -->
                <span class="san-type-label">{{ rule.type }}:</span>
                
                <!-- On appelle notre méthode pour obtenir le message dynamique -->
                <span class="san-count-message">{{ getSanCountMessage(rule.type) }}</span>

            </li>
        </ul>
    </div>
    
    <!-- ========================================================== -->
    <!-- === FIN DE LA NOUVELLE SECTION                         === -->
    <!-- ========================================================== -->

    <!-- Votre boucle existante pour afficher les champs de saisie des SANs -->
    <div *ngFor="let sanGroup of sans.controls; let i = index" [formGroupName]="i">
        <!-- 
            Ici se trouvent vos champs <select> pour le type et <input> pour la valeur.
            Les champs prédéfinis seront déjà dans la liste et grisés (car `sanGroup.disabled` est true).
        -->
    </div>

    <!-- Votre bouton "Ajouter", maintenant contrôlé par la logique dynamique -->
    <button (click)="addSan()" [disabled]="!canAddSan()">
        Ajouter un SAN
    </button>
</div>

<!-- ... (fin de votre formulaire) ... -->
Explication du Résultat
Au chargement : L'utilisateur choisit un "Type de Certificat".
Appel API : La méthode fetchAndApplySanRules est appelée en arrière-plan. Elle récupère les règles et peuple la variable this.sanRules du composant.
Rendu HTML :
Le *ngIf="sanRules.length > 0" devient true. La section de résumé s'affiche.
La boucle *ngFor="let rule of sanRules" s'exécute. Si l'API a retourné des règles pour DNSNAME, IPADDRESS et URI, la boucle va tourner 3 fois.
Première itération (DNSNAME) : Elle affiche DNSNAME: suivi du résultat de getSanCountMessage('DNSNAME').
Deuxième itération (IPADDRESS) : Elle affiche IPADDRESS: suivi du résultat de getSanCountMessage('IPADDRESS').
Et ainsi de suite...
Interaction : Quand l'utilisateur ajoute ou supprime un SAN, le currentCount à l'intérieur de getSanCountMessage est recalculé automatiquement par Angular, et le message à l'écran se met à jour instantanément.
Vous avez maintenant un retour visuel clair, dynamique et en temps réel pour l'utilisateur, qui lui indique exactement ce qu'il peut et doit faire.
///////////////////////// resolution final du ticket 1434 //////////////////
Fichier 1 : SanTypeRule.java (Nouvelle Entité)
Rôle : Représente une règle de SAN dans la base de données.
code
Java
package com.bnpparibas.certis.automationhub.model;
import com.bnpparibas.certis.certificate.request.enums.SanType;
import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "SAN_TYPE_RULE")
public class SanTypeRule implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_san_type_rule_id_gen")
    @SequenceGenerator(name = "seq_san_type_rule_id_gen", sequenceName = "SEQ_SAN_TYPE_RULE_ID", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROFILE_ID", nullable = false)
    private AutomationHubProfile automationHubProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "SAN_TYPE", nullable = false)
    private SanType type;

    @Column(name = "MIN_VALUE", nullable = false)
    private Integer minValue;

    @Column(name = "MAX_VALUE", nullable = false)
    private Integer maxValue;

    // Getters et Setters...
}
Fichier 2 : SanTypeRuleRepository.java (Nouveau Repository)
Rôle : Interface pour manipuler l'entité SanTypeRule.
code
Java
package com.bnpparibas.certis.automationhub.dao;
import com.bnpparibas.certis.automationhub.model.AutomationHubProfile;
import com.bnpparibas.certis.automationhub.model.SanTypeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SanTypeRuleRepository extends JpaRepository<SanTypeRule, Long> {
    void deleteByAutomationHubProfile(AutomationHubProfile profile);
    List<SanTypeRule> findByAutomationHubProfile(AutomationHubProfile profile);
}
Fichier 3 : AutomationHubClient.java (Nouvelle Classe)
Rôle : Centralise les appels à l'API externe d'Horizon.
code
Java
package com.bnpparibas.certis.automationhub.client;
// ... imports ...
@Component
public class AutomationHubClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationHubClient.class);
    private final RestTemplate restTemplate;
    @Value("${external.api.url.profiles}")
    private String horizonApiBaseUrl;
    
    public AutomationHubClient(@Qualifier("automationHubRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ExternalProfileDto fetchProfileDetailsFromHorizon(String profileName) {
        String apiUrl = horizonApiBaseUrl + "/" + profileName;
        LOGGER.info("Appel de l'API Horizon pour le profil '{}'...", profileName);
        try {
            return restTemplate.getForObject(apiUrl, ExternalProfileDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            LOGGER.warn("Profil '{}' non trouvé sur Horizon (404).", profileName);
            return null;
        }
    }
}
Fichier 4 : AutomationHubProfileServiceImpl.java (Modifié)
Rôle : Orchestre la synchronisation et expose les règles au controller.
code
Java
@Service
public class AutomationHubProfileServiceImpl implements AutomationHubProfileService {
    // ... (dépendances injectées : AutomationHubProfileDao, SanTypeRuleRepository, AutomationHubClient, etc.)

    @Override
    @Transactional
    public void syncAllSanRulesFromHorizonApi() {
        // ... (logique de boucle sur les profils de la BDD et appel à processSingleProfile)
    }

    private void processSingleProfile(AutomationHubProfile internalProfile) {
        // ... (logique de récupération depuis le client, nettoyage, et sauvegarde des règles via sanTypeRuleRepository.saveAll)
    }

    @Override
    public ProfileRulesResponseDto getSanRulesByCertificateType(Long typeId, Long subTypeId) {
        // ... (logique pour trouver le profil et retourner les règles ajustées pour le front-end)
    }
    
    @Override
    public AutomationHubProfile findProfileEntityByTypeAndSubType(Long typeId, Long subTypeId) throws FailedToRetrieveProfileException {
        // ... (logique pour "traduire" un type de certificat en profil technique)
    }
    
    private SanRuleResponseDto convertToSanRuleDto(SanTypeRule entity) {
        // ... (logique de conversion Entité -> DTO)
    }
}
Fichier 5 : SanServiceImpl.java (Modifié)
Rôle : Valide les demandes de certificat en utilisant les règles synchronisées.
code
Java
@Service
public class SanServiceImpl implements SanService {
    // ... (dépendances injectées : AutomationHubProfileService, SanTypeRuleRepository, FileManageService, CsrDecoder)

    @Override
    public void validateSansPerRequest(RequestDto requestDto) throws CertisRequestException {
        // Supprimer l'ancien code et appeler la nouvelle méthode
        this.verifySansLimitsDynamically(requestDto);
    }

    private void verifySansLimitsDynamically(RequestDto requestDto) throws CertisRequestException {
        // ... (logique complète de fusion des SANs, traduction du profil, et validation des règles min/max)
    }
}
Fichier 6 : RequestController.java (Modifié)
Rôle : Expose le nouvel endpoint pour le front-end.
code
Java
@RestController
@RequestMapping("/request")
public class RequestController {
    // ... (dépendances injectées, dont AutomationHubProfileService)

    // --- NOUVEL ENDPOINT ---
    @GetMapping("/certificatetypes/{typeId}/san-rules")
    public ResponseEntity<ProfileRulesResponseDto> getSanRulesForCertificateType(
            @PathVariable Long typeId,
            @RequestParam(required = false) Long subTypeId) {
        ProfileRulesResponseDto response = automationHubProfileService.getSanRulesByCertificateType(typeId, subTypeId);
        return ResponseEntity.ok(response);
    }
}
Partie 2 : Le Front-end (Angular)
Objectif : Appeler la nouvelle API pour afficher dynamiquement les contraintes de SANs et guider l'utilisateur.
Fichier 7 : san-rules.model.ts (Nouveau)
Rôle : Définit les types de données pour la communication avec le backend.
code
TypeScript
export type SanType = 'CN' | 'DNSNAME' | 'IPADDRESS' | 'RFC822NAME' | 'URI' | ...;

export interface SanRuleResponseDto { type: SanType; min: number; max: number; }
export interface PredefinedSanDto { type: SanType; value: string; editable: boolean; }
export interface ProfileRulesResponseDto { rules: SanRuleResponseDto[]; predefinedSans: PredefinedSanDto[]; }
Fichier 8 : request.service.ts (Modifié)
Rôle : Ajoute la méthode pour appeler le nouvel endpoint.
code
TypeScript
@Injectable({ providedIn: 'root' })
export class RequestService {
    // ... (dépendances et méthodes existantes)

    // --- NOUVELLE MÉTHODE ---
    getSanRulesForCertificateType(typeId: number, subTypeId?: number): Observable<ProfileRulesResponseDto> {
        const url = `/api/v1/request/certificatetypes/${typeId}/san-rules`; // Adaptez l'URL
        const params = subTypeId ? { subTypeId: subTypeId.toString() } : {};
        return this.apiService.get<ProfileRulesResponseDto>(url, params);
    }
}
Fichier 9 : request-detail-section.component.ts (Modifié)
Rôle : Le composant qui contient toute la logique d'affichage et d'interaction.
code
TypeScript
@Component({ /* ... */ })
export class RequestDetailSectionComponent implements OnInit, OnDestroy {
  
  // --- NOUVELLES PROPRIÉTÉS ---
  private onDestroy$ = new Subject<void>();
  public sanRules: SanRuleResponseDto[] = [];
  
  // --- ngOnInit MODIFIÉ ---
  ngOnInit(): void {
    // ...
    this.listenForCertificateTypeChanges();
    this.listenForCommonNameChanges();
  }

  // --- NOUVELLES MÉTHODES ---
  private listenForCertificateTypeChanges(): void { /* ... */ }
  private fetchAndApplySanRules(typeId: number, subTypeId?: number): void { /* ... */ }
  private listenForCommonNameChanges(): void { /* ... */ }
  private clearSanRulesAndControls(): void { /* ... */ }

  // --- MÉTHODES PUBLIQUES POUR LE TEMPLATE ---
  public getSanCountMessage(sanType: SanType): string { /* ... */ }
  public canAddSan(): boolean { /* ... */ }
}
Fichier 10 : request-detail-section.component.html (Modifié)
Rôle : Affiche les messages de contraintes et contrôle les boutons.
code
Html
<!-- Section des SANs -->
<div formArrayName="sans">
    
    <!-- Affiche la légende des règles -->
    <div class="san-rules-summary" *ngIf="sanRules.length > 0">
        <h4>Contraintes des SANs :</h4>
        <ul>
            <li *ngFor="let rule of sanRules">
                <strong>{{ rule.type }}:</strong> {{ getSanCountMessage(rule.type) }}
            </li>
        </ul>
    </div>

    <!-- Affiche les champs de saisie (votre boucle existante) -->
    <div *ngFor="let sanGroup of sans.controls; let i = index" [formGroupName]="i">
        <!-- Les champs prédéfinis seront ici et désactivés -->
    </div>

    <!-- Contrôle le bouton "Ajouter" -->
    <button (click)="addSan()" [disabled]="!canAddSan()">Ajouter un SAN</button>
</div>
////////////////////////////////1437 //////////////////////////////
pour ce profil en base (comme le fait la task de 1259)."
Traduction :
Si une demande de certificat échoue à cause d'une validation de SANs, cela signifie que nos règles en base de données sont peut-être obsolètes. Il y a eu une "dérive de configuration" : quelqu'un a modifié les règles sur Horizon, mais notre tâche de synchronisation nocturne n'a pas encore tourné.
Au lieu d'attendre la nuit suivante, nous devons déclencher une synchronisation immédiate et ciblée pour le profil qui a causé l'erreur.
La deuxième partie du ticket est une optimisation :
"> Si erreur sur un profil alors relancer la tache de mise a jour (soit tous les profils ou juste sur le profil en question et"
"soit tous les profils" : Facile à faire, mais moins performant.
"ou juste sur le profil en question" : C'est la meilleure approche, la plus ciblée et la plus efficace. C'est celle que nous allons implémenter.
Plan d'Action et Solution
Heureusement, vous avez déjà fait 95% du travail. Toute la logique de synchronisation pour un profil unique existe déjà. Il nous suffit de l'exposer d'une manière différente et de l'appeler au bon moment.
Étape 1 : Exposer la logique de synchronisation d'un seul profil
Votre service AutomationHubProfileServiceImpl a une méthode privée processSingleProfile. Nous allons simplement créer une nouvelle méthode publique qui l'appelle.
Fichier à modifier : AutomationHubProfileServiceImpl.java
code
Java
// Dans AutomationHubProfileServiceImpl.java

@Service
public class AutomationHubProfileServiceImpl implements AutomationHubProfileService {

    // ... (dépendances et méthodes existantes)

    // --- NOUVELLE MÉTHODE PUBLIQUE ---

    /**
     * Déclenche la synchronisation immédiate des règles de SANs pour un seul profil.
     * Cette méthode peut être appelée lorsqu'une dérive de configuration est détectée.
     *
     * @param profileName Le nom du profil à synchroniser.
     */
    @Override
    @Transactional
    public void syncSingleProfileSanRules(String profileName) {
        LOGGER.info("Déclenchement de la synchronisation à la demande pour le profil : {}", profileName);
        
        // 1. On récupère l'entité de profil de notre base locale.
        AutomationHubProfile internalProfile = automationHubProfileDao.findByProfileName(profileName)
                .orElse(null); // On ne lève pas d'erreur si le profil est inconnu

        if (internalProfile == null) {
            LOGGER.error("Impossible de lancer la synchronisation pour le profil '{}' car il est inconnu dans la table AUTOMATIONHUB_PROFILE.", profileName);
            // On pourrait décider de quand même essayer de l'appeler sur Horizon,
            // mais il est plus sûr de se baser sur nos profils connus.
            return;
        }

        // 2. On appelle notre méthode privée existante qui contient toute la logique.
        // On la met dans un try-catch pour s'assurer que l'appelant ne reçoit pas d'exception inattendue.
        try {
            this.processSingleProfile(internalProfile);
            LOGGER.info("Synchronisation à la demande pour le profil '{}' terminée avec succès.", profileName);
        } catch (Exception e) {
            LOGGER.error("Échec de la synchronisation à la demande pour le profil '{}'.", profileName, e);
            // On ne propage pas l'exception pour ne pas interrompre le flux qui a appelé cette méthode.
        }
    }


    // La méthode syncAllSanRulesFromHorizonApi() reste la même.
    // La méthode privée processSingleProfile(AutomationHubProfile internalProfile) reste la même.
}
N'oubliez pas d'ajouter la signature void syncSingleProfileSanRules(String profileName); à votre interface AutomationHubProfileService.java.
Étape 2 : Appeler cette nouvelle méthode au bon moment
C'est l'étape la plus importante. Il faut identifier l'endroit dans votre code où une demande est rejetée à cause d'une erreur de validation de SANs.
Cet endroit est très probablement là où vous appelez sanService.validateSansPerRequest(requestDto);. C'est probablement dans RequestServiceImpl.
Fichier à modifier : RequestServiceImpl.java
code
Java
// Dans RequestServiceImpl.java

@Service
public class RequestServiceImpl implements RequestService {

    // ... (dépendances existantes)
    private final SanService sanService;
    private final AutomationHubProfileService automationHubProfileService; // Assurez-vous d'injecter ce service

    // ... (constructeur mis à jour)

    @Override
    public RequestDto createRequest(RequestDto requestDto) throws Exception {
        
        // ... (votre code existant)

        // --- DÉBUT DE LA MODIFICATION ---
        
        try {
            // On appelle la validation comme avant.
            sanService.validateSansPerRequest(requestDto);
            
        } catch (CertisRequestException e) {
            // On intercepte spécifiquement l'exception de validation.
            
            // On vérifie si l'erreur est bien liée à une validation de SANs (en se basant sur les clés d'erreur).
            // C'est une bonne pratique pour ne pas déclencher la synchro pour d'autres types d'erreurs.
            String errorCode = e.getMsgidx(); // Suppose que votre exception contient une clé d'erreur
            if (errorCode != null && errorCode.startsWith("error.san.")) {
                
                // Si c'est une erreur de SAN, on déclenche la synchronisation en arrière-plan.
                LOGGER.warn("Une erreur de validation de SANs a été détectée pour le profil '{}'. " + 
                          "Déclenchement d'une synchronisation immédiate des règles.", 
                          requestDto.getCertificate().getType().getName());
                
                try {
                    // On doit trouver le nom du profil technique.
                    Long typeId = requestDto.getCertificate().getType().getId();
                    Long subTypeId = (requestDto.getCertificate().getSubType() != null) ? requestDto.getCertificate().getSubType().getId() : null;
                    
                    AutomationHubProfile profileToSync = automationHubProfileService.findProfileEntityByTypeAndSubType(typeId, subTypeId);
                    
                    // On appelle notre nouvelle méthode de synchronisation ciblée.
                    automationHubProfileService.syncSingleProfileSanRules(profileToSync.getProfileName());

                } catch (Exception syncEx) {
                    // On loggue une erreur si la "traduction" du profil échoue, mais on ne bloque pas.
                    LOGGER.error("Impossible de trouver le nom du profil technique pour lancer la synchronisation.", syncEx);
                }
            }
            
            // Quoi qu'il arrive, on repropage l'exception de validation originale
            // pour que l'utilisateur soit bien notifié que sa demande a échoué.
            throw e;
        }

        // --- FIN DE LA MODIFICATION ---
        
        // ... (le reste de votre logique de création de requête)
    }
}
Explication du Flux Final
Un utilisateur soumet une demande avec un nombre incorrect de SANs.
La méthode createRequest dans RequestServiceImpl est appelée.
Elle appelle sanService.validateSansPerRequest(...).
La validation échoue et lève une CertisRequestException avec un code d'erreur comme error.san.max.violation.
Le bloc try-catch dans createRequest intercepte cette exception.
Il vérifie que le code d'erreur commence bien par error.san..
Si c'est le cas, il utilise automationHubProfileService pour trouver le nom du profil technique (ex: "SSL_SRVR").
Il appelle la nouvelle méthode automationHubProfileService.syncSingleProfileSanRules("SSL_SRVR").
Cette méthode va contacter l'API d'Horizon, récupérer les nouvelles règles pour "SSL_SRVR" et mettre à jour la base de données.
Très important : Le catch repropage (throw e;) l'exception originale. Cela garantit que le processus de création de la demande est bien arrêté et que l'utilisateur reçoit le message d'erreur lui disant que sa demande est invalide.
La prochaine fois qu'un utilisateur essaiera de faire une demande pour ce profil, la validation se fera contre les règles fraîchement mises à jour. C'est un système d'auto-correction très efficace.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
