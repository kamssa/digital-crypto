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
////////////////////
Étape 1 : Choisir une clé de traduction
Une clé claire et descriptive serait :
certificateInformationSection.noComment
Étape 2 : Ajouter la clé à vos fichiers de traduction
Ouvrez vos fichiers fr.json et en.json et ajoutez la nouvelle clé.
Dans fr.json :
code
JSON
{
  "certificateInformationSection": {
    "title": "Détail du certificat",
    "history": "Historique",
    "certificateComment": "Commentaire du certificat",
    // ... vos autres clés

    // =======================================================
    //     ▼▼▼   AJOUTEZ CETTE NOUVELLE CLÉ ICI   ▼▼▼
    // =======================================================
    "noComment": "Aucun commentaire."
  }
}
Dans en.json :
code
JSON
{
  "certificateInformationSection": {
    "title": "Certificate Details",
    "history": "History",
    "certificateComment": "Certificate Comment",
    // ... your other keys

    // =======================================================
    //     ▼▼▼   ADD THIS NEW KEY HERE   ▼▼▼
    // =======================================================
    "noComment": "No comment."
  }
}
Étape 3 : Mettre à jour le fichier HTML
Maintenant, nous allons modifier le fichier certificate-details.component.html pour qu'il utilise cette nouvelle clé de traduction.
L'astuce consiste à utiliser le pipe translate comme "valeur par défaut" si certificateRequest.certificate.comment est vide ou nul. Malheureusement, le pipe translate seul ne peut pas faire ça. Nous avons besoin d'une petite astuce dans le composant .ts.
Dans certificate-details.component.ts :
Nous allons ajouter une méthode "getter" qui gère cette logique. C'est la manière la plus propre de le faire.
code
TypeScript
// CHEMIN : src/app/content/dashboard/certificate-details/certificate-details.component.ts

import { TranslateService } from '@ngx-translate/core'; // Assurez-vous que c'est importé

@Component({ /* ... */ })
export class CertificateDetailsComponent implements OnInit {

  // ... (vos propriétés existantes)
  certificateRequest: any;

  constructor(
    private translate: TranslateService, // Assurez-vous que TranslateService est injecté
    // ... vos autres services
  ) {}

  // ... (vos autres méthodes)

  // =======================================================
  //     ▼▼▼   AJOUTEZ CE GETTER À VOTRE CLASSE   ▼▼▼
  // =======================================================
  /**
   * Retourne le commentaire du certificat, ou une clé de traduction par défaut
   * si le commentaire est vide.
   */
  get displayCertificateComment(): string {
    const comment = this.certificateRequest?.certificate?.comment;
    // Si le commentaire est "falsy" (null, undefined, ou une chaîne vide),
    // on retourne la clé de traduction. Sinon, on retourne le commentaire lui-même.
    return comment ? comment : this.translate.instant('certificateInformationSection.noComment');
  }

}
Dans certificate-details.component.html :
Maintenant, le template devient très simple. On appelle juste notre nouveau getter.
code
Html
<!-- CHEMIN : src/app/content/dashboard/certificate-details/certificate-details.component.html -->

<!-- ... -->

<!-- NOUVEAU CHAMP "COMMENTAIRE" -->
<div class="row">
    <div class="ui-g-3">
        <label class="pull-right" style="font-weight: bold;">
            {{ 'certificateInformationSection.certificateComment' | translate }}:
        </label>
    </div>
    <div class="ui-g-9">
        <!-- On utilise le nouveau getter qui gère la logique -->
        <span [innerHTML]="displayCertificateComment"></span>
    </div>
</div>

<!-- ... -->
///////////
Fichier : src/assets/i18n/fr.json (ou l'équivalent)
code
JSON
{
  "requestDetailSection": {
    "addSan": "Ajouter un SAN",
    "errors": {
      "sanFormat": {
        "DEFAULT": "Format invalide pour le type sélectionné.",
        "DNSNAME": "Format DNS invalide. Exemple : www.domaine.com ou *.domaine.com",
        "IPADDRESS": "Format d'adresse IP invalide. Exemple : 192.168.1.1 ou 2001:db8::1",
        "RFC822NAME": "Format d'email invalide. Exemple : utilisateur@domaine.com",
        "URI": "Format d'URI invalide. Exemple : https://www.domaine.com",
        "OTHERNAME_UPN": "Format UPN invalide. Exemple : utilisateur@corp.domaine.com",
        "OTHERNAME_GUID": "Format GUID invalide. Doit se terminer par # suivi de 32 caractères alphanumériques."
      }
    }
  }
}
en.json (exemple)
code
JSON
{
  "requestDetailSection": {
    "addSan": "Add a SAN",
    "errors": {
      "sanFormat": {
        "DEFAULT": "Invalid format for the selected type.",
        "DNSNAME": "Invalid DNS format. Example: www.domain.com or *.domain.com",
        "IPADDRESS": "Invalid IP address format. Example: 192.168.1.1 or 2001:db8::1",
        "RFC822NAME": "Invalid email format. Example: user@domain.com",
        "URI": "Invalid URI format. Example: https://www.domain.com",
        "OTHERNAME_UPN": "Invalid UPN format. Example: user@corp.domain.com",
        "OTHERNAME_GUID": "Invalid GUID format. Must end with # followed by 32 alphanumeric characters."
      }
    }
  }
}
Étape 2 : Modifier le template HTML
Nous allons modifier le template pour qu'il construise dynamiquement la bonne clé de traduction en se basant sur le type de SAN sélectionné.
Fichier : src/app/form/request-detail-section/request-detail-section.component.html
code
Html
<!-- Localisez la boucle *ngFor des SANs -->
<div formArrayName="sans">
    <div *ngFor="let sanGroup of sans.controls; let i = index" [formGroupName]="i" class="ui-g-12 ui-g-nopad" style="margin-bottom: 15px;">
        <div class="ui-g ui-fluid p-align-center">
            
            <!-- ... Vos divs pour l'input, le dropdown et le bouton de suppression ... -->
            
            <!-- MODIFIER LE BLOC D'ERREUR -->
            <div class="ui-g-12" *ngIf="sanGroup.get('value')?.invalid && (sanGroup.get('value')?.dirty || sanGroup.get('value')?.touched)">
                
                <!-- On vérifie spécifiquement l'erreur 'pattern' -->
                <small class="p-error" *ngIf="sanGroup.get('value')?.errors?.pattern">
                    <!-- 
                      CONSTRUCTION DYNAMIQUE DE LA CLÉ :
                      On prend la base 'requestDetailSection.errors.sanFormat.'
                      et on y ajoute la valeur du champ 'type' du formulaire (ex: 'DNSNAME').
                      Le pipe 'translate' fera le reste.
                    -->
                    {{ 'requestDetailSection.errors.sanFormat.' + sanGroup.get('type').value | translate }}
                </small>
                
            </div>

        </div>
    </div>
</div>
////////////////////
// La constante pour la regex IPv4 est conservée.
    private static final String IPV4_REGEX = "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";

    // L'ancienne constante IPV6_REGEX a été supprimée.

    // MODIFIÉ : La constante IPADDRESS ne compile plus que la regex pour IPv4.
    public static final Pattern IPADDRESS = Pattern.compile("^" + IPV4_REGEX + "$");

/////////////////////////
export const SANS_REGEX_PATTERNS = {
  DNSNAME: /^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,6}$|^(\*\.)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,6}$/i,
  RFC822NAME: /^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/i,
  
  // MODIFIÉ : La regex ne valide plus que les adresses IPv4.
  IPADDRESS: /^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/,

  OTHERNAME_GUID: /^#?[a-z0-9]{32}$/i,
  OTHERNAME_UPN: /^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/i,
  URI: /^(https?|ldaps?|ftp|file|tag|urn|data|tel):\/\/[\w\.\+&#\/%?=~_\-!:,|'*]+$/i
};
///////////////////
Fichier : src/app/shared/ts.utils.ts
code
TypeScript
// NOUVEAU styleMapper avec des noms de classe sémantiques
export const styleMapper = {
  'DNSNAME': 'badge-dnsname',        // anciennement 'badge-info'
  'RFC822NAME': 'badge-rfc822name',   // anciennement 'badge-success'
  'IPADDRESS': 'badge-ipaddress',      // anciennement 'badge-warning'
  'URI': 'badge-uri',                // anciennement 'badge-danger'
  'OTHERNAME_GUID': 'badge-othername', // regroupé pour la simplicité
  'OTHERNAME_UPN': 'badge-othername'   // regroupé pour la simplicité
};

