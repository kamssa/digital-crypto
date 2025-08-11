
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
////////
  String newCertificateComment = updateRequest.getCertificateComment();
        
        // On compare avec l'ancien commentaire pour voir s'il y a eu un changement,
        // en utilisant Objects.equals pour gérer les cas où l'un des deux est null.
        if (!Objects.equals(previousCertificate.getComment(), newCertificateComment)) {
            // Le commentaire a changé, on le met à jour sur l'objet Certificate.
            previousCertificate.setComment(newCertificateComment);
            
            // On ajoute une mention spécifique dans la trace de la Request pour cette modification
            traceModification += " Le commentaire du certificat a été mis à jour;";
        }
        
        // --- Fin du bloc ajouté ---

        this.commentService.processComment(previousRequest, null, connectedUser, traceModification);
        
        boolean infoValidateRequest = true; // Supposition basée sur le code
        if (infoValidateRequest) {
            return this.updateRequest(previousRequest);
        } else {
            return this.updateRequestWithoutValidation(previousRequest);
        }
    ////////////////////////////////
	 <div class="row row-style">
            <div class="ui-g-4">
                <label class="pull-right">
                    {{ 'certificateInformationSection.certificateComment' | translate }}:
                </label>
            </div>
            <div class="ui-g-8">
                <textarea pInputTextarea 
                          formControlName="certificateComment" 
                          [rows]="4" 
                          [autoResize]="true"
                          style="width: 100%;"
                          placeholder="Saisissez un commentaire pour ce certificat...">
                </textarea>
                <div *ngIf="form.get('certificateComment')?.invalid && form.get('certificateComment')?.touched" class="alert-error">
                    <small *ngIf="form.get('certificateComment').errors?.maxlength">
                        Le commentaire ne doit pas dépasser 4000 caractères.
                    </small>
                </div>
            </div>
        </div>
		/////////// code complet ts///////////////////////////
		
import { Component, OnInit } from '@angular/core';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';

// Importez tous vos services et modèles nécessaires
import { TranslateService } from '@ngx-translate/core';
import { DataFeedingService } from 'src/app/services/datafeeding.service';
import { RequestService } from 'src/app/services/request.service';
import { MessageService } from 'src/app/services/messages.service';
import { ApiService } from 'src/app/services/api.service';
import { CertificateRequest } from 'src/app/entity/models'; // Adaptez le chemin si besoin

@Component({
  selector: 'app-update-informations-form',
  templateUrl: './update-informations-form.component.html',
  styleUrls: ['./update-informations-form.component.scss']
})
export class UpdateInformationsFormComponent implements OnInit {

  // --- Propriétés existantes reconstituées ---
  form: FormGroup;
  hasChange: boolean = false;
  requestId: number;
  applicationInformations: any; // Objet pour stocker les données initiales

  countryList: SelectItem[];
  entityList: SelectItem[];
  environmentList: SelectItem[];
  auidAutoCompletionSuggestionList: string[] = [];
  appName: string;
  previousGroupSupport: string;
  gsList: Observable<SelectItem[]>;
  gsNotEditable: boolean = true;

  constructor(
    private translateService: TranslateService,
    private route: ActivatedRoute,
    private dataService: DataFeedingService,
    private requestService: RequestService,
    private messageService: MessageService,
    private _api: ApiService,
    private location: Location,
    private _router: Router
  ) {}

  ngOnInit(): void {
    // Initialisation des données et du formulaire
    this.initRequestInformations();
    this.createForm();
    this.initCountryList();
    this.initEntityList();
    this.initEnvironmentList();
    
    // Si l'AUID est déjà là, on charge les infos associées
    if (this.applicationCodeField.value) {
      this._api.lookRefi(this.applicationCodeField.value).subscribe(refi => {
        this.completeAllAuidRelatedFields(refi);
      });
    } else {
      this.clearAllAuidRelatedFields();
    }
    
    this.onGroupFormValueChange();
  }

  createForm(): void {
    this.form = new FormGroup({
      applicationCode: new FormControl(this.applicationInformations.code),
      applicationName: new FormControl(this.applicationInformations.name),
      unknownCodeAP: new FormControl(this.applicationInformations.unknownCodeAP),
      environment: new FormControl(this.applicationInformations.environment, Validators.required),
      entity: new FormControl(this.applicationInformations.entity, Validators.required),
      gsupport: new FormControl(this.applicationInformations.gsupport, Validators.required),
      country: new FormControl(this.applicationInformations.country, Validators.required),
      hostname: new FormControl(this.applicationInformations.hostname),

      // =======================================================
      //     ▼▼▼   AJOUTEZ LE CHAMP AU FORMULAIRE ICI   ▼▼▼
      // =======================================================
      certificateComment: new FormControl(
          this.applicationInformations.certificateComment || '', 
          [Validators.maxLength(4000)]
      )
    });
  }

