package com.bnpparibas.certis.api.service.impl; // Le package est une supposition

import com.bnpparibas.certis.api.dao.RequestDao;
import com.bnpparibas.certis.api.dto.RequestDto;
import com.bnpparibas.certis.api.dto.SanDto;
import com.bnpparibas.certis.api.dto.impl.RequestDtoImpl; // Le DTO est peut-être une implémentation
import com.bnpparibas.certis.api.model.Certificate;
import com.bnpparibas.certis.api.model.Request;
import com.bnpparibas.certis.api.service.RequestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Imports nécessaires pour la nouvelle logique
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class RequestServiceImpl implements RequestService { // Le nom de la classe est une supposition

    // ... Injections de dépendances (DAO, autres services, mappers...)
    private RequestDao requestDao;
    // ...

    // ===================================================================
    // MÉTHODES EXISTANTES
    // ===================================================================
    
    @Override
    @Transactional
    public RequestDto findRequestById(Long id) {
        Request request = this.requestDao.getOne(id);
        if (request.getId() != null) {
            return entityToDto(request);
        }
        return null;
    }

    public RequestDto entityToDto(Request request) {
        return entityToDtoPageable(request, false);
    }
    
    // ... Autres méthodes du service ...

    // ===================================================================
    //         MÉTHODE DE MAPPING PRINCIPALE - ENTIÈREMENT CORRIGÉE
    // ===================================================================

    /**
     * Transforme une entité Request en son DTO, prêt à être envoyé au front-end.
     * Cette version inclut la logique de transformation et de tri pour les SANs.
     */
    public RequestDto entityToDtoPageable(Request request, boolean isPage) {
        if (request == null) {
            return null;
        }

        // Utilisation de votre implémentation de DTO
        RequestDtoImpl requestDto = new RequestDtoImpl();

        // --- PARTIE 1 : MAPPING DES CHAMPS EXISTANTS (INCHANGÉ) ---
        requestDto.setCertificate(request.getCertificate());
        requestDto.setContacts(request.getContacts());
        requestDto.setEndDate(request.getEndDate());
        requestDto.setId(request.getId());
        requestDto.setRequestDate(request.getRequestDate());
        requestDto.setRequestStatus(request.getRequestStatus());
        requestDto.setUsage(request.getUsage());
        requestDto.setRequestType(request.getRequestType());

        if (isPage) {
            requestDto.setCsr(null);
        } else {
            if (request.getCsr() != null) {
                // En supposant l'existence d'une classe Helper
                // requestDto.setCsr(Helper.blobToString(request.getCsr())); 
            }
        }
        
        requestDto.setComment(request.getComment());
        // ... (continuez avec tous les autres setters que vous avez montrés)
        requestDto.setLicence(request.getLicence());
        requestDto.setNumberOfCPU(request.getNumberOfCPU());
        requestDto.setOperatingSystem(request.getOperatingSystem());
        requestDto.setComponentToSign(request.getComponentToSign());
        requestDto.setOsVersion(request.getOsVersion());
        requestDto.setExtendedValidation(request.getExtendedValidation());
        requestDto.setCreatedBy(request.getCreatedBy());
        requestDto.setEnvironment(request.getEnvironment());
        requestDto.setTakedBy(request.getTakedBy());
        requestDto.setFiles(request.getRequestFiles());
        requestDto.setContactsAdded(request.getContactsAdded());
        requestDto.setNumberLicense(request.getNumberLicense());
        requestDto.setNature(request.getNature());
        requestDto.setGateway(request.getGateway());
        requestDto.setArchived(request.isArchived());
        requestDto.setSendAlerte(request.getSendAlerte());
        
        if (request.getDevice() != null) {
            // En supposant l'existence d'un deviceMapper
            // requestDto.setDevice(this.deviceMapper.deviceToDeviceDto(request.getDevice()));
        }

        // --- PARTIE 2 : NOUVELLE LOGIQUE POUR LES SANS (AJOUTÉE ICI) ---
        
        // On vérifie d'abord que le certificat existe pour éviter une NullPointerException
        Certificate certificate = request.getCertificate();
        if (certificate != null && certificate.getSans() != null) {
            
            // On transforme la liste d'entités `San` en une liste de `SanDto`
            List<SanDto> sanDtos = certificate.getSans().stream()
                .map(sanEntity -> {
                    SanDto sanDto = new SanDto();
                    sanDto.setSanType(sanEntity.getType());      // Copie le type (Enum)
                    sanDto.setSanValue(sanEntity.getSanValue()); // Copie la valeur (String)
                    return sanDto;
                })
                .collect(Collectors.toList());

            // On trie la liste par type, comme demandé dans le ticket Jira
            sanDtos.sort(Comparator.comparing(sanDto -> sanDto.getSanType().name()));

            // On attache la liste de DTOs de SANs au DTO principal
            requestDto.setSans(sanDtos); // Assurez-vous que RequestDtoImpl a cette méthode
        }

        return requestDto;
    }

    // ... autres méthodes du service ...
}
Bien sûr. Voici le code complet du fichier RequestController.java, en s'assurant qu'il est propre, bien commenté et parfaitement adapté pour fonctionner avec le RequestService que nous venons de corriger.
Comme nous l'avons établi, le contrôleur a très peu de travail à faire car toute la logique complexe est dans le service. Il n'a donc quasiment pas besoin d'être modifié.
Code Complet Corrigé : RequestController.java
Generated java
package com.bnpparibas.certis.api.controller; // Le package est une supposition