// Le tableau SAN_TYPE n'a pas besoin d'être modifié,
// il utilisera automatiquement les nouvelles valeurs du styleMapper.
export const SAN_TYPE = [
  { label: 'DNSNAME',    value: 'DNSNAME',    styleClass: styleMapper['DNSNAME'] },
  { label: 'IPADDRESS',  value: 'IPADDRESS',  styleClass: styleMapper['IPADDRESS'] },
  { label: 'URI',        value: 'URI',        styleClass: styleMapper['URI'] },
  { label: 'RFC822NAME', value: 'RFC822NAME', styleClass: styleMapper['RFC822NAME'] },
  { label: 'OTHERNAME_GUID', value: 'OTHERNAME_GUID', styleClass: styleMapper['OTHERNAME_GUID'] },
  { label: 'OTHERNAME_UPN',  value: 'OTHERNAME_UPN',  styleClass: styleMapper['OTHERNAME_UPN'] },
];

// ... reste du fichier
Analyse : C'est tout ce qu'il y a à faire côté TypeScript. C'est propre et la logique reste au même endroit.
Étape 2 : Mettre à jour le SCSS (badges.scss)
Maintenant, nous allons créer les nouvelles classes CSS et leur assigner les couleurs de votre groupe $color-bullet-* que l'on voit dans variables.scss. Je vais faire une proposition d'association de couleurs, que vous pourrez ajuster selon vos goûts.
Fichier : src/assets/styles/badges.scss
code
Scss
/* 
  Assurez-vous que ce fichier est bien importé dans votre styles.scss global
  pour que les variables de couleurs soient disponibles.
  
  Exemple dans src/styles.scss :
  @import 'assets/styles/variables';
  @import 'assets/styles/badges';
*/

// On utilise les nouvelles classes définies dans le styleMapper
// et les couleurs de la palette "bullet".

.badge-dnsname {
  background-color: $color-bullet-blue !important; // ex: #157efb
  color: white;
}

.badge-rfc822name {
  background-color: $color-bullet-green !important; // ex: #4ae18c
  color: white;
}

.badge-ipaddress {
  background-color: $color-bullet-orange !important; // ex: #ffac5b
  color: white;
}

.badge-uri {
  background-color: $color-bullet-violet !important; // ex: #ce3fa9
  color: white;
}

.badge-othername {
  background-color: $color-bullet-gray !important; // ex: #bfe1f4
  color: #333; // Le texte noir peut être plus lisible sur un gris clair
}
////////
:host {

  // ::ng-deep est nécessaire pour "casser" l'encapsulation des styles
  // et pouvoir styler les classes de PrimeNG comme ui-g-*.
  ::ng-deep {

    // On cible la ligne spécifique pour ne pas impacter d'autres grilles
    .san-input-row {
      
      // La colonne contenant l'input (7/12)
      .ui-g-7 {
        // On réduit ou supprime le padding à droite
        padding-right: 0.25rem; // Essayez avec 4px ou 0.25rem au lieu de 0
      }

      // La colonne contenant le dropdown (4/12)
      .ui-g-4 {
        // On réduit ou supprime le padding à gauche
        padding-left: 0.25rem; // Essayez avec 4px ou 0.25rem au lieu de 0
      }
    }
  }
}
/////////////////////////////////

Code HTML Complet pour la Section SANs
Copiez ce bloc de code entier et remplacez la section correspondante dans votre fichier request-detail-section.component.html.
code
Html
<!-- =================================================================== -->
<!-- DÉBUT DE LA SECTION SANs (Subject Alternative Name)                 -->
<!-- =================================================================== -->