  initRequestInformations(): void {
    this.route.data.subscribe(data => {
      const request: CertificateRequest = data.request;
      this.requestId = request.id;
      
      this.applicationInformations = {
        code: request.application.code,
        environment: request.certificate.environment,
        unknownCodeAP: request.certificate.unknownCodeAP,
        name: request.application.name,
        entity: request.application.entity,
        gsupport: request.application.gsupport,
        country: request.application.country,
        hostname: request.certificate.hostname,
        
        // =======================================================
        //     ▼▼▼   AJOUTEZ LE COMMENTAIRE À L'OBJET INITIAL   ▼▼▼
        // =======================================================
        certificateComment: request.certificate.comment 
      };
    });
  }

  // =======================================================
  //     ▼▼▼   REMPLACEZ VOTRE onSubmit PAR CELLE-CI   ▼▼▼
  // =======================================================
  onSubmit(): void {
    // 1. Construire le payload complet, qui correspond au DTO du backend
    const infoPayload = {
      code: this.applicationCodeField.value,
      name: this.applicationNameField.value,
      entity: this.entityField.value,
      gsupport: this.gssupportField.value,
      environment: this.environmentField.value,
      country: this.countryField.value,
      hostname: this.hostnameField.value,
      // On inclut le nouveau champ dans le même objet
      certificateComment: this.form.value.certificateComment 
    };

    // 2. Faire un seul appel API à l'endpoint /info existant
    this.requestService.updateApplicationInformations(this.requestId, infoPayload)
      .toPromise()
      .then(() => {
        // Succès de la mise à jour
        this.messageService.showMessage({
          severity: 'success',
          summary: this.translateService.instant('updateInformationsPage.success')
        });
        this.location.back(); // Retour à la page précédente
      })
      .catch((httpError) => {
        // Erreur
        this.messageService.showMessage({
          severity: 'error',
          summary: httpError.error.message // Affiche le message d'erreur du backend
        });
      });
  }
  
  // --- VOS AUTRES MÉTHODES RESTENT INCHANGÉES ---

  initCountryList(): void {
    this.dataService.getCountries().pipe(map(asSelectItems)).subscribe(res => {
      this.countryList = res;
    });
  }

  initEntityList(): void {
    this.dataService.getEntities().pipe(map(asSelectItems)).subscribe(res => {
      this.entityList = res;
    });
  }

  initEnvironmentList(): void {
    this.dataService.getEnvironments().subscribe(res => {
      this.environmentList = res;
    });
  }

  clearAllAuidRelatedFields() {
    this.applicationCodeField.patchValue(null);
    this.appName = '';
    this.gsList = of([]);
    this.gssupportField.patchValue('');
    this.gsNotEditable = true;
  }
  
  private completeAllAuidRelatedFields(refi: any) {
    this.applicationCodeField.patchValue(refi.codeAp);
    if (refi.name) {
      this.appName = refi.name;
    }
    if (refi.groupSupportDto) {
      this.gsList = of(refi.groupSupportDto).pipe(map(asSelectItems));
      this.gssupportField.patchValue(refi.groupSupportDto.name);
      this.gsNotEditable = true;
    } else {
      this.gsList = this.dataService.userGroupSupport().pipe(
        tap(groups => {
          if (this.previousGroupSupport) {
            groups.push(this.previousGroupSupport);
          }
          this.gsNotEditable = false;
        }),
        map(asSelectItems)
      );
    }
  }

  onGroupFormValueChange() {
    const initialValue = this.form.value;
    this.form.valueChanges.subscribe(value => {
      this.hasChange = Object.keys(this.form.value).some(key => this.form.value[key] !== initialValue[key]);
    });
  }

  onCancel(): void {
    this.location.back();
  }
  
  selectCodeApp(event) {
    this._api.lookRefi(event).subscribe(refi => {
      this.completeAllAuidRelatedFields(refi);
    });
  }

  searchCodeApp(searchTerm) {
    let { query } = searchTerm;
    this._api.autocompleteRefi(query.toUpperCase()).pipe().subscribe(data => {
      this.auidAutoCompletionSuggestionList = data.map(refi => refi.codeAp);
    }, () => this.auidAutoCompletionSuggestionList = []);
  }

  // Getters pour les champs du formulaire (existants)
  get gssupportField() { return this.form.get('gsupport'); }
  get applicationCodeField() { return this.form.get('applicationCode'); }
  get entityField() { return this.form.get('entity'); }
  get environmentField() { return this.form.get('environment'); }
  get hostnameField() { return this.form.get('hostname'); }
  get countryField() { return this.form.get('country'); }
  get applicationNameField() { return this.form.get('applicationName'); }
}
///////
<div *ngIf="certificateCommentField?.invalid && certificateCommentField?.touched" class="alert-error">
    <small *ngIf="certificateCommentField.errors?.maxlength">
        {{ 'projectSection.errorMessages.certificateComment.maxlength' | translate }}
    </small>
</div>