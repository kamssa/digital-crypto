Récapitulatif Final et Complet
I. Backend (Spring Boot)
1. Nouveau Fichier DTO : UpdateCommentDto.java
Action : CRÉER ce nouveau fichier.
Rôle : Transporter le nouveau commentaire de manière sécurisée et validée entre le frontend et le backend.
code
Java
// CHEMIN SUGGÉRÉ : src/main/java/com/bnpparibas/certis/certificate/request/dto/UpdateCommentDto.java

package com.bnpparibas.certis.certificate.request.dto;

import javax.validation.constraints.Size;

public class UpdateCommentDto {

    @Size(max = 4000, message = "Le commentaire ne peut pas dépasser 4000 caractères.")
    private String comment;

    // Getters et Setters
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
2. Interface de Service : RequestService.java
Action : MODIFIER ce fichier pour déclarer la nouvelle fonctionnalité.
Rôle : Rendre la méthode updateCertificateComment visible pour le contrôleur.
code
Java
// CHEMIN : src/main/java/com/bnpparibas/certis/service/RequestService.java

public interface RequestService {

    // ... (gardez toutes vos autres déclarations de méthodes existantes)

    /**
     * Met à jour le commentaire d'un certificat et ajoute une trace à l'historique de la requête.
     * @param requestId L'ID de la requête à modifier.
     * @param newCertificateComment Le nouveau texte du commentaire pour le certificat.
     * @param username Le nom de l'utilisateur qui effectue la modification.
     */
    void updateCertificateComment(Long requestId, String newCertificateComment, String username);
}
3. Implémentation du Service : RequestServiceImpl.java
Action : MODIFIER ce fichier pour ajouter la logique métier.
Rôle : C'est le cœur de la fonctionnalité. Il modifie les données et les sauvegarde.
code
Java
// CHEMIN : src/main/java/com/bnpparibas/certis/service/impl/RequestServiceImpl.java

// Ajoutez ces imports en haut du fichier si ils ne sont pas déjà présents
import javax.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RequestServiceImpl implements RequestService {

    // ... (gardez toutes vos propriétés @Autowired et votre constructeur)

    // ==================================================================================
    //     ▼▼▼   AJOUTEZ CETTE NOUVELLE MÉTHODE COMPLÈTE À VOTRE CLASSE   ▼▼▼
    // ==================================================================================
    @Override
    @Transactional
    public void updateCertificateComment(Long requestId, String newCertificateComment, String username) {
        
        // 1. Récupérer l'entité via le DAO (Data Access Object)
        Request request = requestDao.findOne(requestId);
        if (request == null) {
            throw new EntityNotFoundException("Request not found with id: " + requestId);
        }

        // 2. Vérifier les permissions en utilisant votre méthode helper existante
        checkAccessibilityForRequest(request, username, ActionRequestType.UPDATE_REQUEST);

        // 3. Mettre à jour le commentaire du Certificat lié
        Certificate certificate = request.getCertificate();
        if (certificate == null) {
            throw new IllegalStateException("Certificate is null for request id: " + requestId);
        }
        certificate.setComment(newCertificateComment);

        // 4. Utiliser votre CommentService pour tracer l'action
        RequestDto requestDto = entityToDto(request); // Convertir en DTO pour le service
        String traceMessage = "Le commentaire du certificat a été mis à jour.";

        // On appelle la méthode de CommentService pour formater et ajouter la trace à l'historique du DTO
        commentService.processComment(requestDto, null, username, traceMessage);
        
        // 5. Appliquer le nouvel historique (qui vient du DTO modifié) sur notre entité
        request.setComment(requestDto.getComment());

        // 6. Sauvegarder l'entité Request. @Transactional s'occupe de la sauvegarde du certificat lié.
        requestDao.save(request);
    }

    // ... (gardez toutes vos autres méthodes existantes)
}
4. Contrôleur : RequestController.java
Action : MODIFIER ce fichier pour ajouter le nouvel endpoint.
Rôle : Exposer l'URL que le frontend va appeler.
code
Java
// CHEMIN : src/main/java/com/bnpparibas/certis/api/controller/RequestController.java

// Ajoutez ces imports en haut du fichier si besoin
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.servlet.ServletRequest;
import javax.validation.Valid;
import com.bnpparibas.certis.certificate.request.dto.UpdateCommentDto;

@RestController
public class RequestController {

    // ... (gardez toutes vos propriétés @Autowired et votre constructeur)

    // =================================================================
    //     ▼▼▼   AJOUTEZ CETTE NOUVELLE MÉTHODE D'ENDPOINT   ▼▼▼
    // =================================================================
    @PutMapping("/requests/{requestId}/comment")
    public ResponseEntity<Void> updateCertificateComment(
            @PathVariable Long requestId,
            @Valid @RequestBody UpdateCommentDto commentDto,
            ServletRequest servletRequest) {
        
        String username = this.utilityService.getConnectedUserOrApi(servletRequest);
        
        this.requestService.updateCertificateComment(requestId, commentDto.getComment(), username);
        
        return ResponseEntity.ok().build();
    }

    // ... (gardez toutes vos autres méthodes de contrôleur existantes)
}
//////////////////////////////////////
 @Override
    @Transactional
    public void updateCertificateComment(Long requestId, String newCertificateComment, String username) {
        
        // 1. Récupérer l'entité Request via le DAO en utilisant la méthode moderne `findById`.
        //    .orElseThrow() gère élégamment le cas où la requête n'est pas trouvée.
        Request request = requestDao.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));

        // 2. Vérifier les permissions de l'utilisateur pour cette action.
        //    (Adaptez ActionRequestType si vous avez une permission plus spécifique).
        checkAccessibilityForRequest(request, username, ActionRequestType.UPDATE_REQUEST);

        // 3. Accéder à l'entité Certificate qui est liée à la Request.
        Certificate certificate = request.getCertificate();
        if (certificate == null) {
            throw new IllegalStateException("Critical error: Certificate associated with request id " + requestId + " is null.");
        }
        
        // 4. MODIFIER le commentaire principal du certificat.
        certificate.setComment(newCertificateComment);

        // 5. TRACER l'action dans le champ d'historique de la Request.
        //    On utilise votre CommentService pour une logique cohérente.
        RequestDto requestDto = entityToDto(request); // Convertir l'entité en DTO.
        String traceMessage = "Le commentaire du certificat a été mis à jour.";

        // On appelle la méthode de votre service de commentaire existant.
        commentService.processComment(requestDto, null, username, traceMessage);
        
        // 6. Appliquer le nouvel historique (qui a été mis à jour dans le DTO) sur notre entité.
        request.setComment(requestDto.getComment());

        // 7. SAUVEGARDER les modifications.
        // L'annotation @Transactional garantit que les changements sur 'request' ET 'certificate'
        // seront sauvegardés dans la même transaction.
        requestDao.save(request);
    }
	@Override