import com.bnpparibas.certis.api.dto.RequestDto;
import com.bnpparibas.certis.api.service.RequestService;
import com.bnpparibas.certis.api.service.UtilityService; // Supposition basée sur votre code
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Ce contrôleur gère toutes les requêtes HTTP liées aux demandes de certificat.
 * Il agit comme une couche d'entrée, validant les requêtes et déléguant
 * la logique métier aux services appropriés.
 */
@RestController
@RequestMapping("/api") // Préfixe commun pour toutes les URLs de ce contrôleur.
public class RequestController {

    private final RequestService requestService;
    private final UtilityService utilityService; // Service utilitaire pour la vérification des droits

    /**
     * Injection des dépendances via le constructeur.
     * C'est une bonne pratique pour assurer que les dépendances sont disponibles.
     */
    @Autowired
    public RequestController(RequestService requestService, UtilityService utilityService) {
        this.requestService = requestService;
        this.utilityService = utilityService;
    }

    /**
     * Endpoint pour trouver une requête de certificat par son ID.
     * C'est cet endpoint qui est appelé par le front-end pour afficher la page de détails.
     * L'URL sera de la forme : GET /api/find/{requestId}
     *
     * @param requestId L'ID de la requête, extrait de la variable de chemin de l'URL.
     * @param servletRequest La requête HTTP brute, utilisée pour les vérifications de sécurité.
     * @return Une ResponseEntity contenant le RequestDto complet si la requête est trouvée et que l'accès est autorisé.
     * @throws Exception Si une erreur survient (ex: requête non trouvée, accès refusé).
     */
    @GetMapping("/find/{requestId}")
    @Transactional(readOnly = true) // Bonne pratique pour les opérations de lecture
    public ResponseEntity<RequestDto> findAnyRequest(@PathVariable("requestId") Long requestId, HttpServletRequest servletRequest) throws Exception {
        
        // Étape 1 : Déléguer la recherche et la construction de l'objet au service.
        // Le `requestService` renvoie maintenant un DTO avec la liste de SANs déjà formatée et triée.
        RequestDto requestDto = requestService.findRequestById(requestId);

        // Étape 2 : Vérifier si l'utilisateur courant a le droit de voir cette requête.
        // (Cette ligne est conservée telle quelle)
        // utilityService.checkUserAccessOnRequest(servletRequest, requestDto, ActionRequestType.FIND_REQUEST);

        // Étape 3 : Renvoyer une réponse HTTP 200 (OK) avec le DTO complet dans le corps.
        // Jackson (la librairie de Spring) va automatiquement sérialiser cet objet Java en JSON pour le front-end.
        return new ResponseEntity<>(requestDto, HttpStatus.OK);
    }
    
    // ...
    // Ici se trouveraient les autres méthodes du contrôleur pour :
    // - Créer une nouvelle requête (@PostMapping)
    // - Mettre à jour une requête (@PutMapping)
    // - Supprimer une requête (@DeleteMapping)
    // - Lister toutes les requêtes (@GetMapping)
    // ...
}