<fieldset class="section-style">
  <div class="row">
    <!-- Label pour la section -->
    <div class="ui-g-3">
      <label>
        *SANs (Subject Alternative Name)
        <i class="fa fa-info-circle"></i>
      </label>
    </div>

    <!-- Conteneur principal pour les champs SANs -->
    <div class="ui-g-9">

      <!-- On attache le formArray 'sans' à ce conteneur -->
      <div formArrayName="sans">

        <!-- Boucle pour afficher chaque ligne de SAN (input + dropdown + bouton) -->
        <!-- Chaque ligne est maintenant un conteneur Flexbox grâce à la classe "san-input-row" -->
        <div *ngFor="let san of sans.controls; let i = index" [formGroupName]="i" class="san-input-row">

          <!-- 1. Colonne pour l'input de la valeur -->
          <div class="san-value-column">
            <input 
              type="text" 
              pInputText 
              formControlName="value" 
              placeholder="ex: www.site.intra" 
              style="width: 100%;">
          </div>

          <!-- 2. Colonne pour le dropdown du type -->
          <div class="san-type-column">
            <p-dropdown 
              [options]="sanTypes" 
              formControlName="type" 
              optionLabel="label" 
              optionValue="value"
              style="min-width: 160px;"
              appendTo="body">
              
              <!-- Template pour l'élément SÉLECTIONNÉ dans le dropdown -->
              <ng-template let-item pTemplate="selectedItem">
                <p-tag [value]="item.label" [styleClass]="item.styleClass"></p-tag>
              </ng-template>

              <!-- Template pour les éléments DANS LA LISTE du dropdown -->
              <ng-template let-item pTemplate="item">
                <p-tag [value]="item.label" [styleClass]="item.styleClass"></p-tag>
              </ng-template>
            </p-dropdown>
          </div>

          <!-- 3. Colonne pour le bouton de suppression -->
          <div class="san-action-column">
            <button 
              pButton 
              type="button" 
              icon="pi pi-trash" 
              class="p-button-danger p-button-text" 
              (click)="removeSan(i)"
              [disabled]="sans.controls.length <= 1"
              pTooltip="Supprimer ce SAN" 
              tooltipPosition="top">
            </button>
          </div>

        </div> <!-- Fin de la ligne .san-input-row -->
        
        <!-- Boucle pour afficher les messages d'erreur sous les champs -->
        <div *ngFor="let san of sans.controls" class="error-message-container">
          <small class="p-error" *ngIf="san.get('value')?.invalid && (san.get('value')?.dirty || san.get('value')?.touched)">
            <!-- Votre logique d'erreur personnalisée est conservée ici -->
            <span *ngIf="san.get('value')?.errors?.pattern">
              {{ requestDetailSection.errors.sanFormat[san.get('type')?.value] | translate }}
            </span>
            <span *ngIf="san.get('value')?.errors?.required">
              Le champ est requis.
            </span>
          </small>
        </div>

      </div> <!-- Fin du formArrayName="sans" -->

      <!-- Bouton pour ajouter une nouvelle ligne de SAN -->
      <div class="add-san-button-container">
        <button 
          pButton 
          type="button" 
          label="Ajouter un SAN" 
          icon="pi pi-plus" 
          class="p-button-secondary p-button-outlined" 
          (click)="addSan()">
        </button>
      </div>

    </div> <!-- Fin du conteneur .ui-g-9 -->
  </div> <!-- Fin de la ligne .row -->
</fieldset>

<!-- =================================================================== -->
<!-- FIN DE LA SECTION SANs                                              -->
<!-- =================================================================== -->
////////////////////////css///////////
// Conteneur principal pour chaque ligne de SAN
.san-input-row {
  display: flex;          // Active le mode Flexbox pour aligner les éléments horizontalement.
  align-items: center;      // Aligne verticalement l'input, le dropdown et le bouton au centre.
  width: 100%;
  
  // La propriété magique : définit un espacement de 8px entre chaque élément.
  // Vous pouvez ajuster cette valeur (ex: 0.25rem, 4px, 1rem...).
  gap: 0.5rem; 

  // Ajoute un espace en dessous de chaque ligne pour ne pas qu'elles soient collées.
  margin-bottom: 1rem;
}

// Colonne contenant l'input de texte
.san-value-column {
  // Fait en sorte que cette colonne prenne tout l'espace disponible.
  // C'est ce qui la rend "responsive".
  flex: 1; 
}

// Colonne contenant le dropdown
.san-type-column {
  // Empêche le dropdown de se rétrécir si l'espace manque.
  flex-shrink: 0; 
}

// Colonne contenant le bouton
.san-action-column {
  // Empêche le bouton de se rétrécir.
  flex-shrink: 0;
}
//////////////// san enum //////////////////
code
TypeScript
// =======================================================================
// 1. L'ENUM : NOTRE UNIQUE SOURCE DE VÉRITÉ
// Pour ajouter un nouveau type, vous l'ajoutez UNIQUEMENT ICI.
// =======================================================================
export enum SanType {
  DNSNAME        = 'DNSNAME',
  RFC822NAME     = 'RFC822NAME',
  IPADDRESS      = 'IPADDRESS',
  URI            = 'URI',
  OTHERNAME_GUID = 'OTHERNAME_GUID',
  OTHERNAME_UPN  = 'OTHERNAME_UPN',
}

// =======================================================================
// 2. STYLE MAPPER : TYPÉ ET SÉCURISÉ PAR L'ENUM
// `Record<SanType, string>` force une entrée pour chaque SanType.
// Si vous ajoutez un type à l'enum sans l'ajouter ici, TypeScript criera !
// =======================================================================
export const styleMapper: Record<SanType, string> = {
  [SanType.DNSNAME]:        'badge-dnsname',
  [SanType.RFC822NAME]:     'badge-rfc822name',
  [SanType.IPADDRESS]:      'badge-ipaddress',
  [SanType.URI]:            'badge-uri',
  [SanType.OTHERNAME_GUID]: 'badge-othername', // Style regroupé
  [SanType.OTHERNAME_UPN]:  'badge-othername',  // Style regroupé
};

// =======================================================================
// 3. OPTIONS POUR LE DROPDOWN : GÉNÉRÉES AUTOMATIQUEMENT
// Ce code ne change jamais. Il s'adapte à l'enum.
// =======================================================================
export const SAN_TYPE_OPTIONS = Object.values(SanType).map(type => ({
  label: type,
  value: type,
  styleClass: styleMapper[type] // Récupère le style correspondant
}));