public RequestDto updateRequestInfo(UpdateRequesUpdateRquestInfotDto updateRequestInfoDto, Long requestId, String connectedUser, String username, ActionRequestType action) throws Exception {
    
    // Étape 1 : Charger l'entité managée directement depuis la base de données.
    // Nous ne chargeons plus un DTO, mais l'objet `Request` lui-même.
    // La vérification d'accessibilité est conservée.
    this.checkAccessibilityForRequest(requestId, connectedUser, action);
    Request requestToUpdate = this.requestDao.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));

    Certificate certificateToUpdate = requestToUpdate.getCertificate();
    if (certificateToUpdate == null) {
        // Cette vérification est cruciale pour éviter les NullPointerException.
        throw new IllegalStateException("Critical error: Certificate associated with request id " + requestId + " is null.");
    }
    
    // On garde une trace des modifications pour le commentaire.
    String traceModification = "Request information has been modified:";

    // Étape 2 : Appliquer les modifications du DTO directement sur l'entité managée.
    
    // Application Code
    if (!StringUtils.equalsIgnoreCase(updateRequestInfoDto.getApplicationCode(), certificateToUpdate.getApplicationCode())) {
        traceModification += " ApplicationCode set to " + updateRequestInfoDto.getApplicationCode() + ";";
        // L'appel au service qui gère la logique métier est conservé.
        certificateService.setApplicationCodeAndSupportGroup(certificateToUpdate, updateRequestInfoDto.getApplicationCode());
    }

    // Application Name
    if (StringUtils.isNotEmpty(updateRequestInfoDto.getApplicationName())) {
        if (!updateRequestInfoDto.getApplicationName().equalsIgnoreCase(certificateToUpdate.getApplicationName())) {
            traceModification += " ApplicationName set to " + updateRequestInfoDto.getApplicationName() + ";";
            certificateToUpdate.setApplicationName(updateRequestInfoDto.getApplicationName());
        }
    }

    // Hostname
    if (StringUtils.isNotEmpty(updateRequestInfoDto.getHostname())) {
        if (certificateToUpdate.getHostname() == null || !updateRequestInfoDto.getHostname().equalsIgnoreCase(certificateToUpdate.getHostname())) {
            traceModification += " Hostname set to " + updateRequestInfoDto.getHostname() + ";";
            certificateToUpdate.setHostname(updateRequestInfoDto.getHostname());
        }
    }

    // Environment
    if (updateRequestInfoDto.getEnvironment() != null) {
        if (certificateToUpdate.getEnvironment() == null || !updateRequestInfoDto.getEnvironment().equals(certificateToUpdate.getEnvironment())) {
            traceModification += " Environment set to " + updateRequestInfoDto.getEnvironment().getName() + ";";
            certificateToUpdate.setEnvironment(updateRequestInfoDto.getEnvironment());
        }
    }

    // Unknown Code AP
    if (updateRequestInfoDto.getUnknownCodeAP() != null) {
        if (certificateToUpdate.getUnknownCodeAP() == null || !updateRequestInfoDto.getUnknownCodeAP().equals(certificateToUpdate.getUnknownCodeAP())) {
            traceModification += " Unknown Code AP set to " + updateRequestInfoDto.getUnknownCodeAP().toString() + ";";
            certificateToUpdate.setUnknownCodeAP(updateRequestInfoDto.getUnknownCodeAP());
        }
    }

    // Certis Entity
    if (updateRequestInfoDto.getCertisEntity() != null && StringUtils.isNotEmpty(updateRequestInfoDto.getCertisEntity().getName())) {
        CertisEntity entity = this.entityDao.findByName(updateRequestInfoDto.getCertisEntity().getName());
        if (entity != null) {
             if (certificateToUpdate.getCertisEntity() == null || !entity.getName().equalsIgnoreCase(certificateToUpdate.getCertisEntity().getName())) {
                traceModification += " Entity set to " + entity.getName() + ";";
                certificateToUpdate.setCertisEntity(entity);
            }
        }
    }

    // Group Support
    if (updateRequestInfoDto.getGroupSupport() != null && StringUtils.isNotEmpty(updateRequestInfoDto.getGroupSupport().getName())) {
        List<GroupSupport> groupSupportList = groupSupportDao.findByName(updateRequestInfoDto.getGroupSupport().getName().trim());
        if (!CollectionUtils.isEmpty(groupSupportList)) {
            GroupSupport gs = groupSupportList.get(0);
            if (certificateToUpdate.getGroupSupport() == null || !gs.getName().equalsIgnoreCase(certificateToUpdate.getGroupSupport().getName())) {
                traceModification += " Group support set to " + gs.getName() + ";";
                certificateToUpdate.setGroupSupport(gs);
            }
        }
    }

    // Country
    if (updateRequestInfoDto.getCountry() != null && StringUtils.isNotEmpty(updateRequestInfoDto.getCountry().getIsoCode())) {
        GnsCountry country = this.countryDao.findByIsoCode(updateRequestInfoDto.getCountry().getIsoCode());
        if (country != null) {
            if (certificateToUpdate.getGnsCountry() == null || !country.getEnName().equalsIgnoreCase(certificateToUpdate.getGnsCountry().getEnName())) {
                traceModification += " Country set to " + country.getEnName() + ";";
                certificateToUpdate.setGnsCountry(country);
            }
        }
    }

    // Certificate Comment
    String newCertificateComment = updateRequestInfoDto.getCertificateComment();
    if (!Objects.equals(certificateToUpdate.getComment(), newCertificateComment)) {
        certificateToUpdate.setComment(newCertificateComment);
        traceModification += " Le commentaire du certificat a été mis à jour;";
    }

    // Traitement du commentaire sur la demande
    this.commentService.processComment(requestToUpdate, null, connectedUser, traceModification);
    
    // Mettre à jour l'indicateur "code AP vérifié"
    certificateToUpdate.setCodeAPChecked(true);

    // Étape 3 : Sauvegarder l'entité qui a été directement modifiée.
    // L'appel à requestDao.save() persiste toutes les modifications apportées à requestToUpdate et à ses relations (comme certificateToUpdate).
    // @Transactional s'occupera de la propagation des changements.
    Request updatedRequest = this.requestDao.save(requestToUpdate);

    // Retourner un DTO de l'entité mise à jour, comme le contrat de la méthode l'exige.
    return this.entityToDto(updatedRequest);
}
/////

