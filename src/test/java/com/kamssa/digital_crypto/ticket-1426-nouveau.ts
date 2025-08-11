
public class UpdateRequestInfoDto {

    // ... (tous vos champs existants : applicationCode, hostname, etc.)
    private String applicationCode;
    private String hostname;
    // ...

    // =======================================================
    //     ▼▼▼   AJOUTEZ CE NOUVEAU CHAMP   ▼▼▼
    // =======================================================
    @Size(max = 4000)
    private String certificateComment;

    // ... (tous vos getters et setters existants)
    
    // =======================================================
    //     ▼▼▼   AJOUTEZ LES GETTERS/SETTERS ASSOCIÉS   ▼▼▼
    // =======================================================
    public String getCertificateComment() {
        return certificateComment;
    }

    public void setCertificateComment(String certificateComment) {
        this.certificateComment = certificateComment;
    }
}
//////////service //////////
@Service
public class RequestServiceImpl implements RequestService {

    // ...

    @Override
    @Transactional
    public RequestDto updateRequestInfo(UpdateRequestInfoDto updateRequestInfoDto, Long requestId, String username, ActionRequestType action) {
        
        // --- Début du code existant de la méthode ---
        RequestDto previousRequest = this.findRequestByIdAndAccessibility(requestId, username, action);
        Certificate previousCertificate = previousRequest.getCertificate();
        String traceModification = "Request information has been modified:";

        // ... (toute votre logique existante pour mettre à jour les infos : hostname, country, etc.)
        // Cette logique compare les anciennes et les nouvelles valeurs et construit `traceModification`.
        // ...
        
        // =========================================================================
        //     ▼▼▼   AJOUTEZ CE BLOC DE CODE À L'INTÉRIEUR DE LA MÉTHODE   ▼▼▼
        // =========================================================================
        
        // On récupère le nouveau commentaire depuis le DTO
        String newCertificateComment = updateRequestInfoDto.getCertificateComment();
        
        // On compare avec l'ancien commentaire pour voir s'il y a eu un changement
        if (!Objects.equals(previousCertificate.getComment(), newCertificateComment)) {
            // Le commentaire a changé, on le met à jour
            previousCertificate.setComment(newCertificateComment);
            
            // On pourrait ajouter une trace spécifique si on le voulait, mais
            // la trace principale "Request information has been modified" est déjà là.
            // Si vous voulez une trace dédiée :
            // traceModification += " Certificate comment has been updated.";
        }
        
        // --- Fin du bloc ajouté ---
        
        
        // --- Le reste de votre code existant continue ici ---
        this.commentService.processComment(previousRequest, null, username, traceModification);

        if (infoValidateRequest) {
            return this.updateRequest(previousRequest);
        } else {
            return this.updateRequestWithoutValidation(previousRequest);
        }
    }
    
    // ...
}
////////////////////////
@Component({ /* ... */ })
export class UpdateInformationsFormComponent implements OnInit {
  
  // ... (le code pour ajouter "certificateComment" au FormGroup reste le même)
  
  // ===================================================================
  //     ▼▼▼   REMPLACEZ VOTRE onSubmit PAR CETTE VERSION SIMPLIFIÉE   ▼▼▼
  // ===================================================================
  onSubmit(): void {
    // 1. Construire le payload complet, y compris le nouveau commentaire
    const payload: UpdateRequestInfoDto = {
      code: this.form.value.applicationCode,
      name: this.form.value.applicationName,
      entity: this.form.value.entity,
      gsupport: this.form.value.gsupport,
      environment: this.form.value.environment,
      country: this.form.value.country,
      hostname: this.form.value.hostname,
      certificateComment: this.form.value.certificateComment // On inclut le commentaire ici !
    };

    // 2. Faire un seul appel API à l'endpoint existant
    this.requestService.updateApplicationInformations(this.requestId, payload)
      .toPromise()
      .then(() => {
        // Succès
        this.messageService.showMessage({
          severity: 'success',
          summary: this.translateService.instant('updateInformationsPage.success')
        });
        this.location.back();
      })
      .catch((httpError) => {
        // Erreur
        this.messageService.showMessage({
          severity: 'error',
          summary: httpError.error.message
        });
      });
  }
}
/////// updete formulaire////////////////
<!-- CHEMIN : src/app/content/form/update-informations/update-informations-form.component.html -->


<!-- ... (votre code existant pour les champs : applicationCode, hostname, etc.) ... -->


<!-- ======================================================= -->
<!--     ▼▼▼   AJOUTEZ CE BLOC DE CODE DANS VOTRE FORMULAIRE   ▼▼▼ -->
<!-- ======================================================= -->
<!-- Champ pour le commentaire du certificat -->
<div class="row row-style">
    <div class="ui-g-4">
        <label class="pull-right">
            <!-- Vous devrez ajouter cette clé de traduction à vos fichiers JSON (ex: fr.json) -->
            {{ 'certificateInformationSection.certificateComment' | translate }}:
        </label>
    </div>
    <div class="ui-g-8">
        <!-- Champ de texte pour le commentaire -->
        <textarea pInputTextarea 
                  formControlName="certificateComment" 
                  [rows]="4" 
                  [autoResize]="true"
                  style="width: 100%;"
                  placeholder="Saisissez un commentaire pour ce certificat...">
        </textarea>
        
        <!-- Bloc d'erreur (optionnel mais recommandé) -->
        <!-- Il ne s'affichera que si l'utilisateur dépasse la longueur maximale -->
        <div *ngIf="form.get('certificateComment')?.invalid && form.get('certificateComment')?.touched" class="alert-error">
            <small *ngIf="form.get('certificateComment').errors?.maxlength">
                Le commentaire ne doit pas dépasser 4000 caractères.
            </small>
        </div>
    </div>
</div>

<!-- Ici se termine votre <form> -->
</form> 

<!-- La section des boutons reste inchangée -->
<div class="pull-right">
    <p-button styleClass="addButton"
              label="{{ 'form.buttons.save' | translate }}"
              [disabled]="!form.valid || !hasChange"
              (onClick)="onSubmit()">
    </p-button>
    <p-button styleClass="addButton"
              label="{{ 'form.buttons.cancel' | translate }}"
              (onClick)="onCancel()">
    </p-button>
</div>