// =======================================================================
// 4. REGEX PATTERNS : ÉGALEMENT SÉCURISÉ PAR L'ENUM
// =======================================================================
export const SANS_REGEX_PATTERNS: Record<SanType, RegExp> = {
  [SanType.DNSNAME]:        /^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]*[A-Za-z0-9])$/,
  [SanType.RFC822NAME]:     /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
  [SanType.IPADDRESS]:      /^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/,
  [SanType.URI]:            /^(https?|ftp|file):\/\/.+$/,
  [SanType.OTHERNAME_GUID]: /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/,
  [SanType.OTHERNAME_UPN]:  /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
};
(Note : J'ai mis des regex plus simples pour l'exemple, conservez les vôtres qui sont plus complètes).
Étape 2 : Mettre à jour votre Composant
Maintenant, dans votre composant, vous devez importer et utiliser ces nouvelles variables.
Fichier : request-detail-section.component.ts
code
TypeScript
import { Component, OnInit } from '@angular/core';
// Importez l'enum et les options du dropdown
import { SanType, SAN_TYPE_OPTIONS } from 'src/app/shared/ts.utils';

@Component({
  // ...
})
export class RequestDetailSectionComponent implements OnInit {
  
  // Expose les options au template HTML. Le nom de la variable est plus clair.
  public sanTypes = SAN_TYPE_OPTIONS;

  // Vous pouvez aussi exposer l'enum elle-même si besoin dans votre logique
  public SanTypeEnum = SanType;

  // ... reste de votre composant

  // Exemple d'utilisation dans votre code TypeScript :
  someMethod(type: SanType) {
    if (type === SanType.IPADDRESS) { // <-- BEAUCOUP plus sûr que 'IPADDRESS'
      console.log('This is an IP Address!');
    }
  }
}
////////////////////////////
En haut de votre fichier, changez les anciens imports pour les nouveaux.
code
TypeScript
// Fichier: request-detail-section.component.ts

// ... autres imports
// AVANT (ce que vous aviez probablement):
// import { SAN_TYPE, SANS_REGEX_PATTERNS } from 'src/app/shared/ts.utils';

// APRÈS (ce que vous devez avoir maintenant):
import { SanType, SAN_TYPE_OPTIONS, SANS_REGEX_PATTERNS } from 'src/app/shared/ts.utils';
// ...
2. Mettre à jour les propriétés de la classe
Trouvez la ligne où vous déclarez public sanTypes et mettez-la à jour.
code
TypeScript
// Fichier: request-detail-section.component.ts

export class RequestDetailSectionComponent implements OnInit, OnDestroy {
  // ... autres propriétés

  // AVANT:
  // public sanTypes: any; // ou SAN_TYPE

  // APRÈS:
  public sanTypes = SAN_TYPE_OPTIONS;

  // ...
}
3. Mettre à jour la méthode createSanGroup()
C'est un changement crucial. Nous allons remplacer les chaînes de caractères par les valeurs de l'enum pour la sécurité.
code
TypeScript
// Fichier: request-detail-section.component.ts

createSanGroup(): FormGroup {
  const sanGroup = this.fb.group({
    
    // AVANT:
    // type: ['DNSNAME', Validators.required],
    // value: ['', [Validators.required, Validators.pattern(SANS_REGEX_PATTERNS.DNSNAME)]],
    
    // APRÈS:
    type: [SanType.DNSNAME, Validators.required],
    value: ['', [Validators.required, Validators.pattern(SANS_REGEX_PATTERNS[SanType.DNSNAME])]],

  });

  // La logique qui suit pour la mise à jour dynamique est PARFAITE.
  // Elle fonctionnera directement avec l'enum, car la valeur du formulaire (type)
  // sera une des valeurs de l'enum (ex: 'DNSNAME').
  sanGroup.get('type').valueChanges
    .pipe(takeUntil(this.onDestroy$))
    .subscribe((type: SanType) => { // Le type est maintenant SanType
      const valueControl = sanGroup.get('value');
      const regex = SANS_REGEX_PATTERNS[type]; // Ça fonctionne toujours !

      if (regex) {
        valueControl.setValidators([Validators.required, Validators.pattern(regex)]);
      } else {
        valueControl.setValidators(Validators.required);
      }
      valueControl.updateValueAndValidity();
    });

  return sanGroup;
}
Étape 3 : Vérifier le fichier HTML (request-detail-section.component.html)
Ici, il ne devrait y avoir aucun changement nécessaire. Votre code HTML est déjà prêt.
code
Html
<!-- Fichier: request-detail-section.component.html -->

<!-- ... -->
<p-dropdown 
  [options]="sanTypes" <!-- Cette variable existe toujours et contient les bonnes données -->
  formControlName="type" 
  optionLabel="label" 
  optionValue="value">
    <!-- ... -->
</p-dropdown>
<!-- ... -->
La variable sanTypes du composant pointe maintenant vers SAN_TYPE_OPTIONS. La structure de cet objet ({ label, value, styleClass }) est exactement la même qu'avant, donc le template HTML fonctionnera sans aucune modification.
Résumé des Actions
Remplacez le contenu de ts.utils.ts par la version finale avec l' enum.
Dans request-detail-section.component.ts :
Changez les import pour importer SanType, SAN_TYPE_OPTIONS, et SANS_REGEX_PATTERNS.
Modifiez la propriété public sanTypes = SAN_TYPE_OPTIONS;.
Dans createSanGroup(), remplacez 'DNSNAME' par SanType.DNSNAME et SANS_REGEX_PATTERNS.DNSNAME par SANS_REGEX_PATTERNS[SanType.DNSNAME].
Le fichier HTML n'a pas besoin de modification.
Vous avez maintenant une architecture beaucoup plus solide, plus sûre et plus facile à faire évoluer.
///////////////// back ////////////////////
import java.util.regex.Pattern;

/**
 * Représente les différents types de Subject Alternative Name (SAN).
 * Chaque type contient son propre Pattern de validation Regex pré-compilé pour la performance.
 */
public enum SanType {

    // Définition de chaque type de SAN avec sa regex associée.
    // La compilation du Pattern est faite une seule fois au chargement de la classe.
    DNSNAME(
        // Votre regex pour DNSNAME
        "(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,6}" 
    ),
    RFC822NAME(
        // Votre regex pour RFC822NAME (email)
        "[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?"
    ),
    IPADDRESS(
        // Votre regex pour IPv4. La concaténation est gérée directement ici.
        "^" + "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" + "$"
    ),
    URI(
        // Votre regex pour URI
        "^(https|ldaps?|ftp|file|tag|urn|data|tel)://.+"
    ),
    OTHERNAME_GUID(
        // Votre regex pour GUID
        "^[a-zA-Z0-9]{32}$"
    ),
    OTHERNAME_UPN(
        // Comme dans votre code original, UPN réutilise la même validation que RFC822NAME
        RFC822NAME.getRegexString()
    );
    