@Override
public RequestDto updateRequestInfo(UpdateRequesUpdateRquestInfotDto updateRequestInfoDto, Long requestId, String connectedUser, String username, ActionRequestType action) throws Exception {
    
    // Étape 1 : Charger l'entité managée et vérifier l'accès
    Request requestToUpdate = this.requestDao.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));

    this.checkAccessibilityForRequest(requestToUpdate, connectedUser, action);

    Certificate certificateToUpdate = requestToUpdate.getCertificate();
    if (certificateToUpdate == null) {
        throw new IllegalStateException("Critical error: Certificate associated with request id " + requestId + " is null.");
    }
    
    String traceModification = "Request information has been modified:";

    // Étape 2 : Appliquer les modifications sur les entités managées...
    // ... (toutes les sections if pour ApplicationCode, Environment, UnknownCodeAP, etc.) ...
    
    // [COLLEZ ICI TOUTES LES SECTIONS DE MISE À JOUR QUE NOUS AVONS CORRIGÉES PRÉCÉDEMMENT]
    // Par exemple :
    // Application Name
    if (StringUtils.hasText(updateRequestInfoDto.getApplicationName())) {
        if (!updateRequestInfoDto.getApplicationName().equalsIgnoreCase(certificateToUpdate.getApplicationName())) {
            traceModification += " ApplicationName set to " + updateRequestInfoDto.getApplicationName() + ";";
            certificateToUpdate.setApplicationName(updateRequestInfoDto.getApplicationName());
        }
    }
    // ... et toutes les autres ...

    // --- CORRECTION APPLIQUÉE ICI ---
    // Traitement du commentaire sur la demande.
    // La méthode attend un DTO, nous convertissons donc notre entité `requestToUpdate` en DTO.
    RequestDto requestDtoForComment = this.entityToDto(requestToUpdate);
    this.commentService.processComment(requestDtoForComment, null, connectedUser, traceModification);
    // --- FIN DE LA CORRECTION ---
    
    // Mettre à jour l'indicateur "code AP vérifié"
    certificateToUpdate.setCodeAPChecked(true);

    // Étape 3 : Sauvegarder l'entité qui a été directement modifiée.
    Request updatedRequest = this.requestDao.save(requestToUpdate);

    // Retourner un DTO de l'entité mise à jour, comme le contrat de la méthode l'exige.
    return this.entityToDto(updatedRequest);
}
////// correction bug ////////////////////
@Service
public class RequestServiceImpl implements RequestService {

    // ... (tous vos @Autowired DAOs et Services : requestDao, requestStatusDao, commentService, etc.)
    // Assurez-vous que toutes les dépendances sont bien injectées.
    
    // --- MÉTHODE DE CRÉATION (RESTE INCHANGÉE MAIS NE DOIT PAS ÊTRE UTILISÉE POUR UPDATE) ---
    @Override
    @Transactional
    public RequestDto createRequest(RequestDto requestDto) throws Exception {
        // ... (votre logique de création existante)
        // Cette méthode est correcte pour la CRÉATION, mais ne doit JAMAIS être appelée pour une MISE À JOUR.
        Request request = dtoToEntity(requestDto);
        Request savedRequest = requestDao.save(request);
        return entityToDto(savedRequest);
    }

    // --- ANCIENNES MÉTHODES DE MISE À JOUR - MAINTENANT OBSOLÈTES ---
    @Override
    public RequestDto updateRequest(RequestDto requestDto) {
        throw new UnsupportedOperationException("DEPRECATED: Use a specific service method for the business action instead (e.g., updateRequestStatus, takeRequest).");
    }

    @Override
    public RequestDto updateRequestWithoutValidation(RequestDto requestDto) {
        throw new UnsupportedOperationException("DEPRECATED: Use a specific service method for the business action instead (e.g., updateRequestStatus, takeRequest).");
    }

    // --- MÉTHODE DE MISE À JOUR D'INFORMATIONS (CORRIGÉE) ---
    @Override
    @Transactional
    public RequestDto updateRequestInfo(UpdateRequesUpdateRquestInfotDto updateRequestInfoDto, Long requestId, String connectedUser, String username, ActionRequestType action) throws Exception {
        Request requestToUpdate = this.requestDao.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));
        this.checkAccessibilityForRequest(requestToUpdate, connectedUser, action);

        Certificate certificateToUpdate = requestToUpdate.getCertificate();
        if (certificateToUpdate == null) {
            throw new IllegalStateException("Critical error: Certificate associated with request id " + requestId + " is null.");
        }
        
        String traceModification = "Request information has been modified:";

        // (Collez ici TOUTE votre logique de mise à jour des champs de 'updateRequestInfo')
        // Exemple :
        if (StringUtils.hasText(updateRequestInfoDto.getApplicationName())) {
            //... etc.
        }

        RequestDto requestDtoForComment = this.entityToDto(requestToUpdate);
        this.commentService.processComment(requestDtoForComment, null, connectedUser, traceModification);
        requestToUpdate.setComment(requestDtoForComment.getComment());
        
        certificateToUpdate.setCodeAPChecked(true);

        // La transaction sauvegarde automatiquement les modifications sur requestToUpdate.
        return this.entityToDto(requestToUpdate);
    }


    // --- NOUVELLES MÉTHODES DE SERVICE POUR LES ACTIONS MÉTIER ---

    @Transactional
    public RequestDto updateRequestStatus(Long requestId, String newStatusName, String user, String comment) {
        Request requestToUpdate = requestDao.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));

        RequestStatus newStatus = requestStatusDao.findByName(newStatusName);
        if (newStatus == null) {
            throw new EntityNotFoundException("RequestStatus not found with name: " + newStatusName);
        }
        requestToUpdate.setRequestStatus(newStatus);

        if (StringUtils.hasText(comment)) {
            RequestDto tempDto = entityToDto(requestToUpdate);

            commentService.processComment(tempDto, null, user, comment);
            requestToUpdate.setComment(tempDto.getComment());
        }
        
        return entityToDto(requestToUpdate);
    }

    @Transactional
    public void takeRequest(Long requestId, String userId) throws Exception {
        Request request = requestDao.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Request not found: " + requestId));
            
        if (!"SUBMITTED".equals(request.getRequestStatus().getName()) && !"TAKEN".equals(request.getRequestStatus().getName())) {
            // Gérer le cas où la requête ne peut pas être prise
            throw new IllegalStateException("Request cannot be taken in its current state.");
        }

        request.setRequestStatus(findRequestStatusByName("TAKEN"));
        request.setTakedBy(userId);
        
        RequestDto dto = entityToDto(request);
        sendMailUtils.prepareAndSendEmail("template/request-taken-for-user.vm", dto, null, null);
        actionService.buildAndSaveAction(ActionRequestType.AFFECT_REQUEST, userId, dto, this.utilityService.getGatewayName());
    }

    @Transactional
    public void updateStatusAndSendEmail(Long requestId, String statusName, ActionRequestType actionType, String user, String templatePath, String subject) throws Exception {
        Request requestToUpdate = requestDao.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));

        RequestStatus newStatus = requestStatusDao.findByName(statusName);
        requestToUpdate.setRequestStatus(newStatus);
        
        RequestDto requestDto = entityToDto(requestToUpdate);

        sendMailUtils.prepareAndSendEmail(subject, templatePath, requestDto, null, null);
        actionService.buildAndSaveAction(actionType, user, requestDto, this.utilityService.getGatewayName());
    }

    // ... (Gardez toutes vos autres méthodes existantes : findById, dtoToEntity, etc.)
}
/////////////
@Service
public class RequestServiceImpl implements RequestService {

    // ... (tous vos @Autowired DAOs et Services : requestDao, requestStatusDao, commentService, etc.)
    // Assurez-vous que toutes les dépendances sont bien injectées.
    
    // --- MÉTHODE DE CRÉATION (RESTE INCHANGÉE MAIS NE DOIT PAS ÊTRE UTILISÉE POUR UPDATE) ---
    @Override
    @Transactional
    public RequestDto createRequest(RequestDto requestDto) throws Exception {
        // ... (votre logique de création existante)
        // Cette méthode est correcte pour la CRÉATION, mais ne doit JAMAIS être appelée pour une MISE À JOUR.
        Request request = dtoToEntity(requestDto);
        Request savedRequest = requestDao.save(request);
        return entityToDto(savedRequest);
    }

    // --- ANCIENNES MÉTHODES DE MISE À JOUR - MAINTENANT OBSOLÈTES ---
    @Override
    public RequestDto updateRequest(RequestDto requestDto) {
        throw new UnsupportedOperationException("DEPRECATED: Use a specific service method for the business action instead (e.g., updateRequestStatus, takeRequest).");
    }

    @Override
    public RequestDto updateRequestWithoutValidation(RequestDto requestDto) {
        throw new UnsupportedOperationException("DEPRECATED: Use a specific service method for the business action instead (e.g., updateRequestStatus, takeRequest).");
    }

    // --- MÉTHODE DE MISE À JOUR D'INFORMATIONS (CORRIGÉE) ---
    @Override
    @Transactional
    public RequestDto updateRequestInfo(UpdateRequesUpdateRquestInfotDto updateRequestInfoDto, Long requestId, String connectedUser, String username, ActionRequestType action) throws Exception {
        Request requestToUpdate = this.requestDao.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));
        this.checkAccessibilityForRequest(requestToUpdate, connectedUser, action);

        Certificate certificateToUpdate = requestToUpdate.getCertificate();
        if (certificateToUpdate == null) {
            throw new IllegalStateException("Critical error: Certificate associated with request id " + requestId + " is null.");
        }
        
        String traceModification = "Request information has been modified:";

        // (Collez ici TOUTE votre logique de mise à jour des champs de 'updateRequestInfo')
        // Exemple :
        if (StringUtils.hasText(updateRequestInfoDto.getApplicationName())) {
            //... etc.
        }

        RequestDto requestDtoForComment = this.entityToDto(requestToUpdate);
        this.commentService.processComment(requestDtoForComment, null, connectedUser, traceModification);
        requestToUpdate.setComment(requestDtoForComment.getComment());
        
        certificateToUpdate.setCodeAPChecked(true);

        // La transaction sauvegarde automatiquement les modifications sur requestToUpdate.
        return this.entityToDto(requestToUpdate);
    }


    // --- NOUVELLES MÉTHODES DE SERVICE POUR LES ACTIONS MÉTIER ---

    @Transactional
    public RequestDto updateRequestStatus(Long requestId, String newStatusName, String user, String comment) {
        Request requestToUpdate = requestDao.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));

        RequestStatus newStatus = requestStatusDao.findByName(newStatusName);
        if (newStatus == null) {
            throw new EntityNotFoundException("RequestStatus not found with name: " + newStatusName);
        }
        requestToUpdate.setRequestStatus(newStatus);

        if (StringUtils.hasText(comment)) {
            RequestDto tempDto = entityToDto(requestToUpdate);

            commentService.processComment(tempDto, null, user, comment);
            requestToUpdate.setComment(tempDto.getComment());
        }
        
        return entityToDto(requestToUpdate);
    }

    @Transactional
    public void takeRequest(Long requestId, String userId) throws Exception {
        Request request = requestDao.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Request not found: " + requestId));
            
        if (!"SUBMITTED".equals(request.getRequestStatus().getName()) && !"TAKEN".equals(request.getRequestStatus().getName())) {
            // Gérer le cas où la requête ne peut pas être prise
            throw new IllegalStateException("Request cannot be taken in its current state.");
        }

        request.setRequestStatus(findRequestStatusByName("TAKEN"));
        request.setTakedBy(userId);
        
        RequestDto dto = entityToDto(request);
        sendMailUtils.prepareAndSendEmail("template/request-taken-for-user.vm", dto, null, null);
        actionService.buildAndSaveAction(ActionRequestType.AFFECT_REQUEST, userId, dto, this.utilityService.getGatewayName());
    }

    @Transactional
    public void updateStatusAndSendEmail(Long requestId, String statusName, ActionRequestType actionType, String user, String templatePath, String subject) throws Exception {
        Request requestToUpdate = requestDao.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));

        RequestStatus newStatus = requestStatusDao.findByName(statusName);
        requestToUpdate.setRequestStatus(newStatus);
        
        RequestDto requestDto = entityToDto(requestToUpdate);

        sendMailUtils.prepareAndSendEmail(subject, templatePath, requestDto, null, null);
        actionService.buildAndSaveAction(actionType, user, requestDto, this.utilityService.getGatewayName());
    }

    // ... (Gardez toutes vos autres méthodes existantes : findById, dtoToEntity, etc.)
}
////// new/////////////////////////
@Service
public class RequestServiceImpl implements RequestService {