    private final String regexString;
    private final Pattern compiledPattern;

    /**
     * Constructeur de l'enum.
     * @param regex La chaîne de caractères de l'expression régulière à compiler.
     */
    SanType(String regex) {
        this.regexString = regex;
        this.compiledPattern = Pattern.compile(regex);
    }
    
    /**
     * @return Le Pattern Regex pré-compilé associé à ce type de SAN.
     */
    public Pattern getPattern() {
        return this.compiledPattern;
    }

    /**
     * @return La chaîne de caractères de la regex. Utile pour la réutilisation.
     */
    private String getRegexString() {
        return this.regexString;
    }
}
//////////////////////
public class RequestServiceImpl implements RequestService {

    public void processCertificateRequest(RequestDTO requestDto) {
        // ... autre logique de validation

        for (SanDTO sanDto : requestDto.getSans()) {
            try {
                // Convertit la chaîne de caractères (ex: "DNSNAME") de la DTO en notre enum.
                // Le .toUpperCase() rend la conversion robuste à la casse.
                SanType sanType = SanType.valueOf(sanDto.getType().toUpperCase());

                // Utilise notre nouvelle classe de validation. C'est propre et lisible !
                if (!SanValidator.isValid(sanType, sanDto.getValue())) {
                    throw new InvalidSanException("La valeur '" + sanDto.getValue() + "' n'est pas valide pour le type " + sanType);
                }

            } catch (IllegalArgumentException e) {
                // Cette exception est levée si le type de SAN envoyé par le frontend n'existe pas dans notre enum.
                throw new InvalidSanException("Le type de SAN '" + sanDto.getType() + "' est inconnu.");
            }
        }

        // ... continuer le traitement si tout est valide
    }
}
//////////
private void verifySanFormats(RequestDto requestDto) {
    // La vérification initiale pour voir si la liste est vide est une bonne pratique, on la garde.
    if (requestDto.getCertificate() == null || CollectionUtils.isEmpty(requestDto.getCertificate().getSans())) {
        return;
    }

    // On parcourt chaque SAN de la requête.
    for (San san : requestDto.getCertificate().getSans()) {
        if (!StringUtils.hasText(san.getType()) || !StringUtils.hasText(san.getSanValue())) {
            // Le type ou la valeur est manquant.
            throw new CertisRequestException("request.error.san.incomplete", HttpStatus.BAD_REQUEST);
        }

        SanType sanType;
        try {
            // On convertit la chaîne de caractères (ex: "DNSNAME") de la DTO en notre enum.
            // Le .toUpperCase() rend la conversion robuste à la casse (DNSName, dnsname, etc.).
            sanType = SanType.valueOf(san.getType().toUpperCase());

        } catch (IllegalArgumentException e) {
            // Gère le cas où le type envoyé par le client (ex: "DNSTYPE_INCONNU") n'existe pas dans notre enum.
            // C'est une erreur de format.
            Object[] args = { san.getSanValue(), san.getType() };
            throw new CertisRequestException("request.error.san.invalid.format", args, HttpStatus.BAD_REQUEST);
        }

        // On utilise notre nouvelle classe de validation. L'appel est simple et clair.
        boolean isValid = SanValidator.isValid(sanType, san.getSanValue());

        // Si la validation échoue, on lève l'exception comme avant.
        if (!isValid) {
            Object[] args = { san.getSanValue(), san.getType() };
            throw new CertisRequestException("request.error.san.invalid.format", args, HttpStatus.BAD_REQUEST);
        }
    }
}
///////////////////////// test///////////////////
{
  "usage": "INTERNAL",
  "requestType": null,
  "status": "Draft",
  "application": {
    "code": "AP19382",
    "applicationName": "AP19382 -- Certificat Orchestrated Plateforme"
  },
  "certificate": {
    "certificateName": "Traore.test.intra",
    "certificateType": "Serveur SSL",
    "platform": "WEBSPHERE7",
    "environment": "STAGING",
    "country": "FR",
    "creator": "h45884",
    "entity": "AAaa",
    "csrFile": {
      "name": "csrTest.csr",
      "content": "--- COLLEZ LE CONTENU DE VOTRE CSR VALIDE ICI (sans les -----BEGIN... et -----END...) ---"
    },
    "extendedValidation": "STANDARD",
    "hostname": null,
    "licence": false,
    "numberOfCPU": null,
    "operatingSystem": null,
    "organisationName": null,
    "password": null,
    "secondCN": null
  },
  "gsupport": [
    {
      "name": "BNPP_ITGP_PRODSEC_DEFENSE_INC_CERTIS"
    }
  ],
  "administrativeContacts": [
    {
      "cidd": "h45884",
      "email": "abdoulaye.l.traore@bnpparibas.com",
      "roles": "IT Responsible"
    }
  ],
  "technicalContacts": [
    {
      "cidd": "b90783",
      "email": "raphael.lamy@bnpparibas.com",
      "roles": "Business owner"
    }
  ],
  "sans": [
    {
      "type": "DNSNAME",
      "values": "Traore.test.intra"
    }
  ]
}
////////
public Request populate(Request request) {

    System.out.println("--- [DEBUG] --- DEBUT DE LA METHODE POPULATE ---");

    if (request != null && request.getCertificate() != null) {

        if (request.getCertificate().getCertificateType() != null) {
            
            // --- Log n°1 : On vérifie la configuration et ce que l'on cherche ---
            System.out.println("--- [DEBUG] --- URL BDD : " + env.getProperty("spring.datasource.url"));
            System.out.println("--- [DEBUG] --- RECHERCHE TYPE : '" + request.getCertificate().getCertificateType().getName() + "'");
            
            CertificateType foundType = certificateTypeDao.findByName(request.getCertificate().getCertificateType().getName());

            // --- Log n°2 : On vérifie le résultat de la recherche ---
            if (foundType == null) {
                System.err.println("--- [DEBUG] --- !!! ECHEC : Type non trouvé en BDD !!!"); // System.err écrit en rouge, c'est pratique pour les erreurs
            } else {
                System.out.println("--- [DEBUG] --- SUCCES : Type trouvé, ID = " + foundType.getId());
            }

            request.getCertificate().setCertificateType(foundType);
        } else {
            System.out.println("--- [DEBUG] --- WARNING : CertificateType est null dans la requête.");
        }

        // ... Le reste de votre code ...

    } else {
        System.out.println("--- [DEBUG] --- WARNING : La requête ou le certificat est null.");
    }
    
    System.out.println("--- [DEBUG] --- FIN DE LA METHODE POPULATE ---");
    return request;
}
/////////////////////////
{
    {
    "id": null,
    "requestType": "RENEW",
    "createdBy": "b90783",
    "usage": "INTERNAL",
    "environment": {
        "name": "STAGING"
    },
    "certificate": {
        "applicationCode": "A1800117",
        "applicationName": "Certificat Orchestrated Plateforme",
        "comment": null,
        "commonName": "testrafaignigration.net.intra",
        "criticality": "Moyenne",
        "hostname": null,
        "unknownCodeAp": false,
        "mutualAuthent": 1,
        "dns": true,
        "organisationName": null,
        "gnsCountry": {
            "isoCode": "FR"
        },
        "password": null,
        "certificateType": {
            "name": "Multi OU SSL"
        },
        "certificateSubType": null,
        "certisEntity": {
            "name": "BP2I - TestPlan"
        },
        "groupSupport": {
            "name": "BNPP_ITGP_PRODSEC_DEFENSE_INC_CERTIS"
        },
        "platform": {
            "name": "WEBSPHERE7"
        },
        "sans": [
             { "type": "URL", "value": "toto" },
             { "type": "URL", "value": "tata" }
        ],
        "secondCN": "myousuper"
    },
    "contacts": [
        {
            "emailNotification": "raphael.lamy@bnpparibas.com",
            "role": {
                "name": "Applicant",
                "type": "Generic"
            },
            "uid": null,
            "firstname": null,
            "lastname": null,
            "editable": true
        },
        {
            "emailNotification": "raphael.lamy@bnpparibas.com",
            "role": {
                "name": "IT Responsible",
                "type": "Admin"
            },
            "uid": "b90783",
            "firstname": "Raphael",
            "lastname": "LAMY",
            "editable": true
        },
        {
            "emailNotification": "ibrahim.tamboura@bnpparibas.com",
            "role": {
                "name": "Business Owner",
                "type": "Tech"
            },
            "uid": "a11899",
            "firstname": "Ibrahim",
            "lastname": "TAMBOURA",
            "editable": true
        }
    ],
    "csr": "--- COLLEZ ICI LA LONGUE CHAINE DE VOTRE CSR ---",
    "comment": "null",
    "nature": "APPLICATION",
    "extendedValidation": "STANDARD"
}
//////// elimination////////////////
{
    "id": null,
    "requestType": "NEW",
    "usage": "INTERNAL", // <-- AJOUTÉ
    "certificate": {
        "applicationName": "Certificat Orchestrated Plateforme"
    }
}
Envoyez. L'erreur sur usage devrait disparaître.
Ajoutez le champ contacts (avec un contenu minimal) :
code
JSON
{
    ... // les autres champs
    "contacts": [
        {
            "emailNotification": "test@test.com",
            "role": { "name": "Applicant", "type": "Generic" }
        }
    ]
}
Envoyez. L'erreur sur contacts devrait disparaître.
Ajoutez le certificateType dans le certificat :
code
JSON
{
    ...
    "certificate": {
        "applicationName": "Certificat Orchestrated Plateforme",
        "certificateType": { // <-- AJOUTÉ
            "name": "Serveur SSL/TLS"
        }
    }
    ...
}
/////
"contacts": [
        {
            "emailNotification": "test@test.com",
            "role": {
                "name": "Applicant", // Vous pouvez garder celui-ci si nécessaire
                "type": "Generic"
            }
        },
        {
            "emailNotification": "admin@test.com",
            "role": {
                "name": "IT Responsible", // Exemple de contact administratif
                "type": "Admin"
            }
        },
        {
            "emailNotification": "tech@test.com",
            "role": {
                "name": "Technical support", // Exemple de contact technique
                "type": "Tech"
            }
        }
    ]
	///////////////// composant personnaliser /////////////////////////////
	
code
TypeScript
// Fichier : src/app/shared/components/san-badges-list/san-badges-list.component.ts

import { Component, Input } from '@angular/core';

export interface San {
  sanType: string;
  sanValue: string;
}

@Component({
  selector: 'app-san-badges-list',
  templateUrl: './san-badges-list.component.html',
  styleUrls: ['./san-badges-list.component.scss']
})
export class SanBadgesListComponent {

  @Input() sanList: San[] = [];

  // ... (votre sanTypeToCssClass reste inchangé) ...
   private readonly sanTypeToCssClass = {
    'DNSNAME':        'badge-dnsname',
    'RFC822NAME':     'badge-rfc822name',
    'IPADDRESS':      'badge-ipaddress',
    'URI':            'badge-uri',
    'OTHERNAME_GUID': 'badge-othername-guid',
    'OTHERNAME_UPN':  'badge-othername-upn',
    'DEFAULT':        'badge-default' // On peut ajouter une classe par défaut
  };

  /**
   * Dictionnaire interne pour les expressions régulières associées à chaque type.
   */
  private readonly sanTypeRegex = {
    'DNSNAME':        /^[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9](?:\.[a-zA-Z]{2,})+$/,
    'RFC822NAME':     /^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$/,
    'IPADDRESS':      /^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/,
    'URI':            /^(https?|ftp):\/\/[^\s/$.?#].[^\s]*$/,
    'OTHERNAME_GUID': /^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$/, // Exemple plus strict pour un GUID
    'OTHERNAME_UPN':  /^[a-zA-Z0-9.-]+@[a-zA-Z0-9.-]+$/
  };

  /**
   * Méthode publique pour le template, qui vérifie si une valeur SAN est valide.
   * @param san L'objet SAN complet.
   * @returns `true` si la valeur est valide, `false` sinon.
   */
  public isSanValueValid(san: San): boolean {
    const regex = this.sanTypeRegex[san.sanType];
    
    // Si nous n'avons pas de regex pour ce type, on considère que c'est valide.
    if (!regex) {
      return true;
    }
    
    // On teste la valeur contre la regex.
    return regex.test(san.sanValue);
  }

  // ... (votre méthode getBadgeClass reste inchangée) ...
  public getBadgeClass(sanType: string): string {
    return this.sanTypeToCssClass[sanType] || this.sanTypeToCssClass['DEFAULT'];
  }
}
////////////////
code
Scss
// Fichier : src/app/shared/components/san-badges-list/san-badges-list.component.scss

// Définition des couleurs spécifiques aux SANs
$dnsname-color: #007bff;        // Bleu
$rfc822name-color: #28a745;     // Vert
$ipaddress-color: #ffc107;      // Jaune/Orange
$uri-color: #dc3545;            // Rouge
$othername-guid-color: #17a2b8; // Cyan
$othername-upn-color: #6c757d;  // Gris
$default-color: #343a40;        // Gris foncé

// Style pour chaque ligne de la liste
.san-item {
  display: flex;
  align-items: center;
  margin-bottom: 6px; // Espace entre chaque SAN

  &:last-child {
    margin-bottom: 0;
  }
}

// Style de base pour tous les badges
.san-badge {
  padding: 4px 10px;
  border-radius: 12px;
  color: white;
  font-size: 0.8em;
  font-weight: bold;
  margin-right: 12px;
  min-width: 120px;
  text-align: center;
  text-transform: uppercase;
}

// Style pour la valeur du SAN
.san-value {
  font-family: monospace; // Idéal pour afficher des URLs ou des IPs
}

// Application des couleurs spécifiques
.badge-dnsname { background-color: $dnsname-color; }
.badge-rfc822name { background-color: $rfc822name-color; }
.badge-ipaddress { background-color: $ipaddress-color; color: #212529; } // Texte foncé pour la lisibilité
.badge-uri { background-color: $uri-color; }
.badge-othername-guid { background-color: $othername-guid-color; }
.badge-othername-upn { background-color: $othername-upn-color; }
.badge-default { background-color: $default-color; }
///////////////
code
Html
<!-- Fichier : src/app/shared/components/san-badges-list/san-badges-list.component.html -->

<!-- Le composant ne s'affiche que s'il y a des SANs à montrer -->
<div *ngIf="sanList && sanList.length > 0">
  
  <!-- On boucle sur la liste de SANs reçue en @Input -->
  <div *ngFor="let san of sanList" class="san-item">
    
    <!-- Le badge, dont la classe est déterminée par la méthode du composant -->
    <span class="san-badge" [ngClass]="getBadgeClass(san.sanType)">
      {{ san.sanType }}
    </span>

    <!-- La valeur du SAN -->
    <span class="san-value">
      {{ san.sanValue }}
    </span>

  </div>
</div>
//////////////////
code
Html
<!-- Dans certificate-details.component.html -->

<div class="row nopad" *ngIf="certificateRequest?.certificate?.sans?.length > 0">
    <!-- Colonne pour le label "SANS" -->
    <div class="ui-g-3 nopad">
        <label class="pull-right">{{'requestDetailsSection.SANS' | translate}} :</label>
    </div>
    
    <!-- Colonne pour notre nouveau composant -->
    <div class="ui-g-9 nopad">
        <!-- 
            On appelle notre nouveau composant ici.
            On lui passe la liste des SANs via le data binding [sanList].
        -->
        <app-san-badges-list [sanList]="certificateRequest.certificate.sans"></app-san-badges-list>
    </div>
</div>
	
	//////////////////////////
	<!-- Fichier : src/app/shared/components/san-badges-list/san-badges-list.component.html -->

<div *ngIf="sanList && sanList.length > 0">
  <div *ngFor="let san of sanList" class="san-item">
    
    <!-- Badge (inchangé) -->
    <span class="san-badge" [ngClass]="getBadgeClass(san.sanType)">
      {{ san.sanType }}
    </span>

    <!-- Valeur du SAN (inchangé) -->
    <span class="san-value">
      {{ san.sanValue }}
    </span>

    <!-- ========================================================== -->
    <!--          NOUVELLE PARTIE : ICÔNE D'AVERTISSEMENT         -->
    <!-- ========================================================== -->
    
    <!-- 
      Cette icône ne s'affiche QUE si la méthode isSanValueValid(san) retourne `false`.
      L'icône `fa-exclamation-triangle` vient de Font Awesome (très courant dans les projets Angular).
    -->
    <span *ngIf="!isSanValueValid(san)" class="san-warning-icon">
      <i class="fa fa-exclamation-triangle" title="Cette valeur ne respecte pas le format attented"></i>
    </span>

  </div>
</div>
Étape 3 : Ajouter le style pour l'icône (san-badges-list.component.scss)
Enfin, nous ajoutons un peu de style pour que notre icône soit bien visible et positionnée.
code
Scss
// Fichier : src/app/shared/components/san-badges-list/san-badges-list.component.scss

// ... (tous vos styles existants pour .san-item, .san-badge, etc.) ...


// ==========================================================
//          NOUVEAU STYLE POUR L'ICÔNE D'AVERTISSEMENT
// ==========================================================

.san-warning-icon {
  margin-left: 10px; // Espace par rapport à la valeur
  color: #ffc107;      // Couleur orange/avertissement
  font-size: 1.2em;   // Un peu plus grande pour être visible
}

.san-warning-icon i[title] {
  cursor: help; // Le curseur change pour indiquer qu'on peut survoler pour avoir une info-bulle
}
///////////////////////////////////////////////////////////////////////////
// Fichier : src/app/shared/components/san-badges-list/san-badges-list.component.scss

// ... (tous vos styles existants pour .san-item, .san-badge, etc.) ...


// ==========================================================
//          NOUVEAU STYLE POUR L'ICÔNE D'AVERTISSEMENT
// ==========================================================

.san-warning-icon {
  margin-left: 10px; // Espace par rapport à la valeur
  color: #ffc107;      // Couleur orange/avertissement
  font-size: 1.2em;   // Un peu plus grande pour être visible
}

.san-warning-icon i[title] {
  cursor: help; // Le curseur change pour indiquer qu'on peut survoler pour avoir une info-bulle
}
//////////////////////////////////////////////
<div *ngIf="sanList && sanList.length > 0">
  <div *ngFor="let san of sanList" class="san-item">
    
    <!-- Badge (inchangé) -->
    <span class="san-badge" [ngClass]="getBadgeClass(san.sanType)">
      {{ san.sanType }}
    </span>

    <!-- Valeur du SAN (inchangé) -->
    <span class="san-value">
      {{ san.sanValue }}
    </span>

    <!-- ========================================================== -->
    <!--          NOUVELLE PARTIE : ICÔNE D'AVERTISSEMENT         -->
    <!-- ========================================================== -->
    
    <!-- 
      Cette icône ne s'affiche QUE si la méthode isSanValueValid(san) retourne `false`.
      L'icône `fa-exclamation-triangle` vient de Font Awesome (très courant dans les projets Angular).
    -->
    <span *ngIf="!isSanValueValid(san)" class="san-warning-icon">
      <i class="fa fa-exclamation-triangle" title="Cette valeur ne respecte pas le format attented"></i>
    </span>

  </div>
</div>
/////////////////////////////////////
// Fichier : src/app/shared/components/san-badges-list/san-badges-list.component.scss

// On importe le fichier de badges global pour pouvoir utiliser le placeholder %badge-base
@import 'src/assets/styles/badges'; // Adaptez le chemin vers votre fichier _badges.scss

// Style pour chaque ligne de la liste des SANs
.san-item {
  display: flex;
  align-items: center;
  margin-bottom: 6px;

  &:last-child {
    margin-bottom: 0;
  }
}

// Le style du badge est maintenant plus simple.
// On applique les styles de base définis dans votre fichier global.
.san-badge {
  @extend %badge-base; // On hérite des styles de base (comme `color: white;`)
  
  // On peut ajouter des styles spécifiques au composant si nécessaire
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 0.8em;
  font-weight: bold;
  margin-right: 12px;
  min-width: 120px;
  text-align: center;
  text-transform: uppercase;
}

// Style pour la valeur du SAN
.san-value {
  font-family: monospace;
}
//////////////////////////////////
code
Html
<!-- Fichier : src/app/shared/components/san-badges-list/san-badges-list.component.html -->

<div *ngIf="sanList && sanList.length > 0">
  <div *ngFor="let san of sanList" class="san-item">
    
    <!-- La classe de base `san-badge` est appliquée. -->
    <!-- [ngClass] ajoute la classe de couleur (ex: 'badge-dnsname') qui est définie dans votre fichier global. -->
    <span class="san-badge" [ngClass]="getBadgeClass(san.sanType)">
      {{ san.sanType }}
    </span>

    <span class="san-value">
      {{ san.sanValue }}
    </span>
  </div>
</div>
///////////////////////////
<!-- Dans certificate-details.component.html -->
<div class="ui-g-9 nopad">
  <app-san-badges-list [sanList]="certificateRequest.certificate.sans"></app-san-badges-list>
</div>
/////////////////////////////////////
@import 'src/assets/styles/variables'; 
@import 'src/assets/styles/badges';


/* ==========================================================
    2. STYLES DE MISE EN PAGE DU COMPOSANT
   ========================================================== */

// Conteneur pour chaque ligne de la liste des SANs (badge + valeur)
.san-item {
  display: flex;
  align-items: center;
  margin-bottom: 6px; // Espace vertical entre chaque SAN

  // On supprime la marge du dernier élément pour un alignement parfait
  &:last-child {
    margin-bottom: 0;
  }
}

// Style spécifique pour la valeur textuelle du SAN
.san-value {
  font-family: monospace; // Police idéale pour les URLs, IPs, etc.
  margin-left: 12px;     // Espace entre le badge et la valeur
}


/* ==========================================================
    3. DÉFINITION DES STYLES DE BASE ET DE COULEUR POUR LES BADGES
   ========================================================== */

// Classe de base pour tous les badges de ce composant
.san-badge {
  // On hérite des styles communs définis dans votre fichier _badges.scss
  @extend %badge-base; 

  // On peut ajouter ou surcharger des styles spécifiques ici si besoin
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 0.8em;
  font-weight: bold;
  min-width: 120px;
  text-align: center;
  text-transform: uppercase;
}

// Définition des couleurs de fond pour chaque type de SAN
// en utilisant les variables de votre fichier _variables.scss.
// Ces noms de classes correspondent à ce qui est retourné par le .ts

.badge-dnsname {
  background-color: $color-bullet-blue;
}

.badge-rfc822name {
  background-color: $color-bullet-green;
}

.badge-ipaddress {
  background-color: $color-bullet-orange;
  color: #212529; // Le texte est foncé pour une meilleure lisibilité sur fond orange
}

.badge-uri {
  background-color: $color-bullet-violet; // Vous pouvez aussi choisir $color-bullet-red
}

.badge-othername-guid {
  background-color: $color-bullet-gray;
}

.badge-othername-upn {
  background-color: $color-bullet-cyan;
}

.badge-default {
  background-color: $color-medium-grey; // Utilisation d'une autre variable pour le cas par défaut
}
//////////////////////////////////

@import 'src/assets/styles/variables'; 
@import 'src/assets/styles/badges'; // On importe ce fichier pour pouvoir utiliser %badge-base


/* ==========================================================
    2. STYLES DE MISE EN PAGE DU COMPOSANT
   ========================================================== */

.san-item {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}

.san-value {
  font-family: monospace;
  margin-left: 12px;
}


/* ==========================================================
    3. EXTENSION DES STYLES DE BADGES
   ========================================================== */

/*
 * Ici, nous ne créons pas un style de base.
 * Nous disons simplement que nos classes de couleur DOIVENT HÉRITER
 * de tous les styles définis dans %badge-base de votre fichier global.
 * C'est plus propre et évite la duplication de code.
 */

.badge-dnsname,
.badge-rfc822name,
.badge-ipaddress,
.badge-uri,
.badge-othername-guid,
.badge-othername-upn,
.badge-default {
  @extend %badge-base; // TOUS héritent des styles de base (padding, font-size, color: white, etc.)

  // On peut ajouter des styles communs à tous les badges de CE composant ici si nécessaire
  min-width: 120px;
  text-transform: uppercase;
}

/* 
 * Maintenant, on se contente d'appliquer la couleur de fond spécifique
 * à chaque classe, en utilisant les variables de votre fichier _variables.scss.
 */

.badge-dnsname        { background-color: $color-bullet-blue; }
.badge-rfc822name     { background-color: $color-bullet-green; }
.badge-ipaddress      { background-color: $color-bullet-orange; color: #212529; }
.badge-uri            { background-color: $color-bullet-violet; }
.badge-othername-guid { background-color: $color-bullet-gray; }
.badge-othername-upn  { background-color: $color-bullet-cyan; }
.badge-default        { background-color: $color-medium-grey; }