    // --- TOUTES VOS DÉPENDANCES INJECTÉES ---
    // (Assurez-vous que toutes celles utilisées dans le code ci-dessous sont présentes)
    @Autowired
    private RequestDao requestDao;
    
    @Autowired
    private RequestStatusDao requestStatusDao;
    
    @Autowired
    private CommentService commentService;

    @Autowired
    private ActionService actionService;

    // ... etc pour tous vos DAOs et Services.

    
    // --- MÉTHODE DE CRÉATION (INCHANGÉE) ---
    // Correcte pour la création, ne pas utiliser pour une mise à jour.
    @Override
    @Transactional
    public RequestDto createRequest(RequestDto requestDto) throws Exception {
        // ... (votre logique de création existante, qui est probablement correcte)
        // Par exemple:
        this.validateRequest(requestDto);
        Request request = dtoToEntity(requestDto);
        Request savedRequest = requestDao.save(request);
        return entityToDto(savedRequest);
    }


    // --- ANCIENNES MÉTHODES DE MISE À JOUR - DÉPRÉCIÉES ---
    // Elles lèvent une exception pour vous obliger à utiliser les nouvelles méthodes spécifiques.

    @Override
    public RequestDto updateRequest(RequestDto requestDto) {
        throw new UnsupportedOperationException("DEPRECATED: Use a specific service method for the business action instead (e.g., updateRequestStatus). This method causes bugs.");
    }

    @Override
    public RequestDto updateRequestWithoutValidation(RequestDto requestDto) {
        throw new UnsupportedOperationException("DEPRECATED: Use a specific service method for the business action instead (e.g., updateRequestStatus). This method causes bugs.");
    }


    // --- MÉTHODE DE MISE À JOUR D'INFORMATIONS SPÉCIFIQUE (CORRIGÉE) ---
    @Override
    @Transactional
    public RequestDto updateRequestInfo(UpdateRequesUpdateRquestInfotDto updateRequestInfoDto, Long requestId, String connectedUser, String username, ActionRequestType action) throws Exception {
        Request requestToUpdate = this.requestDao.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));
        this.checkAccessibilityForRequest(requestToUpdate, connectedUser, action);

        Certificate certificateToUpdate = requestToUpdate.getCertificate();
        if (certificateToUpdate == null) {
            throw new IllegalStateException("Critical error: Certificate associated with request id " + requestId + " is null.");
        }
        
        String traceModification = "Request information has been modified:";

        // (Collez ici TOUTE votre logique de mise à jour des champs de 'updateRequestInfo')
        // ...
        // ...
        // ...

        // Utilisation correcte du CommentService
        RequestDto tempDto = this.entityToDto(requestToUpdate);
        this.commentService.processComment(tempDto, null, connectedUser, traceModification);
        requestToUpdate.setComment(tempDto.getComment());
        
        certificateToUpdate.setCodeAPChecked(true);

        // La transaction sauvegarde automatiquement. On retourne le DTO à jour.
        return this.entityToDto(requestToUpdate);
    }


    // --- NOUVELLES MÉTHODES DE SERVICE POUR LES ACTIONS MÉTIER ---

    /**
     * Met à jour le statut et le commentaire d'une requête.
     * C'est la nouvelle méthode sécurisée pour la plupart des changements d'état.
     * @return Le RequestDto mis à jour.
     */
    @Override
    @Transactional
    public RequestDto updateRequestStatus(Long requestId, String newStatusName, String user, String comment) {
        Request requestToUpdate = requestDao.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));

        RequestStatus newStatus = requestStatusDao.findByName(newStatusName);
        if (newStatus == null) {
            throw new EntityNotFoundException("RequestStatus not found with name: " + newStatusName);
        }
        requestToUpdate.setRequestStatus(newStatus);

        if (StringUtils.hasText(comment)) {
            RequestDto tempDto = entityToDto(requestToUpdate);
            // On passe un DTO temporaire au service de commentaire
            commentService.processComment(tempDto, null, user, comment);
            // On récupère le résultat et on l'applique à notre VRAIE entité
            requestToUpdate.setComment(tempDto.getComment());
        }
        
        // La transaction sauvegarde. On retourne le DTO à jour pour le contrôleur.
        return entityToDto(requestToUpdate);
    }

    /**
     * Prend en charge une requête. Ne gère que la modification en base de données.
     * @return Le RequestDto mis à jour pour que le contrôleur orchestre le reste.
     */
    @Override
    @Transactional
    public RequestDto takeRequest(Long requestId, String userId) {
        Request request = requestDao.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Request not found: ".concat(requestId.toString())));
            
        if (!"SUBMITTED".equals(request.getRequestStatus().getName()) && !"TAKEN".equals(request.getRequestStatus().getName())) {
            throw new IllegalStateException("Request cannot be taken in its current state.");
        }

        request.setRequestStatus(findRequestStatusByName("TAKEN"));
        request.setTakedBy(userId);
        
        return entityToDto(request);
    }
    
    // --- CONSERVEZ TOUTES VOS AUTRES MÉTHODES EXISTANTES ---
    // (Toutes les méthodes de recherche comme findById, findBySerialNumber, 
    // les convertisseurs comme entityToDto, dtoToEntity, etc. doivent être gardées)

    // ...
    // ...
    // (Le reste de votre fichier)
    // ...
    // ...
}
/////////////////////////
@Override
@Transactional
public RequestDto updateFullRequest(Long requestId, RequestDto updatedDto, String user) throws Exception {
    
    // --- ÉTAPE 1: CHARGER L'ENTITÉ MANAGÉE ---
    Request requestToUpdate = requestDao.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Request to update not found with id: " + requestId));

    // --- ÉTAPE 2: VALIDER (SI NÉCESSAIRE) ---
    this.validateRequest(updatedDto);

    // --- ÉTAPE 3: FUSIONNER LES DONNÉES DU DTO VERS L'ENTITÉ ---
    // ** Champs simples de l'entité Request **
    requestToUpdate.setComment(updatedDto.getComment());
    requestToUpdate.setUsage(updatedDto.getUsage());
    requestToUpdate.setEndDate(updatedDto.getEndDate());
    // ... Ajoutez ici tous les autres champs simples de Request que vous devez mettre à jour.

    // ** Gestion des relations (objets liés) **
    if (updatedDto.getRequestStatus() != null) {
        RequestStatus newStatus = requestStatusDao.findByName(updatedDto.getRequestStatus().getName());
        requestToUpdate.setRequestStatus(newStatus);
    }

    // ** Fusion de l'entité Certificate associée **
    Certificate certificateToUpdate = requestToUpdate.getCertificate();
    CertificateDto certificateDto = updatedDto.getCertificate();
    if (certificateToUpdate != null && certificateDto != null) {
        // Mettre à jour les champs simples du certificat
        certificateToUpdate.setCommonName(certificateDto.getCommonName());
        // ... Ajoutez tous les autres champs simples du certificat.

        // ** GESTION SÉCURISÉE DE LA COLLECTION `sans` **
        // 1. Vider l'ancienne collection
        certificateToUpdate.getSans().clear(); // `orphanRemoval=true` va supprimer les anciens en BDD

        // 2. Ajouter les nouveaux éléments depuis le DTO
        if (certificateDto.getSans() != null) {
            for (SanDto sanDto : certificateDto.getSans()) {
                San newSan = new San();
                newSan.setType(sanDto.getType());
                newSan.setSanValue(sanDto.getValue());
                newSan.setCertificate(certificateToUpdate); // Lier au parent
                certificateToUpdate.getSans().add(newSan);
            }
        }
    }

    // ** GESTION SÉCURISÉE DE LA COLLECTION `contacts` **
    // 1. Vider l'ancienne collection
    requestToUpdate.getContacts().clear(); // `orphanRemoval=true` va supprimer les anciens en BDD

    // 2. Ajouter les nouveaux éléments depuis le DTO
    if (updatedDto.getContacts() != null) {
        for (ContactDto contactDto : updatedDto.getContacts()) {
            Contact newContact = new Contact();
            // ... mapper les champs de contactDto vers newContact ...
            newContact.setRequest(requestToUpdate); // Lier à la requête parente
            requestToUpdate.getContacts().add(newContact);
        }
    }
    
    // --- ÉTAPE 4: PERSISTANCE ---
    // La transaction se chargera de la sauvegarde.

    // --- ÉTAPE 5: RETOURNER LE RÉSULTAT ---
    return this.entityToDto(requestToUpdate);
}
///////////////////////controller /////////////
Fichier à modifier : RequestController.java
1. Méthode updateRequest (Endpoint: POST /requests/{id})
Cette méthode est la plus complexe. Elle fusionne deux DTOs avant d'appeler le service.
REMPLACEZ PAR :
code
Java
@PostMapping("/requests/{id}")
@Transactional
public ResponseEntity<?> updateRequest(@PathVariable("id") Long id, 
                                     @Valid @RequestBody RequestDtoImpl newRequestDto, 
                                     UriComponentsBuilder uriComponentsBuilder, 
                                     ServletRequest servletRequest) throws Exception {

    RequestDto oldRequest = requestService.findById(id);
    if (oldRequest.getId() == 0) {
        return utilityService.certisResponse(ResponseType.ERROR, HttpStatus.NOT_FOUND, "request.not.exists");
    }

    String refoguid = utilityService.getConnectedUserOrApi(servletRequest);
    utilityService.checkUserAccessOnRequest(servletRequest, oldRequest, ActionRequestType.UPDATE_REQUEST);

    // --- Début de votre logique de fusion métier (reste dans le contrôleur) ---
    // (Cette partie prépare le DTO final à envoyer au service)
    String newRequestCsr = this.fileManageService.extractCsr(newRequestDto, Boolean.TRUE);
    // ...
    // ... Toute votre logique existante pour préparer l'objet `oldRequest` ...
    oldRequest.setContacts(newRequestDto.getContacts());
    oldRequest.getCertificate().setComment(newRequestDto.getCertificate().getComment());
    // ... etc.
    // --- Fin de la logique de fusion ---

    // --- APPEL À LA NOUVELLE MÉTHODE DE SERVICE SÉCURISÉE ---
    RequestDto requestDtoResult = requestService.updateFullRequest(id, oldRequest, refoguid);

    LOGGER.info("Request modified " + requestDtoResult.getId());
    
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(uriComponentsBuilder.path(URL_READ_REQUEST).buildAndExpand(requestDtoResult.getId()).toUri());

    return new ResponseEntity<>(
        new DefaultResponse<>(requestDtoResult.getId(), requestDtoResult.getRequestStatus().getName()),
        headers,
        HttpStatus.OK
    );
}
2. Méthode rejectRequest (Endpoint: PATCH /admin/reject/{requestId})
REMPLACEZ PAR :
code
Java
@PatchMapping("/admin/reject/{requestId}")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public ResponseEntity<?> rejectRequest(@PathVariable("requestId") Long requestId, @RequestBody(required = false) RequestDtoImpl requestDtoCom, Principal principal, ServletRequest servletRequest) throws Exception {
    String refoguid = principal.getName();
    String comment = (requestDtoCom != null) ? requestDtoCom.getComment() : "";
    String authorComment = String.format("Reject reason: %s", org.apache.commons.lang3.StringUtils.defaultIfEmpty(comment, ""));

    // 1. Appel au service pour la mise à jour BDD
    RequestDto updatedDto = requestService.updateRequestStatus(requestId, "REJECTED", refoguid, authorComment);

    // 2. Orchestration des actions post-mise à jour
    sendMailUtils.prepareAndSendEmail(SUBJECT_REJECT, "template/request-refuse-for-user.vm", updatedDto, null, null);
    this.actionService.buildAndSaveAction(ActionRequestType.REJECTED_ADMIN_REQUEST, refoguid, updatedDto, this.utilityService.getGatewayName());

    return utilityService.certisResponse(ResponseType.OK, HttpStatus.OK, "resp.reject");
}
3. Méthode takeRequest (Endpoint: PATCH /admin/take/{requestId})
REMPLACEZ PAR :
code
Java
@PatchMapping("/admin/take/{requestId}")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public ResponseEntity<?> takeRequest(@PathVariable("requestId") Long requestId, @RequestParam("uid") String uid) throws Exception {
    RequestDto requestDto = requestService.findById(requestId);
    if (StringUtils.isEmpty(uid) || requestDto == null) {
        return utilityService.certisResponse(ResponseType.ERROR, HttpStatus.NOT_FOUND, "request.not.exists");
    }

    // 1. Appel au service pour la mise à jour BDD
    RequestDto updatedRequestDto = requestService.takeRequest(requestId, uid);
    
    // 2. Enregistrer l'action
    this.actionService.buildAndSaveAction(ActionRequestType.AFFECT_REQUEST, uid, updatedRequestDto, this.utilityService.getGatewayName());
    
    // 3. Envoyer l'email
    sendMailUtils.prepareAndSendEmail("template/request-taken-for-user.vm", updatedRequestDto, null, null);
    
    return utilityService.certisResponse(ResponseType.OK, HttpStatus.OK, "resp.take");
}
4. Méthode cancelRequest (Endpoint: PATCH /cancel/{requestId})
REMPLACEZ PAR :
code
Java
@PatchMapping("/cancel/{requestId}")
public ResponseEntity<?> cancelRequest(@PathVariable("requestId") Long requestId, ServletRequest servletRequest) throws Exception {
    String refoguid = utilityService.getConnectedUserOrApi(servletRequest);
    RequestDto requestDto = requestService.findById(requestId);
    utilityService.checkUserAccessOnRequest(servletRequest, requestDto, ActionRequestType.ANNULER_REQUEST);

    if (!"DONE".equals(requestDto.getRequestStatus().getName()) && !"TO_REVOKE".equals(requestDto.getRequestStatus().getName()) && !"VALIDATED".equals(requestDto.getRequestStatus().getName())) {
        
        RequestDto updatedDto = requestService.updateRequestStatus(requestId, "CANCELED", refoguid, "Request canceled by user.");
        
        this.actionService.buildAndSaveAction(ActionRequestType.ANNULER_REQUEST, refoguid, updatedDto, this.utilityService.getGatewayName());
        
        return utilityService.certisResponse(ResponseType.OK, HttpStatus.OK, "resp.cancel");
    }
    return utilityService.certisResponse(ResponseType.NOT_ACCEPTABLE, HttpStatus.NOT_ACCEPTABLE, "error.request.cancel");
}
5. Méthode validateRequest (Endpoint: PATCH /admin/validate/{requestId})
REMPLACEZ PAR :
code
Java
@PatchMapping("/admin/validate/{requestId}")
@Transactional
public ResponseEntity<?> validateRequest(@PathVariable("requestId") Long requestId, ServletRequest servletRequest) throws Exception {
    String refoguid = this.utilityService.getConnectedUserOrApi(servletRequest);
    RequestDto requestDto = requestService.findById(requestId);

    if (requestDto == null) {
        return utilityService.certisResponse(ResponseType.ERROR, HttpStatus.NOT_FOUND, "request.not.exists");
    }

    if (!"TAKEN".equals(requestDto.getRequestStatus().getName())) {
        return utilityService.certisResponse(ResponseType.NOT_ACCEPTABLE, HttpStatus.NOT_ACCEPTABLE, "request.error.reject");
    }

    RequestDto updatedDto = requestService.updateRequestStatus(requestId, "VALIDATED", refoguid, "Request validated.");
    
    sendMailUtils.prepareAndSendEmail(SUBJECT_VALIDATION, "template/request-validate-for-user.vm", updatedDto, null, null);
    
    this.actionService.buildAndSaveAction(ActionRequestType.VALIDATE_REQUEST, refoguid, updatedDto, this.utilityService.getGatewayName());

    return utilityService.certisResponse(ResponseType.OK, HttpStatus.OK, "resp.validate");
}
6. Méthode updateComment (Endpoint: POST /comment/{requestId})
Cette méthode est complexe et appelle saveOrUpdateComment qui doit être revue.
REMPLACEZ PAR (créez une nouvelle méthode de service updateRequestComment) :
code
Java
@PostMapping("/comment/{requestId}")
public ResponseEntity<?> updateComment(@PathVariable("requestId") Long id, @RequestBody RequestDtoImpl requestDtoComImpl, ServletRequest servletRequest) throws Exception {
    String refoguid = utilityService.getConnectedUserOrApi(servletRequest);
    RequestDto oldRequest = requestService.findById(id);
    // ... vérifications ...

    // Appel à une nouvelle méthode de service dédiée
    RequestDto requestDtoResult = requestService.updateRequestComment(id, requestDtoComImpl.getComment(), refoguid);

    // ... logique d'envoi d'email
    return new ResponseEntity<>(requestDtoResult, HttpStatus.OK);
}

// N'oubliez pas d'ajouter `updateRequestComment` dans RequestServiceImpl
@Transactional
public RequestDto updateRequestComment(Long requestId, String comment, String user) {
    Request request = requestDao.findById(requestId).orElseThrow(...);
    request.setComment(comment);
    // La transaction sauvegarde
    return entityToDto(request);
}
Résumé de votre tâche
Pour chaque autre endpoint de mise à jour que nous avons identifié (comme addCertificate, notRenewCertificate, submitWafRequest, etc.), vous devez suivre ce même patron :
Créer une méthode métier spécifique et sécurisée dans RequestServiceImpl si elle n'existe pas déjà (par exemple, addCertificateToRequest(Long requestId, ...)).
Remplacer le corps de la méthode du contrôleur pour qu'elle appelle cette nouvelle méthode de service.
Orchestrer les appels à actionService et sendMailUtils dans le contrôleur, après l'appel au service principal.
///////////////////////
 @Override
    @Transactional
    public RequestDto updateRequestComment(Long requestId, String newComment, String user) throws Exception {
        
        // 1. Charger l'entité managée depuis la base de données
        Request requestToUpdate = requestDao.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));

        // 2. Préparer le DTO temporaire pour le CommentService
        // On prend l'état AVANT la modification pour que `processComment` puisse comparer.
        RequestDto oldRequestStateDto = entityToDto(requestToUpdate);

        // 3. Utiliser le CommentService pour formater et appliquer le commentaire
        // Le service va modifier le DTO `oldRequestStateDto` directement
        commentService.processComment(oldRequestStateDto, oldRequestStateDto, user, newComment);

        // 4. Appliquer le commentaire finalisé à notre entité managée
        requestToUpdate.setComment(oldRequestStateDto.getComment());
        
        // 5. Laisser @Transactional sauvegarder les changements.
        // On retourne un DTO frais de l'entité mise à jour.
        return entityToDto(requestToUpdate);
    }
	////// file ///////////////////
	code
Html
<p-fileUpload name="myfile[]" 
              url="https://www.primefaces.org/primeng-v10-lts/upload.php">
</p-fileUpload>
Exemple de code corrigé :
Remplacez l'URL par le point de terminaison (endpoint) de votre propre API. L'URL exacte dépend de votre architecture backend.
code
Html
<!-- Remplacer par l'URL de votre propre API ! -->
<p-fileUpload name="myfile[]" 
              url="/api/v1/demandes/enregistrer">
</p-fileUpload>
ou si vous le gérez dans le fichier .ts :
code
TypeScript
// Dans votre fichier .ts
enregistrerDemande() {
  const donneesDuFormulaire = this.form.value;
  // L'URL incorrecte est probablement dans ce service
  this.monApiService.enregistrer(donneesDuFormulaire).subscribe(...); 
}
//////////
Rapprocher le champ de saisie (input) et la liste déroulante (dropdown).
Arrondir les bords des badges à l'intérieur de la liste.
Réduire la largeur de la liste déroulante pour qu'elle s'adapte à la taille du texte des badges.
Nous pouvons faire tout cela avec quelques modifications dans vos fichiers HTML et CSS.
Étape 1 : Préparer le HTML
Pour pouvoir cibler précisément nos éléments avec le CSS, nous allons nous assurer que nos classes personnalisées sont bien en place.
Ouvrez votre fichier request-detail-section.component.html et vérifiez que la ligne des SANs ressemble à ceci (avec san-input-row et san-type-column) :
code
Html
<!-- DANS request-detail-section.component.html -->

<!-- ... à l'intérieur de la boucle *ngFor ... -->
<div class="ui-g ui-fluid p-align-center san-input-row">
  
  <!-- Champ de saisie -->
  <div class="ui-g-7">
    <input type="text" pInputText formControlName="value" ...>
  </div>

  <!-- Conteneur du Dropdown -->
  <div class="ui-g-4 san-type-column">
    <p-dropdown 
        [options]="sanTypes" 
        formControlName="type"
        optionLabel="label"
        optionValue="value">
      
      <ng-template let-item pTemplate="selectedItem">
          <p-tag [value]="item.label" [styleClass]="item.styleClass"></p-tag>
      </ng-template>
      <ng-template let-item pTemplate="item">
          <p-tag [value]="item.label" [styleClass]="item.styleClass"></p-tag>
      </ng-template>
    </p-dropdown>
  </div>

  <!-- ... bouton de suppression ... -->
</div>
Étape 2 : Ajouter le Code CSS Complet
Maintenant, ouvrez votre fichier de style request-detail-section.component.css et ajoutez le bloc de code suivant. Ce bloc unique va gérer les trois modifications que vous avez demandées.
code
Css
/* DANS request-detail-section.component.css */

/* 
  On utilise :host ::ng-deep pour s'assurer que nos styles peuvent
  modifier les composants de PrimeNG sans être bloqués par l'encapsulation d'Angular.
*/

/* --- 1. Rapprocher l'input et le dropdown --- */
:host ::ng-deep .san-input-row .ui-g-7 {
  padding-right: 2px; /* Réduit l'espace à droite de l'input */
}
:host ::ng-deep .san-input-row .ui-g-4 {
  padding-left: 2px;  /* Réduit l'espace à gauche du dropdown */
}


/* --- 2. Arrondir les bords des badges --- */
/* On cible les badges (p-tag) à l'intérieur de notre dropdown */
:host ::ng-deep .san-type-column .p-tag {
  border-radius: 12px; /* Donne une forme de "pilule" arrondie. Ajustez la valeur si besoin. */
}


/* --- 3. Réduire la largeur du dropdown --- */
/* On cible la colonne qui contient le dropdown */
:host ::ng-deep .san-type-column {
  /* On dit à la grille de ne pas étirer cette colonne */
  flex-grow: 0; 
  /* La largeur s'adaptera à son contenu */
  width: auto;
  /* On garantit une largeur minimale pour un bon affichage */
  min-width: 130px; 
}
Explication du Code CSS
Pour rapprocher les éléments : On réduit le padding (marge intérieure) des colonnes de la grille PrimeNG (.ui-g-7 et .ui-g-4), mais uniquement à l'intérieur de notre ligne san-input-row.
Pour arrondir les badges : On cible les composants <p-tag> (qui deviennent des .p-tag en CSS) et on leur applique un border-radius généreux.
Pour réduire la largeur du dropdown : On modifie le comportement de la colonne qui le contient (.san-type-column).
width: auto; lui demande de prendre juste la place nécessaire pour son contenu (le badge sélectionné).
flex-grow: 0; l'empêche de s'étirer pour remplir l'espace vide.
min-width: 130px; assure qu'il ne deviendra pas trop petit si un badge a un texte très court (comme "IP").
Après avoir ajouté ce code, sauvegardez et rechargez votre page avec un rechargement forcé (Ctrl+F5 ou Cmd+Shift+R) pour vous assurer que le navigateur charge bien le nouveau fichier CSS. Vous devriez voir les trois changements de design appliqués.
91,4s