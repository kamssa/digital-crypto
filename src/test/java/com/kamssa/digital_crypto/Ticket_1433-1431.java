CertificateDto certificateDto = requestDto.getCertificate();
    Certificate certificateEntity = request.getCertificate();
    if (certificateDto != null && certificateEntity != null && !CollectionUtils.isEmpty(certificateDto.getSans())) {
        certificateEntity.getSans().clear();
        for (SanDto sanDto : certificateDto.getSans()) {
            San sanEntity = new San();
            sanEntity.setType(sanDto.getType());
            sanEntity.setSanValue(sanDto.getSanValue());
            sanEntity.setUrl(sanDto.getUrl());
            sanEntity.setCertificate(certificateEntity);
            certificateEntity.getSans().add(sanEntity);
        }
    }
	//////////////////////////
	Fichier 3 : SanServiceImpl.java
Action : Moderniser les méthodes pour qu'elles travaillent avec List<SanDto>.
1. Modification de validateSansOnRefweb :
Generated java
public List<String> validateSansOnRefweb(RequestDto requestDto) throws Exception {
    // ... logique initiale inchangée ...
    List<SanDto> sanList = requestDto.getCertificate().getSans();
    if (CollectionUtils.isEmpty(sanList)) { return Collections.emptyList(); }

    for (SanDto sanDto : sanList) {
        String valueToValidate = sanDto.getSanValue(); // Utiliser getSanValue()
        if (this.evaluateCnAndCertTypeForCheckRefWebSan(requestDto, valueToValidate)) {
            if (!this.checkSanUrlOnRefweb(valueToValidate)) {
                sansInvalid.add(valueToValidate);
            }
        }
    }
    return sansInvalid;
}
///////////////////
@Override
public List<String> validateSansOnRefweb(RequestDto requestDto) throws Exception {
    ArrayList<String> sansInvalid = new ArrayList<>();

    String userGatewayName = requestDto.getCreatedBy();
    if (userGatewayName != null && !usersGatewayBypassRefWeb.isEmpty() && usersGatewayBypassRefWeb.stream().anyMatch(str -> str.equalsIgnoreCase(userGatewayName))) {
        return Collections.emptyList();
    }

    // On récupère la liste de SanDto
    List<SanDto> sanDtoList = requestDto.getCertificate().getSans();

    boolean hasNoSans = CollectionUtils.isEmpty(sanDtoList);
    boolean isWhitelistedPlatform = (requestDto.getCertificate().getPlatform() != null && requestDto.getCertificate().getPlatform().getName() != null && /* ... votre logique de plateforme ici ... */);

    if (hasNoSans || isWhitelistedPlatform) {
        return Collections.emptyList();
    }

    // On boucle directement sur la liste d'objets SanDto
    for (SanDto sanDto : sanDtoList) {
        
        // On extrait la valeur textuelle à valider depuis l'objet SanDto
        String valueToValidate = sanDto.getSanValue();

        // Le reste de la logique utilise cette valeur et reste inchangé
        if (this.evaluateCnAndCertTypeForCheckRefWebSan(requestDto, valueToValidate)) {
            if (!this.checkSanUrlOnRefweb(valueToValidate)) {
                sansInvalid.add(valueToValidate);
            }
        }
    }
    
    return sansInvalid;
}
////////////////////////////////////////////////
private RequestDto evaluateSan3W(RequestDto requestDto) {
    List<SanDto> sanDtoList = requestDto.getCertificate().getSans();
    if (sanDtoList == null) {
        sanDtoList = new ArrayList<>();
        requestDto.getCertificate().setSans(sanDtoList);
    }
    // ... logique pour créer `finalDomainWWW` ...
    boolean alreadyExists = sanDtoList.stream()
            .anyMatch(sanDto -> finalDomainWWW.equalsIgnoreCase(sanDto.getSanValue()));

    if (!alreadyExists) {
        SanDto sanDtoWWW = new SanDto();
        sanDtoWWW.setType(SanTypeEnum.DNSNAME);
        sanDtoWWW.setSanValue(finalDomainWWW);
        sanDtoWWW.setUrl(finalDomainWWW);
        sanDtoList.add(0, sanDtoWWW);
    }
    return requestDto;
}
///////////////
private RequestDto evaluateSan3W(RequestDto requestDto) {
    
    // CHANGEMENT 1 : On travaille avec le bon type d'objet : List<SanDto>
    List<SanDto> sanDtoList = requestDto.getCertificate().getSans();

    // SÉCURITÉ : On s'assure que la liste existe pour éviter les erreurs plus tard.
    if (sanDtoList == null) {
        sanDtoList = new ArrayList<>();
        requestDto.getCertificate().setSans(sanDtoList);
    }
    
    // La logique pour déterminer la valeur "www" reste la même.
    String cn = requestDto.getCertificate().getCommonName();
    String domainWWW = cn.startsWith("www.") ? cn.replaceFirst("www.", "") : "www." + cn;
    final String finalDomainWWW = domainWWW; 

    // CHANGEMENT 2 : On vérifie l'existence en se basant sur la propriété `sanValue` du DTO.
    boolean alreadyExists = sanDtoList.stream()
            .anyMatch(sanDto -> finalDomainWWW.equalsIgnoreCase(sanDto.getSanValue()));

    // Si le SAN "www" n'existe pas dans la liste...
    if (!alreadyExists) {
        // CHANGEMENT 3 : On crée un NOUVEAU DTO (`new SanDto()`), pas une entité.
        SanDto sanDtoWWW = new SanDto();

        // CHANGEMENT 4 : On remplit TOUTES les propriétés nécessaires du DTO.
        // On définit le type, car on sait que c'est un nom de domaine.
        sanDtoWWW.setType(SanTypeEnum.DNSNAME);
        
        // On définit la valeur principale.
        sanDtoWWW.setSanValue(finalDomainWWW);
        
        // On respecte la règle que `url` et `sanValue` sont identiques.
        sanDtoWWW.setUrl(finalDomainWWW);

        // On ajoute ce DTO complet et bien formé à la liste.
        sanDtoList.add(0, sanDtoWWW);
    }
    
    return requestDto;
}
///////////////////////////////////////////////// front end///////////////////////
Voir une liste de champs pour les Noms Alternatifs du Sujet (SANs).
Pour chaque SAN, saisir une valeur (ex: www.site.com).
Pour chaque SAN, choisir son type via des badges colorés (DNSNAME, Email, IP, URI).
Ajouter de nouvelles lignes de SAN ou en supprimer.
Soumettre ces données dans un format structuré.
Fichier 1 : src/app/shared/utils/style-mapper.ts (Nouveau Fichier)
Rôle : Centraliser la correspondance entre les types de SAN et les classes CSS des badges. C'est votre "source de vérité" pour les styles, ce qui rendra l'application plus facile à maintenir.
Action : Créer ce fichier et y ajouter le code suivant.
Generated typescript
// Ce fichier est votre source de vérité pour les styles.
export const StyleMapper = {
  // Types de SAN
  'DNSNAME':    'badge-info',
  'RFC822NAME': 'badge-success',
  'IPADDRESS':  'badge-warning',
  'URI':        'badge-danger',

  // Vous pourrez ajouter d'autres statuts de votre application ici pour tout harmoniser.
};
Use code with caution.
TypeScript
Fichier 2 : src/styles/_badges.scss (Nouveau Fichier)
Rôle : Définir le style visuel de chaque badge.
Action : Créer ce fichier, y ajouter le code, et l'importer dans votre fichier principal src/styles.scss (avec @import 'styles/badges';).
Generated scss
// Ce fichier définit l'apparence des badges.
// Vous pouvez ajuster les couleurs pour qu'elles correspondent à votre charte graphique.
.badge-info { background-color: #17a2b8 !important; color: white !important; }
.badge-success { background-color: #28a745 !important; color: white !important; }
.badge-warning { background-color: #ffc107 !important; color: #212529 !important; /* Texte sombre sur fond jaune */ }
.badge-danger { background-color: #dc3545 !important; color: white !important; }
Use code with caution.
Scss
Fichier 3 : src/app/.../certificate-detail-section.component.ts
Rôle : C'est le "cerveau" de cette section du formulaire. Il gère la structure des données et les actions de l'utilisateur (ajout/suppression).
Action : Mettre à jour ce composant pour inclure la logique du FormArray des SANs.
Generated typescript
// --- Imports nécessaires au début du fichier ---
import { Component, OnInit, Input } from '@angular/core';
import { FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { StyleMapper } from 'src/app/shared/utils/style-mapper'; // Import du mapper de style

@Component({
  selector: 'app-certificate-detail-section',
  // ...
})
export class CertificateDetailSectionComponent implements OnInit {
  @Input() certificateInformationSection: FormGroup; // Le FormGroup parent
  @Input() certificateRequest: any; // Pour le mode édition

  // Utilise le mapper pour définir les options des badges
  sanTypes = [
    { label: 'DNSNAME', value: 'DNSNAME',    styleClass: StyleMapper['DNSNAME'] },
    { label: 'Email',   value: 'RFC822NAME', styleClass: StyleMapper['RFC822NAME'] },
    { label: 'IP',      value: 'IPADDRESS',  styleClass: StyleMapper['IPADDRESS'] },
    { label: 'URI',     value: 'URI',        styleClass: StyleMapper['URI'] }
  ];

  constructor(private fb: FormBuilder) {}

  ngOnInit(): void {
    // S'assure que le FormArray 'sans' existe
    if (!this.certificateInformationSection.get('sans')) {
      this.certificateInformationSection.addControl('sans', this.fb.array([ this.createSanGroup() ]));
    }
    // Ajoutez ici la logique pour charger les SANs existants si vous êtes en mode édition.
  }

  // Getter pour un accès facile depuis le template HTML
  get sans(): FormArray {
    return this.certificateInformationSection.get('sans') as FormArray;
  }

  // Crée un groupe de formulaire pour un SAN (valeur + type)
  // **C'est ici que 'DNSNAME' est défini comme type par défaut.**
  createSanGroup(value = '', type = 'DNSNAME'): FormGroup {
    return this.fb.group({
      value: [value, Validators.required],
      type: [type, Validators.required]
    });
  }

  // Ajoute un nouveau SAN au formulaire
  addSan(): void {
    this.sans.push(this.createSanGroup());
  }

  // Supprime un SAN du formulaire
  removeSan(index: number): void {
    // Empêche la suppression du dernier champ
    if (this.sans.length > 1) {
      this.sans.removeAt(index);
    }
  }
}
Use code with caution.
TypeScript
Fichier 4 : src/app/.../certificate-detail-section.component.html
Rôle : C'est l'interface visuelle que l'utilisateur voit.
Action : Insérer ce bloc de code dans votre formulaire, à l'endroit où vous voulez que les champs SAN apparaissent.
Generated html
<!-- BLOC HTML COMPLET POUR LA GESTION DES SANS -->
<div class="row row-style">
  <div class="ui-g-4">
    <label class="pull-right">Noms Alternatifs du Sujet (SANs)
      <span class="mandatory-field pull-right">*</span>
    </label>
  </div>
  <div class="ui-g-8">
    <!-- Le conteneur lié au FormArray 'sans' -->
    <div formArrayName="sans">
      <!-- Boucle pour afficher une ligne pour chaque SAN -->
      <div *ngFor="let sanGroup of sans.controls; let i = index" [formGroupName]="i" class="ui-g-12 ui-g-nopad" style="margin-bottom: 15px;">
        <div class="ui-g ui-fluid p-align-center">
          <!-- Champ de saisie pour la valeur -->
          <div class="ui-g-7">
            <input type="text" pInputText formControlName="value" placeholder="ex: www.site.com ou admin@site.com">
          </div>
          <!-- Sélection du type avec les badges -->
          <div class="ui-g-4">
            <p-selectButton [options]="sanTypes" formControlName="type" optionValue="value">
              <ng-template let-item><p-tag [value]="item.label" [styleClass]="item.styleClass"></p-tag></ng-template>
            </p-selectButton>
          </div>
          <!-- Bouton de suppression -->
          <div class="ui-g-1" style="text-align: right;">
            <button pButton type="button" icon="pi pi-trash" class="p-button-danger p-button-text" (click)="removeSan(i)" [disabled]="sans.controls.length <= 1"></button>
          </div>
        </div>
      </div>
    </div>
    <!-- Bouton pour ajouter une ligne -->
    <div class="ui-g-12 ui-g-nopad" style="margin-top: 10px;">
      <button pButton type="button" label="Ajouter un SAN" icon="pi pi-plus" class="p-button-secondary" (click)="addSan()"></button>
    </div>
  </div>
</div>
Use code with caution.
Html
Fichier 5 : src/app/form.component.ts (Le composant Parent)
Rôle : Transformer le payload final juste avant de l'envoyer au backend pour qu'il corresponde parfaitement à ce que l'API Java attend.
Action : Modifier votre méthode de soumission (onSubmit, addRequest, etc.).
Generated typescript
// Dans la méthode qui envoie les données au backend
public onSubmit(): void {
  // Créez une copie profonde pour ne pas altérer le formulaire
  const payload = JSON.parse(JSON.stringify(this.form.value));

  // Accédez à la section des SANs
  const sansFromForm = payload.certificateDetails?.sans; // Utilisez le nom de votre form group

  if (sansFromForm && Array.isArray(sansFromForm)) {
    // **C'est ici qu'on s'assure que `url` et `sanValue` sont synchronisés.**
    payload.certificateDetails.sans = sansFromForm.map(san => {
      return {
        type: san.type,
        sanValue: san.value,
        url: san.value // url et sanValue reçoivent la même valeur
      };
    });
  }

  // Maintenant, le `payload` est prêt à être envoyé au service
  // this.requestService.addRequest(payload).subscribe(...);
}
///////////////////////////////////////////////////////////////////   nuveau modif //////////////////////////
 <div class="row row-style" style="margin-top: 1.5rem;" [hidden]="!(constraint?.fields['SANS'])">
    <br><br>
    <app-panel-message class="sanPanel" type="DANGER" [center]="true" [shrink]="false" message="requestDetailSection.sanWarningPanelMsg"></app-panel-message>
</div>

<!-- =================================================================== -->
<!-- ### DÉBUT DU BLOC MODIFIÉ ET INTÉGRÉ POUR LA GESTION DES SANS ###   -->
<!-- REMPLACEZ L'ANCIEN BLOC PAR CELUI-CI -->
<!-- =================================================================== -->
<div class="row row-style" [hidden]="!(constraint?.fields['SANS'])">
  <div class="ui-g-4">
    <i class="fa fa-info-circle tooltip pull-right" [pTooltip]="'Tooltips.SANS' | translate" tooltipPosition="top"></i>
    <label class="pull-right">{{ 'requestDetailSection.SANS' | translate }}</label>
  </div>
  <div class="ui-g-8">
    <!-- Le conteneur lié au FormArray 'sans' -->
    <div formArrayName="sans">
      <!-- Boucle pour afficher une ligne pour chaque SAN -->
      <div *ngFor="let sanGroup of sans.controls; let i = index" [formGroupName]="i" class="ui-g-12 ui-g-nopad" style="margin-bottom: 15px;">
        <div class="ui-g ui-fluid p-align-center">
          <div class="ui-g-7">
            <input type="text" pInputText formControlName="value" placeholder="ex: www.site.com ou admin@site.com">
          </div>
          <div class="ui-g-4">
            <p-selectButton [options]="sanTypes" formControlName="type" optionValue="value">
              <ng-template let-item><p-tag [value]="item.label" [styleClass]="item.styleClass"></p-tag></ng-template>
            </p-selectButton>
          </div>
          <div class="ui-g-1" style="text-align: right;">
            <button pButton type="button" icon="pi pi-trash" class="p-button-danger p-button-text" (click)="removeSan(i)" [disabled]="sans.controls.length <= 1"></button>
          </div>
        </div>
      </div>
    </div>
    <div class="ui-g-12 ui-g-nopad" style="margin-top: 10px;">
      <button pButton type="button" label="{{ 'form.buttons.addSan' | translate }}" icon="pi pi-plus" class="p-button-secondary" (click)="addSan()"></button>
    </div>
  </div>
</div>
//////// badges.scss //////////////////////

.badge-info {
  background-color: $info-color !important;
  color: white !important;
}
.badge-success {
  background-color: $success-color !important;
  color: white !important;
}
.badge-warning {
  background-color: $warning-color !important;
  color: #212529 !important; // Texte sombre pour les fonds clairs
}
.badge-danger {
  background-color: $danger-color !important;
  color: white !important;
}
//// variable //////////////
// AJOUTEZ CES VARIABLES SI ELLES N'EXISTENT PAS
$info-color:    #17a2b8;
$success-color: #28a745;
$warning-color: #ffc107;
$danger-color:  #dc3545;
//////////////////////////// 
<!-- =================================================================== -->
<!-- ### DÉBUT DU NOUVEAU BLOC DE CODE POUR LES SANS ###                 -->
<!-- Collez ce code à la place de l'ancien -->
<!-- =================================================================== -->
<div class="row row-style" [hidden]="!(constraint?.fields['SANS'])">
  <div class="ui-g-4">
    <i class="fa fa-info-circle tooltip pull-right" [pTooltip]="'Tooltips.SANS' | translate" tooltipPosition="top"></i>
    <label class="pull-right">{{ 'requestDetailSection.SANS' | translate }}
      <span class="mandatory-field pull-right" *ngIf="constraint?.fields['SANS']?.required">*</span>
    </label>
  </div>
  <div class="ui-g-8">
    <!-- Le conteneur qui est lié au FormArray 'sans' dans le .ts -->
    <div formArrayName="sans">
      
      <!-- Boucle *ngFor pour afficher une ligne pour chaque SAN -->
      <div *ngFor="let sanGroup of sans.controls; let i = index" [formGroupName]="i" class="ui-g-12 ui-g-nopad" style="margin-bottom: 15px;">
        
        <div class="ui-g ui-fluid p-align-center">
          <!-- Champ de saisie pour la valeur du SAN (domaine, email, etc.) -->
          <div class="ui-g-7">
            <input type="text" pInputText formControlName="value" placeholder="ex: www.site.com ou admin@site.com">
          </div>

          <!-- Menu de sélection du type avec les badges colorés -->
          <div class="ui-g-4">
            <p-selectButton 
                [options]="sanTypes" 
                formControlName="type" 
                optionValue="value">
              <ng-template let-item>
                <p-tag [value]="item.label" [styleClass]="item.styleClass"></p-tag>
              </ng-template>
            </p-selectButton>
          </div>
          
          <!-- Bouton pour supprimer la ligne SAN -->
          <div class="ui-g-1" style="text-align: right;">
            <button pButton type="button" icon="pi pi-trash"
                    class="p-button-danger p-button-text"
                    (click)="removeSan(i)"
                    [disabled]="sans.controls.length <= 1"
                    pTooltip="Supprimer ce SAN" tooltipPosition="top">
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Bouton pour ajouter une nouvelle ligne de SAN -->
    <div class="ui-g-12 ui-g-nopad" style="margin-top: 10px;">
       <button pButton type="button" label="{{ 'form.buttons.addSan' | translate }}"
               icon="pi pi-plus"
               class="p-button-secondary"
               (click)="addSan()">
       </button>
    </div>
  </div>
</div>
<!-- =================================================================== -->
<!-- ### FIN DU NOUVEAU BLOC DE CODE ###                                -->
<!-- =================================================================== -->

///////////////////////////////////
<!-- =================================================================== -->
<!-- ### CODE COMPLET POUR LA SECTION DES SANS AVEC LISTE DÉROULANTE ### -->
<!-- =================================================================== -->
<div class="row row-style" [hidden]="!(constraint?.fields['SANS'])">
  <div class="ui-g-4">
    <label class="pull-right">{{ 'requestDetailSection.SANS' | translate }}
      <span class="mandatory-field pull-right">*</span>
    </label>
  </div>
  <div class="ui-g-8">
    <div formArrayName="sans">
      <div *ngFor="let sanGroup of sans.controls; let i = index" [formGroupName]="i" class="ui-g-12 ui-g-nopad" style="margin-bottom: 15px;">
        <div class="ui-g ui-fluid p-align-center">
          
          <!-- Champ de saisie pour la valeur du SAN -->
          <div class="ui-g-7">
            <input type="text" pInputText formControlName="value" placeholder="ex: www.site.com">
          </div>

          <!-- ======================================================= -->
          <!-- ### C'EST ICI QUE L'UTILISATEUR CHOISIT LE TYPE ### -->
          <!-- ======================================================= -->
          <div class="ui-g-4">
            <!-- C'est une liste déroulante standard de PrimeNG -->
            <p-dropdown 
                [options]="sanTypes" 
                formControlName="type"
                optionLabel="label"
                optionValue="value">
                
                <!-- Template pour l'élément sélectionné (quand la liste est fermée) -->
                <ng-template let-item pTemplate="selectedItem">
                    <p-tag [value]="item.label" [styleClass]="item.styleClass" style="width: 100%;"></p-tag>
                </ng-template>
                
                <!-- Template pour chaque option dans la liste ouverte -->
                <ng-template let-item pTemplate="item">
                    <p-tag [value]="item.label" [styleClass]="item.styleClass" style="width: 100%;"></p-tag>
                </ng-template>
            </p-dropdown>
          </div>
          <!-- ======================================================= -->

          <!-- Bouton de suppression -->
          <div class="ui-g-1" style="text-align: right;">
            <button pButton type="button" icon="pi pi-trash" class="p-button-danger p-button-text" (click)="removeSan(i)" [disabled]="sans.controls.length <= 1"></button>
          </div>

        </div>
      </div>
    </div>
    
    <!-- Bouton pour ajouter une nouvelle ligne -->
    <div class="ui-g-12 ui-g-nopad" style="margin-top: 10px;">
       <button pButton type="button" label="{{ 'form.buttons.addSan' | translate }}"
               icon="pi pi-plus" class="p-button-secondary" (click)="addSan()"></button>
    </div>
  </div>
</div>
////////////////////////// onSubmit /////////////////////////////////
 // ===================================================================
  onSubmit(): void {
    // 1. Marquer tous les champs comme "touchés" pour afficher les messages d'erreur s'il y en a.
    this.form.markAllAsTouched();
    
    // 2. Vérifier si le formulaire est valide. Si ce n'est pas le cas, on arrête tout.
    if (this.form.invalid) {
      console.error('Le formulaire est invalide. Soumission annulée.');
      return; // Sort de la fonction
    }

    // 3. Activer l'état de "chargement" pour l'interface (ex: désactiver le bouton, montrer un spinner).
    this.requestChangeInProgress = true;
    this.errorMessage = null; // Réinitialiser les anciens messages d'erreur

    // 4. Créer une copie profonde du payload pour ne pas altérer l'objet du formulaire.
    const payload = JSON.parse(JSON.stringify(this.form.value));

    // 5. La transformation cruciale de la liste des SANs.
    const sansFromForm = payload.certificateDetails?.sans; // Utilisez le bon nom de votre form group

    if (sansFromForm && Array.isArray(sansFromForm)) {
      payload.certificateDetails.sans = sansFromForm.map(san => {
        // Formate chaque objet SAN pour correspondre à ce que le backend attend.
        return {
          type: san.type,
          sanValue: san.value,
          url: san.value // `url` et `sanValue` sont synchronisés
        };
      });
    }

    // 6. Appeler le service pour envoyer le payload final au backend.
    console.log('Payload final envoyé au backend :', payload); // Très utile pour le débogage !
    
    this.requestService.addRequest(payload).subscribe({
      // 7a. Gestion du cas de SUCCÈS
      next: (response) => {
        console.log('Requête créée avec succès !', response);
        this.requestChangeInProgress = false;
        
        // Rediriger l'utilisateur vers une page de confirmation, par exemple.
        // const newRequestId = response.id;
        // this.router.navigate(['/confirmation', newRequestId]);
      },
      // 7b. Gestion du cas d'ERREUR
      error: (err) => {
        console.error('Une erreur est survenue lors de la création de la requête :', err);
        this.errorMessage = 'Une erreur est survenue. Veuillez réessayer.'; // Message pour l'utilisateur
        this.requestChangeInProgress = false; // Très important de désactiver le spinner même en cas d'erreur
      }
    });
  }
}
//////////onSubmit ////////////////////
onSubmit(): void {
    this.form.markAllAsTouched();

    if (this.form.invalid) {
      console.error('Formulaire invalide. Soumission annulée.');
      return;
    }

    this.requestChangeInProgress = true;
    this.errorMessage = null;

    const formValue = this.form.value;

    const finalPayload = {
      ...this.certificateRequest,
      ...formValue.requestDetails,
      ...formValue.project,
      certificate: {
        ...this.certificateRequest?.certificate,
        ...formValue.certificateDetails
      }
    };

    if (finalPayload.certificate?.sans && Array.isArray(finalPayload.certificate.sans)) {
      finalPayload.certificate.sans = finalPayload.certificate.sans.map(san => ({
        type: san.type,
        sanValue: san.value,
        url: san.value
      }));
    }

    console.log('Payload final prêt à être envoyé :', finalPayload);

    let request$: Observable<any>;
    if (finalPayload.id) {
      request$ = this.requestService.editRequest(finalPayload);
    } else {
      request$ = this.requestService.addRequest(finalPayload);
    }

    request$.pipe(
      switchMap((res: any) => {
        // Votre logique existante pour les fichiers
        return of(res);
      })
    ).subscribe({
      next: (response) => {
        console.log('Opération réussie !', response);
        this.requestChangeInProgress = false;
        // Gérer la redirection ou le message de succès...
      },
      error: (err) => {
        console.error('Erreur lors de la soumission :', err);
        this.errorMessage = 'Une erreur est survenue.';
        this.requestChangeInProgress = false;
      }
    });
}
//////////////////////////////////////////
onSubmit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      console.error('Formulaire invalide. Soumission annulée.');
      return;
    }

    this.requestChangeInProgress = true;
    this.errorMessage = null;

    const formValue = this.form.value;

    const finalPayload = {
      ...this.certificateRequest,
      ...formValue.requestDetails,
      ...formValue.project,
      certificate: {
        ...this.certificateRequest?.certificate,
        ...formValue.certificateDetails,
      },
    };

    if (finalPayload.certificate?.sans && Array.isArray(finalPayload.certificate.sans)) {
      finalPayload.certificate.sans = finalPayload.certificate.sans.map(san => ({
        type: san.type,
        sanValue: san.value,
        url: san.value,
      }));
    }

    console.log('Payload final prêt à être envoyé :', finalPayload);

    let request$: Observable<any>;
    if (finalPayload.id) {
      request$ = this.requestService.editRequest(finalPayload);
    } else {
      request$ = this.requestService.addRequest(finalPayload);
    }

    request$.pipe(
        switchMap((res: any) => {
          // Votre logique existante pour les fichiers
          return of(res);
        })
      )
      .subscribe({
        next: (response) => {
          console.log('Opération réussie !', response);
          this.requestChangeInProgress = false;
          // Gérer la redirection ou le message de succès...
        },
        error: (err) => {
          console.error('Erreur lors de la soumission :', err);
          this.errorMessage = 'Une erreur est survenue.';
          this.requestChangeInProgress = false;
        },
      });
  }
  //////// froamt ///////////////////
   createSanGroup(): FormGroup {
    // On crée le groupe de formulaire pour une ligne SAN
    const sanGroup = this.fb.group({
      type: ['DNSNAME', Validators.required], // Type par défaut
      value: ['', [Validators.required, Validators.pattern(SAN_REGEX_PATTERNS.DNSNAME)]] // Validateur par défaut
    });

    // On écoute les changements sur le champ 'type' (le dropdown)
    sanGroup.get('type').valueChanges
      .pipe(takeUntil(this.onDestroy$)) // Important pour éviter les fuites de mémoire
      .subscribe(type => {
        const valueControl = sanGroup.get('value');
        const regex = SAN_REGEX_PATTERNS[type]; // On récupère la regex depuis notre fichier de config

        if (regex) {
          // On met à jour les validateurs du champ 'value'
          valueControl.setValidators([Validators.required, Validators.pattern(regex)]);
        } else {
          valueControl.setValidators(Validators.required);
        }
        // On force la re-validation du champ
        valueControl.updateValueAndValidity();
      });

    return sanGroup;
  }

  // MODIFIER votre méthode addSan existante pour qu'elle soit plus simple
  addSan(): void {
    const sans = this.requestDetailSectionForm.get('sans') as FormArray;
    sans.push(this.createSanGroup());
  }
  ////
  export class RequestDetailSectionComponent implements OnInit, OnDestroy {
  // ...
  private onDestroy$ = new Subject<void>(); // <-- S'IL N'EXISTE PAS, AJOUTEZ-LE

  // ...

  ngOnDestroy() {
    this.onDestroy$.next();
    this.onDestroy$.complete();
    // ... potentiellement d'autres logiques de désinscription déjà présentes
  }
}
///
<!-- Fichier request-detail-section.component.html -->

<!-- ... -->
<div formArrayName="sans">
  <!-- Je me base sur la structure vue dans vos screenshots -->
  <div *ngFor="let sanGroup of sans.controls; let i = index" [formGroupName]="i">
    <!-- ... vos divs pour l'input et le dropdown ... -->
    <div class="div-qui-contient-l-input">
        <input type="text" pInputText formControlName="value" placeholder="ex: www.site.com">
    </div>
    <div class="div-qui-contient-le-dropdown">
        <p-dropdown [options]="sanTypes" formControlName="type"></p-dropdown>
    </div>

    <!-- 
      AJOUTER CE BLOC POUR L'ERREUR 
      RAISON : Ce bloc affiche un message à l'utilisateur uniquement si
      le champ 'value' est invalide ET que l'erreur est de type 'pattern'.
      Ceci est la connexion directe entre la validation par regex dans votre
      code TypeScript et le feedback visuel pour l'utilisateur.
    -->
    <div *ngIf="sanGroup.get('value').invalid && (sanGroup.get('value').dirty || sanGroup.get('value').touched)" 
         class="p-col-12 p-text-right">
      <small class="p-error" *ngIf="sanGroup.get('value').errors?.pattern">
        Format invalide pour le type de SAN sélectionné.
      </small>
    </div>

  </div>
</div>
<!-- ... -->
////regex///
xport const SAN_REGEX_PATTERNS = {
  DNSNAME: /^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,6}$|^(\*\.)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,6}$/,
  RFC822NAME: /^[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?\.)+[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?$/,
  IPADDRESS: /^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^((([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,6})|:)|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])))$/,
  OTHERNAME_GUID: /^.*#[a-zA-Z0-9]{32}$/,
  OTHERNAME_UPN: /^[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?\.)+[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?$/,
  URI: /^(https?|ldaps?|ftp|file|tag|urn|data|tel):\/\/[a-zA-Z0-9\.\+&#\/%?=~_\-!:,|'*]+$/i
  ///////
  
  
};
/////////////////////////////////////////////////////////////
<!-- =================== DÉBUT DE LA SECTION À REMPLACER =================== -->

<!-- Titre et explication pour la section SANs (déjà dans votre code) -->
<div class="row row-style" [hidden]="!constraint?.fields['SANs']">
    <div class="ui-g-4">
        <i class="fa fa-info-circle tooltip pull-right" [pTooltip]="'tooltips.SANs' | translate" tooltipPosition="top"></i>
        <label class="pull-right">{{ 'requestDetailSection.SANs' | translate }}</label>
        <span class="mandatory-field pull-right">*</span>
    </div>

    <!-- Conteneur principal pour le FormArray et le bouton d'ajout -->
    <div class="ui-g-8">

        <!-- Conteneur du FormArray qui contient la boucle -->
        <div formArrayName="sans">
            <!-- Boucle qui crée une ligne pour chaque SAN -->
            <div *ngFor="let sanGroup of sans.controls; let i = index" [formGroupName]="i" class="ui-g-12 ui-g-nopad" style="margin-bottom: 15px;">
                <div class="ui-g ui-fluid p-align-center">
                    
                    <!-- Champ de saisie -->
                    <div class="ui-g-7">
                        <input type="text" pInputText formControlName="value" placeholder="ex: www.site.com">
                    </div>

                    <!-- Dropdown pour le type -->
                    <div class="ui-g-4">
                        <p-dropdown [options]="sanTypes" 
                                    formControlName="type" 
                                    optionLabel="label" 
                                    optionValue="value" 
                                    placeholder="Choisir un type">
                            <ng-template let-item pTemplate="selectedItem">
                                <p-tag [value]="item.label" [styleClass]="item.styleClass" style="width: 100%;"></p-tag>
                            </ng-template>
                            <ng-template let-item pTemplate="item">
                                <p-tag [value]="item.label" [styleClass]="item.styleClass" style="width: 100%;"></p-tag>
                            </ng-template>
                        </p-dropdown>
                    </div>
                    
                    <!-- Bouton de suppression -->
                    <div class="ui-g-1" style="text-align: right;">
                        <button pButton type="button" icon="pi pi-trash" class="p-button-danger p-button-text" (click)="removeSan(i)"></button>
                    </div>

                    <!-- NOUVEAU BLOC D'ERREUR (À L'INTÉRIEUR de la boucle) -->
                    <div class="ui-g-12" *ngIf="sanGroup.get('value')?.invalid && (sanGroup.get('value')?.dirty || sanGroup.get('value')?.touched)">
                        <small class="p-error" *ngIf="sanGroup.get('value')?.errors?.pattern">
                            {{ 'requestDetailSection.errors.sanFormat' | translate }}
                        </small>
                    </div>

                </div>
            </div>
        </div>

        <!-- LE BOUTON addSan() (EN DEHORS de la boucle) -->
        <div class="ui-g-12 ui-g-nopad" style="margin-top: 10px;">
            <button pButton type="button" 
                    label="{{ 'requestDetailSection.addSan' | translate }}" 
                    icon="pi pi-plus-circle" 
                    class="p-button-secondary" 
                    (click)="addSan()">
            </button>
        </div>

    </div>
</div>
//// i18n////////////////////
{
  "requestDetailSection": {
    "errors": {
      "sanFormat": {
        "DNSNAME": "Format de nom de domaine invalide. Exemple : www.domaine.com",
        "IPADDRESS": "Format d'adresse IP invalide. Exemple : 192.168.1.1",
        "RFC822NAME": "Format d'adresse e-mail invalide. Exemple : utilisateur@domaine.com",
        "URI": "Format d'URI invalide. Exemple : https://www.domaine.com"
      }
    }
  }
}
Use code with caution.
Json
en.json
Generated json
{
  "requestDetailSection": {
    "errors": {
      "sanFormat": {
        "DNSNAME": "Invalid domain name format. Example: www.domain.com",
        "IPADDRESS": "Invalid IP address format. Example: 192.168.1.1",
        "RFC822NAME": "Invalid email address format. Example: user@domain.com",
        "URI": "Invalid URI format. Example: https://www.domain.com"
      }
    }
  }
}
///////////////////////// back ////////////////////////////
import com.bnpparibas.certis.api.util.SanValidationPatterns; // Vous devrez créer ce fichier
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class SanServiceImpl implements SanService {

    // ...

    @Override
    public void validateSansPerRequest(RequestDto requestDto) {
        if (this.skipValidationIfDataMissing(requestDto)) {
            return;
        }

        // ... code existant ...
        if (requestDto.getUsage().equalsIgnoreCase(INT_USAGE)) { /* ... */ }
        if (requestDto.getUsage().equalsIgnoreCase(EXT_USAGE)) { /* ... */ }

        // ↓↓↓ LIGNE À AJOUTER ↓↓↓
        this.verifySanFormats(requestDto);
    }
    
    // ↓↓↓ NOUVELLE MÉTHODE COMPLÈTE À AJOUTER ↓↓↓
    private void verifySanFormats(RequestDto requestDto) {
        if (requestDto.getCertificate() == null || CollectionUtils.isEmpty(requestDto.getCertificate().getSans())) {
            return;
        }

        for (San san : requestDto.getCertificate().getSans()) {
            if (san.getType() == null || !StringUtils.hasText(san.getSanValue())) {
                throw new CertisRequestException("request.error.san.incomplete", HttpStatus.BAD_REQUEST);
            }

            boolean isValid = false;
            switch (san.getType()) {
                case DNSNAME:
                    isValid = SanValidationPatterns.DNSNAME.matcher(san.getSanValue()).matches();
                    break;
                case IPADDRESS:
                    isValid = SanValidationPatterns.IPADDRESS.matcher(san.getSanValue()).matches();
                    break;
                case RFC822NAME:
                    isValid = SanValidationPatterns.RFC822NAME.matcher(san.getSanValue()).matches();
                    break;
                case URI:
                    isValid = SanValidationPatterns.URI.matcher(san.getSanValue()).matches();
                    break;
                default:
                    isValid = true;
                    break;
            }

            if (!isValid) {
                Object[] args = { san.getSanValue(), san.getType().name() };
                throw new CertisRequestException("request.error.san.invalid.format", HttpStatus.BAD_REQUEST, args);
            }
        }
    }
    
    // ...
}
/////////////////////////////// util///////////////
package com.bnpparibas.certis.api.util;

import java.util.regex.Pattern;

public final class SanValidationPatterns {
    public static final Pattern DNSNAME = Pattern.compile("^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,6}$|^(\\*\\.)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,6}$");
    public static final Pattern RFC822NAME = Pattern.compile("^[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?$");
    public static final Pattern OTHERNAME_GUID = Pattern.compile("^.*#[a-zA-Z0-9]{32}$");
    public static final Pattern OTHERNAME_UPN = RFC822NAME;
    public static final Pattern URI = Pattern.compile("^(https?|ldaps?|ftp|file|tag|urn|data|tel)://[a-zA-Z0-9\\.\\+&#/%?=~_\\-!:,|'*]+$", Pattern.CASE_INSENSITIVE);
    private static final String IPV4_REGEX = "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
    private static final String IPV6_REGEX = "((([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,6})|:)|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])))";
    public static final Pattern IPADDRESS = Pattern.compile("^(" + IPV4_REGEX + "|" + IPV6_REGEX + ")$");

    private SanValidationPatterns() {}
}
Use code with caution.

request.error.san.invalid.format=La valeur ''{0}'' n''est pas un format valide pour un SAN de type {1}.
request.error.san.invalid.format=The value ''{0}'' is not a valid format for a SAN of type {1}.
request.error.san.incomplete=The SAN is incomplete. Both type and value are required.
request.error.san.incomplete=Le SAN est incomplet. Le type et la valeur sont obligatoires.
request.error.san.invalid.format=La valeur ''{0}'' n\u0027est pas un format valide pour un SAN de type {1}.
request.error.san.incomplete=Le SAN est incomplet. Le type et la valeur sont obligatoires.
////////// 1430/////////////////
<!-- Votre structure principale que nous conservons -->
<div class="row nopad" [hidden]="!certificateRequest?.certificate?.sans?.length">
    <div class="ui-g-3 nopad">
        <label class="pull-right">{{'requestDetailsSection.SANS' | translate}} :</label>
    </div>
    <div class="ui-g-9 nopad">
        <!-- La boucle *ngFor est conservée, mais on accède à l'objet 'san' -->
        <div *ngFor="let san of certificateRequest.certificate.sans" class="row nopad san-item-row">
            
            <!-- Cette colonne vide est conservée pour l'alignement -->
            <div class="ui-g-3 nopad">
                <label class="pull-right nopad"></label>
            </div>

            <!-- ========================================================== -->
            <!--             C'EST ICI QUE LA MODIFICATION OPÈRE            -->
            <!-- ========================================================== -->
            <div class="ui-g-9 nopad">
                <!-- On remplace l'ancien <label> par ce bloc -->
                <span class="badge" [ngClass]="sanStyleMapper[san.sanType]">
                    {{ san.sanType }}
                </span>
                <span class="san-value">
                    {{ san.sanValue }}
                </span>
            </div>

        </div>
    </div>
</div>
/////////////////////////////////us  1431 ///////////////////////////////
Absolument. C'est une excellente idée de tout récapituler. Avec tous les fichiers que vous avez fournis, je peux maintenant vous donner un guide précis, fichier par fichier, avec les codes exacts à modifier et des explications claires pour chaque étape.
L'Objectif Final (tel que nous l'avons défini)
Nous allons fusionner deux listes de SANs :
Celle saisie par l'utilisateur dans l'interface Angular.
Celle contenue dans le fichier CSR qu'il a téléversé.
Le résultat sera une liste unique, sans doublons, qui sera ensuite utilisée par toute votre logique de validation et de sauvegarde existante.
Récapitulatif des Fichiers à Modifier (3 Fichiers au total)
Voici les 3 seuls fichiers que vous devez modifier ou créer.
Fichier 1 : Le DTO SanDto.java
C'est la modification la plus importante car elle permet la suppression automatique des doublons. Nous allons apprendre à Java comment comparer deux objets SanDto.
Chemin du fichier : .../certificate/request/model/dto/SanDto.java (adaptez si besoin)
Pourquoi cette modification ?
Pour qu'un Set (comme LinkedHashSet que nous utiliserons) puisse supprimer les doublons, il doit savoir quand deux objets sont "égaux". Par défaut, il ne compare que les adresses mémoire. Nous allons lui dire que deux SanDto sont égaux si leur sanValue (ex: "www.exemple.com") est identique, sans tenir compte de la majuscule ou minuscule.
Code à Ajouter :
Ajoutez les méthodes equals et hashCode à votre classe SanDto existante.
Generated java
Fichier 2 : Le Décodeur CertificateCsrDecoder.java (Nouveau Fichier)
Ce nouveau fichier va isoler la complexité de la lecture du CSR. C'est une bonne pratique de ne pas mélanger cette logique avec votre service principal.
Chemin du fichier à créer : .../certificate/request/service/impl/CertificateCsrDecoder.java
Pourquoi cette modification ?
Pour respecter le principe de responsabilité unique. Le rôle de RequestServiceImpl est d'orchestrer la création d'une requête, pas de parser des fichiers cryptographiques. En créant cette classe, votre code est plus propre, plus facile à tester et à maintenir.
Code à Créer :
Créez ce nouveau fichier Java.
Generated java
package com.bnpparibas.certis.certificate.request.service.impl;

// Assurez-vous que les chemins d'import sont corrects pour votre projet
import com.bnpparibas.certis.certificate.request.model.SanTypeEnum;
import com.bnpparibas.certis.certificate.request.model.dto.SanDto;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class CertificateCsrDecoder {

    public List<SanDto> extractSansFromCsr(String csrString) throws Exception {
        if (StringUtils.isEmpty(csrString)) {
            return new ArrayList<>();
        }

        List<SanDto> sans = new ArrayList<>();
        try (PEMParser pemParser = new PEMParser(new StringReader(csrString))) {
            Object parsedObj = pemParser.readObject();

            if (!(parsedObj instanceof PKCS10CertificationRequest)) {
                throw new IllegalArgumentException("Le contenu fourni n'est pas un CSR valide.");
            }

            PKCS10CertificationRequest csr = (PKCS10CertificationRequest) parsedObj;
            Extension sanExtension = csr.getRequestedExtensions().getExtension(Extension.subjectAlternativeName);

            if (sanExtension != null) {
                GeneralNames generalNames = GeneralNames.getInstance(sanExtension.getParsedValue());
                for (GeneralName name : generalNames.getNames()) {
                    SanDto sanDto = new SanDto();
                    String sanValue = name.getName().toString();
                    
                    switch (name.getTagNo()) {
                        case GeneralName.dNSName:
                            sanDto.setSanType(SanTypeEnum.DNSNAME);
                            sanDto.setSanValue(sanValue);
                            sans.add(sanDto);
                            break;
                        case GeneralName.iPAddress:
                            sanDto.setSanType(SanTypeEnum.IPADDRESS);
                            sanDto.setSanValue(sanValue);
                            sans.add(sanDto);
                            break;
                    }
                }
            }
        }
        return sans;
    }
}
Use code with caution.
Java
Fichier 3 : Le Service RequestServiceImpl.java
C'est ici que nous allons orchestrer la fusion. Nous modifions la méthode principale createRequest pour qu'elle utilise notre nouvelle logique avant de faire quoi que ce soit d'autre.
Chemin du fichier : .../certificate/request/service/impl/RequestServiceImpl.java
Pourquoi cette modification ?
C'est le point d'entrée de la création d'une requête. En modifiant le requestDto ici, au tout début, nous nous assurons que toute la chaîne de traitement qui suit (les validations dans SanServiceImpl, le mapping dtoToEntity, la sauvegarde) travaillera sur une liste de SANs propre et finale, sans avoir à modifier le reste du code.
Code à Modifier :
Ajoutez l'injection de dépendance en haut de la classe RequestServiceImpl :
Generated java
// ... sous les autres @Autowired
@Autowired
private CertificateCsrDecoder certificateCsrDecoder;
Use code with caution.
Java
Remplacez votre méthode createRequest existante par celle-ci :
Generated java
// N'oubliez pas les imports en haut du fichier
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
// ...

@Override
public RequestDto createRequest(RequestDto requestDto) {

    // ===================================================================
    // ===             DÉBUT DE LA LOGIQUE DE FUSION/DÉDUPLICATION     ===
    // ===================================================================

    // 1. On récupère les SANs saisis dans Angular (peut être vide).
    List<SanDto> sansFromAngular = requestDto.getCertificateDto() != null && requestDto.getCertificateDto().getSanDto() != null
                                   ? requestDto.getCertificateDto().getSanDto()
                                   : new ArrayList<>();

    // 2. On récupère les SANs du fichier CSR (peut être vide).
    List<SanDto> sansFromCsr = new ArrayList<>();
    final String csr = this.fileManagerService.extractCsr(requestDto, Boolean.TRUE);
    if (!StringUtils.isEmpty(csr)) {
        try {
            sansFromCsr = this.certificateCsrDecoder.extractSansFromCsr(csr);
        } catch (Exception e) {
            throw new CertisRequestException("error.request.csr.invalid_format", HttpStatus.BAD_REQUEST);
        }
    }

    // 3. On fusionne les deux listes et on supprime les doublons.
    // On utilise LinkedHashSet pour garder l'ordre et garantir l'unicité.
    Set<SanDto> finalUniqueSans = new LinkedHashSet<>();
    finalUniqueSans.addAll(sansFromAngular); // D'abord ceux du front
    finalUniqueSans.addAll(sansFromCsr);      // Ensuite ceux du CSR (les doublons sont ignorés grâce à equals/hashCode)

    // 4. On met à jour le DTO avec la liste finale et propre.
    if (requestDto.getCertificateDto() != null) {
        requestDto.getCertificateDto().setSanDto(new ArrayList<>(finalUniqueSans));
    }

    // ===================================================================
    // ===              FIN DE LA LOGIQUE DE FUSION/DÉDUPLICATION      ===
    // ===================================================================

    // LE RESTE DE VOTRE MÉTHODE EST INCHANGÉ
    if (requestDto.getComment() != null && requestDto.getComment().length() > 3998) {
        requestDto.setComment(requestDto.getComment().substring(0, 3998));
    }
    Request request = dtoToEntity(requestDto);
    if (!CollectionUtils.isEmpty(requestDto.getCertificate().getSans())) {
        for (San san : requestDto.getCertificate().getSans()) {
            san.setCertificate(requestDto.getCertificate());
        }
    }
    if (!CollectionUtils.isEmpty(requestDto.getContacts())) {
        for (Contact contact : requestDto.getContacts()) {
            contact.setRequest(request);
        }
    }
    RequestDto requestDtoResult = entityToDto(requestDao.save(request));
    return requestDtoResult;
}
package com.bnpparibas.certis.certificate.request.model.dto; // Adaptez le package à votre projet

import com.bnpparibas.certis.certificate.request.model.SanTypeEnum;
import java.util.Objects;

public class SanDto { // Le nom de votre classe DTO

    private SanTypeEnum sanType;
    private String sanValue;
    // Peut-être un champ 'url' ici aussi, mais nous allons ignorer 'url' pour la comparaison.
    // ... autres champs, constructeurs, getters, setters ...

    public String getSanValue() {
        return sanValue;
    }

    public void setSanValue(String sanValue) {
        this.sanValue = sanValue;
    }

    // ... autres getters/setters ...

    // ===================================================================
    // ===                    BLOC DE CODE À AJOUTER                   ===
    // ===================================================================

    /**
     * Définit quand deux objets SanDto sont considérés comme "égaux".
     * Ici, nous décidons qu'ils sont égaux si leur champ `sanValue` est identique,
     * en ignorant les majuscules/minuscules (ex: "test.com" est égal à "TEST.COM").
     */
    @Override
    public boolean equals(Object o) {
        // 1. Vérification de base : est-ce le même objet en mémoire ?
        if (this == o) return true;
        
        // 2. Vérification du type : est-ce un objet null ou d'une autre classe ?
        if (o == null || getClass() != o.getClass()) return false;
        
        // 3. Conversion de l'objet et comparaison du champ métier.
        SanDto sanDto = (SanDto) o;
        if (sanValue == null) {
            return sanDto.sanValue == null;
        }
        return sanValue.equalsIgnoreCase(sanDto.sanValue);
    }

    /**
     * Calcule un "code de hachage" pour l'objet.
     * RÈGLE OBLIGATOIRE : Si deux objets sont égaux selon .equals(), leur hashCode DOIT être le même.
     * C'est pourquoi nous basons le calcul sur la version minuscule de `sanValue`.
     */
    @Override
    public int hashCode() {
        // On utilise la version minuscule de sanValue pour être cohérent avec equalsIgnoreCase.
        return Objects.hash(sanValue != null ? sanValue.toLowerCase() : null);
    }

    // ===================================================================
    // ===                    FIN DU BLOC DE CODE                      ===
    // ===================================================================
}
///// version corrige de equal et hascode ///////////
@Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SanDto sanDto = (SanDto) o;

        // Étape 1 : On compare le type. Si différent, ce ne sont pas les mêmes objets.
        if (getSanType() != sanDto.getSanType()) return false;
        
        // Étape 2 : On compare la valeur de manière null-safe et insensible à la casse.
        // Si sanValue est null, on vérifie si l'autre l'est aussi.
        if (getSanValue() == null) {
            return sanDto.getSanValue() == null;
        }
        // Sinon, on fait la comparaison insensible à la casse.
        return getSanValue().equalsIgnoreCase(sanDto.getSanValue());
    }

    @Override
    public int hashCode() {
        // On doit utiliser une version normalisée de la valeur pour le hash,
        // ici, on la passe en minuscule pour être cohérent avec equalsIgnoreCase.
        String lowerCaseSanValue = (getSanValue() == null) ? null : getSanValue().toLowerCase();
        
        // On calcule le hash sur les mêmes champs que equals.
        return Objects.hash(getSanType(), lowerCaseSanValue);
    }

    // ===================================================================
    // ===                 FIN DE LA VERSION CORRIGÉE                  ===
    // ===================================================================

/////////////////////// nouvelle methode ////////////////////
public List<SanDto> extractSansWithTypesFromCsr(String csrPem) throws Exception {
    if (StringUtils.isEmpty(csrPem)) {
        return new ArrayList<>();
    }

    // On réutilise votre méthode existante pour parser le CSR, c'est parfait !
    PKCS10CertificationRequest csr = this.csrPemToPKCS10(csrPem);

    if (csr == null) {
        return new ArrayList<>();
    }
    
    List<SanDto> sanDtoList = new ArrayList<>();

    // On cherche l'extension "subjectAlternativeName" dans le CSR
    Extension sanExtension = csr.getRequestedExtensions().getExtension(Extension.subjectAlternativeName);

    if (sanExtension != null) {
        // On récupère la liste des noms
        GeneralNames generalNames = GeneralNames.getInstance(sanExtension.getParsedValue());
        
        for (GeneralName name : generalNames.getNames()) {
            SanDto sanDto = new SanDto();
            String sanValue = name.getName().toString();

            // On affecte le bon type, comme demandé dans le ticket
            switch (name.getTagNo()) {
                case GeneralName.dNSName:
                    sanDto.setSanType(SanTypeEnum.DNSNAME); // Assurez-vous que cet Enum correspond au vôtre
                    sanDto.setSanValue(sanValue);
                    sanDtoList.add(sanDto);
                    break;
                case GeneralName.iPAddress:
                    sanDto.setSanType(SanTypeEnum.IPADDRESS); // Assurez-vous que cet Enum correspond au vôtre
                    sanDto.setSanValue(sanValue);
                    sanDtoList.add(sanDto);
                    break;
                // Vous pouvez ajouter d'autres cas ici si besoin (EMAIL, URI...)
                // case GeneralName.rfc822Name:
                //     sanDto.setSanType(SanTypeEnum.EMAIL);
                //     ...
            }
        }
    }
    
    return sanDtoList;
}
//////////// decoder repris //////////////////
public List<SanDto> extractSansWithTypesFromCsr(String csrPem) throws Exception {
    if (StringUtils.isEmpty(csrPem)) {
        return new ArrayList<>();
    }

    PKCS10CertificationRequest csr = this.csrPemToPKCS10(csrPem);
    if (csr == null) { return new ArrayList<>(); }
    
    List<SanDto> sanDtoList = new ArrayList<>();
    Extension sanExtension = csr.getRequestedExtensions().getExtension(Extension.subjectAlternativeName);

    if (sanExtension != null) {
        GeneralNames generalNames = GeneralNames.getInstance(sanExtension.getParsedValue());
        
        for (GeneralName name : generalNames.getNames()) {
            SanDto sanDto = new SanDto();
            
            switch (name.getTagNo()) {
                case GeneralName.dNSName:
                    sanDto.setSanType(SanTypeEnum.DNSNAME);
                    sanDto.setSanValue(name.getName().toString());
                    sanDtoList.add(sanDto);
                    break;
                
                case GeneralName.iPAddress:
                    sanDto.setSanType(SanTypeEnum.IPADDRESS);
                    sanDto.setSanValue(name.getName().toString());
                    sanDtoList.add(sanDto);
                    break;
                
                case GeneralName.rfc822Name: // Le type pour les emails
                    sanDto.setSanType(SanTypeEnum.RFC822NAME);
                    sanDto.setSanValue(name.getName().toString());
                    sanDtoList.add(sanDto);
                    break;
                    
                case GeneralName.uniformResourceIdentifier: // Le type pour les URI
                    sanDto.setSanType(SanTypeEnum.URI);
                    sanDto.setSanValue(name.getName().toString());
                    sanDtoList.add(sanDto);
                    break;

                case GeneralName.otherName: // Cas plus complexe pour GUID et UPN
                    // otherName est une séquence : [ObjectID, Valeur]
                    ASN1ObjectIdentifier objectId = (ASN1ObjectIdentifier) ((DERTaggedObject) name.getName()).getBaseObject();
                    String otherNameValue = ((DERTaggedObject) objectId.getBaseObject()).getBaseObject().toString();
                    
                    // L'ObjectID nous dit si c'est un GUID ou un UPN. Vous devrez trouver les bons OIDs.
                    // Exemple d'OIDs (à vérifier !)
                    String upnOid = "1.3.6.1.4.1.311.20.2.3";
                    // ... trouvez l'OID pour le GUID
                    
                    if (upnOid.equals(objectId.getId())) {
                        sanDto.setSanType(SanTypeEnum.OTHERNAME_UPN);
                        sanDto.setSanValue(otherNameValue);
                        sanDtoList.add(sanDto);
                    } 
                    // else if (guidOid.equals(objectId.getId())) {
                    //     sanDto.setSanType(SanTypeEnum.OTHERNAME_GUID);
                    //     ...
                    // }
                    break;
            }
        }
    }
    
    return sanDtoList;
}
/////////// nouvelle methode //////////////////////
public List<SanDto> extractSansWithTypesFromCsr(String csrPem) throws Exception {
    if (StringUtils.isEmpty(csrPem)) {
        return new ArrayList<>();
    }

    PKCS10CertificationRequest csr = this.csrPemToPKCS10(csrPem);
    if (csr == null) {
        return new ArrayList<>();
    }

    List<SanDto> sanDtoList = new ArrayList<>();

    // ===================================================================
    // ===                    DÉBUT DE LA CORRECTION                     ===
    // ===================================================================

    // 1. On cherche l'attribut qui contient les extensions dans le CSR.
    Attribute[] attributes = csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);

    // 2. Si cet attribut est trouvé, on en extrait les extensions.
    if (attributes != null && attributes.length > 0) {
        // L'attribut contient un ensemble de valeurs, qui sont nos extensions.
        Extensions extensions = Extensions.getInstance(attributes[0].getAttrValues().getObjectAt(0));

        // 3. À partir d'ici, le code redevient comme avant, mais en utilisant notre variable 'extensions'.
        Extension sanExtension = extensions.getExtension(Extension.subjectAlternativeName);

        if (sanExtension != null) {
            GeneralNames generalNames = GeneralNames.getInstance(sanExtension.getParsedValue());
            for (GeneralName name : generalNames.getNames()) {
                SanDto sanDto = new SanDto();
                
                // Le switch pour gérer tous les types de SAN reste identique
                switch (name.getTagNo()) {
                    case GeneralName.dNSName:
                        sanDto.setSanType(SanTypeEnum.DNSNAME);
                        sanDto.setSanValue(name.getName().toString());
                        sanDtoList.add(sanDto);
                        break;

                    case GeneralName.iPAddress:
                        sanDto.setSanType(SanTypeEnum.IPADDRESS);
                        sanDto.setSanValue(name.getName().toString());
                        sanDtoList.add(sanDto);
                        break;
                    
                    // ... Ajoutez ici les autres cas (RFC822NAME, URI, etc.)
                }
            }
        }
    }

    // ===================================================================
    // ===                     FIN DE LA CORRECTION                      ===
    // ===================================================================
    
    return sanDtoList;
}
///////////////////////////
// ===                    BLOC DE CODE CORRIGÉ                     ===
// ===================================================================
case GeneralName.otherName:
    // 1. Un "otherName" est une séquence ASN.1. On la récupère.
    ASN1Sequence otherNameSequence = ASN1Sequence.getInstance(name.getName());

    // 2. Le premier élément de la séquence est l'ObjectID (OID).
    ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) otherNameSequence.getObjectAt(0);
    
    // 3. Le deuxième élément est la valeur, enveloppée dans un "tag". On l'extrait.
    if (otherNameSequence.size() > 1) {
        ASN1Encodable valueEncodable = ((DERTaggedObject) otherNameSequence.getObjectAt(1)).getBaseObject();
        String otherNameValue = valueEncodable.toString();

        // 4. On compare l'OID avec les valeurs connues pour savoir si c'est un UPN, un GUID, etc.
        // OID pour User Principal Name (UPN) - Standard Microsoft, très courant.
        final String upnOid = "1.3.6.1.4.1.311.20.2.3";
        // OID pour GUID - C'est un exemple, vous devrez peut-être vérifier l'OID exact utilisé dans votre environnement.
        final String guidOid = "1.3.6.1.4.1.311.25.1"; // ATTENTION: OID d'exemple, à vérifier !

        if (upnOid.equals(oid.getId())) {
            sanDto.setSanType(SanTypeEnum.OTHERNAME_UPN);
            sanDto.setSanValue(otherNameValue);
            sanDtoList.add(sanDto);
        } else if (guidOid.equals(oid.getId())) {
            sanDto.setSanType(SanTypeEnum.OTHERNAME_GUID);
            sanDto.setSanValue(otherNameValue);
            sanDtoList.add(sanDto);
        }
        // Vous pouvez ajouter d'autres 'else if' pour d'autres types d'otherName.
    }
    break;
// ===================================================================
// ===                     FIN DU BLOC CORRIGÉ                     ===
// ===================================================================

/////// createRquest ///////////////////////
@Override
public RequestDto createRequest(RequestDto requestDto) {

    List<SanDto> sansFromAngular = new ArrayList<>();
    if (requestDto.getCertificateDto() != null && requestDto.getCertificateDto().getSanDto() != null) {
        sansFromAngular.addAll(requestDto.getCertificateDto().getSanDto());
    }

    List<SanDto> sansFromCsr = new ArrayList<>();
    final String csr = this.fileManagerService.extractCsr(requestDto, Boolean.TRUE);
    if (!StringUtils.isEmpty(csr)) {
        try {
            sansFromCsr = this.certificateCsrDecoder.extractSansWithTypesFromCsr(csr);
        } catch (Exception e) {
            throw new CertisRequestException("error.request.csr.invalid_format", HttpStatus.BAD_REQUEST);
        }
    }

    Set<SanDto> finalUniqueSans = new LinkedHashSet<>();
    finalUniqueSans.addAll(sansFromAngular);
    finalUniqueSans.addAll(sansFromCsr);

    if (requestDto.getCertificateDto() != null) {
        requestDto.getCertificateDto().setSanDto(new ArrayList<>(finalUniqueSans));
    }

    if (requestDto.getComment() != null && requestDto.getComment().length() > 3999) {
        requestDto.setComment(requestDto.getComment().substring(0, 3998));
    }

    Request request = dtoToEntity(requestDto);

    if (!CollectionUtils.isEmpty(requestDto.getCertificate().getSans())) {
        for (San san : requestDto.getCertificate().getSans()) {
            san.setCertificate(requestDto.getCertificate());
        }
    }

    if (!CollectionUtils.isEmpty(requestDto.getContacts())) {
        for (Contact cont : requestDto.getContacts()) {
            cont.setRequests(request);
        }
    }

    RequestDto requestDtoResult = entityToDto(requestDao.save(request));
    
    return requestDtoResult;
}
////////////////////// EnrollBuider //////////////////////
  @Override
    protected List<EnrollPayloadTemplateSanDto> buildSan() {

        List<SanDto> inputSans = automationHubRequestDto.getSanList();
        List<EnrollPayloadTemplateSanDto> enrollPayloadTemplateSanDtos = new ArrayList<>();

        // CAS 1 : La liste de SANs est vide ou nulle. On tente le fallback sur le Common Name.
        if (inputSans == null || inputSans.isEmpty()) {
            String commonName = automationHubRequestDto.getCommonName();
            
            // On applique la règle de validation sur le commonName.
            if (commonName != null && !commonName.isEmpty() && !commonName.contains("|")) {
                EnrollPayloadTemplateSanDto payloadSan = new EnrollPayloadTemplateSanDto();
                payloadSan.setType(SanTypeEnum.DNSNAME.name());
                payloadSan.setValue(Collections.singletonList(commonName));
                enrollPayloadTemplateSanDtos.add(payloadSan);
            }
        } 
        // CAS 2 : La liste de SANs est fournie. On applique la logique de regroupement par type.
        else {
            // On utilise une Map pour regrouper les valeurs.
            Map<String, List<String>> sanBuildingMap = new HashMap<>();

            // Étape 1 : Remplir la map.
            for (SanDto san : inputSans) {
                if (san != null && san.getSanType() != null && san.getSanValue() != null) {
                    String sanType = san.getSanType().name();
                    List<String> currentValues = sanBuildingMap.computeIfAbsent(sanType, k -> new ArrayList<>());
                    currentValues.add(san.getSanValue());
                }
            }

            // Étape 2 : Construire les DTOs finaux à partir de la map.
            for (Map.Entry<String, List<String>> entry : sanBuildingMap.entrySet()) {
                EnrollPayloadTemplateSanDto dto = new EnrollPayloadTemplateSanDto();
                dto.setType(entry.getKey());
                dto.setValue(entry.getValue());
                enrollPayloadTemplateSanDtos.add(dto);
            }
        }

        return enrollPayloadTemplateSanDtos;
    }
}
/////////////////////


@Override
protected List<EnrollPayloadTemplateSanDto> buildSan() {
    
    List<EnrollPayloadTemplateSanDto> sanList = super.buildSan();
    
    String commonName = this.automationHubRequestDto.getCommonName();
    
    if (commonName == null || commonName.isEmpty()) {
        return sanList;
    }

    EnrollPayloadTemplateSanDto dnsNameDto = null;
    for (EnrollPayloadTemplateSanDto dto : sanList) {
        if (SanTypeEnum.DNSNAME.name().equalsIgnoreCase(dto.getType())) {
            dnsNameDto = dto;
            break;
        }
    }

    if (dnsNameDto != null) {
        List<String> values = dnsNameDto.getValue() != null ? new ArrayList<>(dnsNameDto.getValue()) : new ArrayList<>();
        if (!values.contains(commonName)) {
            values.add(commonName);
        }
        dnsNameDto.setValue(values);
        
    } else {
        EnrollPayloadTemplateSanDto newDnsNameDto = new EnrollPayloadTemplateSanDto();
        newDnsNameDto.setType(SanTypeEnum.DNSNAME.name());
        newDnsNameDto.setValue(Collections.singletonList(commonName));
        sanList.add(newDnsNameDto);
    }

    return sanList;
}
/////////////////// 1282///////////////
// package com.bnpparibas.certis.automationhub.dto.business.search; // À adapter au besoin

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CriterionDto {
    private String field;
    private String operator;
    private Object value;
}
////////
import com.bnpparibas.certis.automationhub.dto.business.search.CriterionDto; // Assurez-vous d'importer la classe précédente
import java.util.List;
import lombok.Data;

@Data
public class SearchPayloadDto {
    private String rawQuery;
    private List<CriterionDto> criteria;
    private boolean caseSensitive;
    private List<String> fields;
    private int pageSize;
    private int pageIndex;
}
/////////////
// package com.bnpparibas.certis.automationhub.dto.payload; // À adapter au besoin

import com.bnpparibas.certis.automationhub.dto.business.search.CriterionDto; // Assurez-vous d'importer la classe précédente
import java.util.List;
import lombok.Data;

@Data
public class SearchPayloadDto {
    private String rawQuery;
    private List<CriterionDto> criteria;
    private boolean caseSensitive;
    private List<String> fields;
    private int pageSize;
    private int pageIndex;
}
////
// package com.bnpparibas.certis.automationhub.builder; // À adapter au besoin

import com.bnpparibas.certis.automationhub.dto.business.search.CriterionDto;
import com.bnpparibas.certis.automationhub.dto.business.search.ISearchCriterion;
import com.bnpparibas.certis.automationhub.dto.business.search.SearchCertificateRequestDto;
import com.bnpparibas.certis.automationhub.dto.payload.SearchPayloadDto;
import org.springframework.util.StringUtils;
import java.util.stream.Collectors;

public class SearchPayloadBuilder {

    private final SearchCertificateRequestDto requestDto;

    public SearchPayloadBuilder(SearchCertificateRequestDto requestDto) {
        this.requestDto = requestDto;
    }

    public SearchPayloadDto build() {
        SearchPayloadDto payload = new SearchPayloadDto();
        
        if (StringUtils.hasText(requestDto.getRawQuery())) {
            payload.setRawQuery(requestDto.getRawQuery());
        } else {
            payload.setCriteria(
                requestDto.getCriterionList().stream()
                    .map(this::mapCriterion)
                    .collect(Collectors.toList())
            );
        }

        payload.setCaseSensitive(requestDto.isCaseSensitive());
        return payload;
    }

    // Le coeur de la transparence : un mapping direct, sans "calculs"
    private CriterionDto mapCriterion(ISearchCriterion sourceCriterion) {
        return new CriterionDto(
            sourceCriterion.getField().getValue(),
            sourceCriterion.getOperator().getValue(),
            sourceCriterion.getValue()
        );
    }
}
/////
// Dans le fichier AutomationHubClient.java

// (N'oubliez pas les imports nécessaires pour les nouvelles classes)
import com.bnpparibas.certis.automationhub.builder.SearchPayloadBuilder;
import com.bnpparibas.certis.automationhub.dto.payload.SearchPayloadDto;
// ... autres imports ...

public List<AutomationHubCertificateLightDto> searchCertificates(SearchCertificateRequestDto searchCertificateRequestDto) throws FailedToSearchCertificatesException {
    List<AutomationHubCertificateLightDto> allCertificates = new ArrayList<>();
    boolean hasMore = true;
    int pageIndex = 1;

    // 1. On utilise le nouveau builder pour préparer le payload de manière transparente.
    SearchPayloadDto payload = new SearchPayloadBuilder(searchCertificateRequestDto).build();
    payload.setFields(new ArrayList<>(FIELDS_FOR_SEARCH_ENDPOINT)); // On peut garder la liste des champs à retourner

    while (hasMore) {
        payload.setPageSize(paginationConfig.getPageSize());
        payload.setPageIndex(pageIndex);
        
        LOGGER.info("Searching certificates with payload: {}", payload);

        // 2. On envoie directement l'objet 'payload'. Spring/Jackson le convertit en JSON.
        // Assurez-vous que ResponseSearchDto est le bon type de classe pour la réponse.
        ResponseSearchDto resp = automationHubRestTemplate.postForEntity(
                "/certificates/search",
                payload,
                ResponseSearchDto.class
        ).getBody();

        // Le reste de la logique pour traiter la réponse reste similaire
        if (resp != null && resp.getSearchResultDto() != null) {
            allCertificates.addAll(
                    responseSearchDtoToSearchResultDtoMapper.toSearchResponseDto(resp).getResults()
            );
            hasMore = resp.getSearchResultDto().getHasMore();
            pageIndex++;
        } else {
            hasMore = false;
        }
    }
    return allCertificates;
}
////
import com.bnpparibas.certis.automationhub.builder.SearchPayloadBuilder;
import com.bnpparibas.certis.automationhub.dto.payload.SearchPayloadDto;
// ... vos autres imports ...

// REMPLACEZ VOTRE MÉTHODE EXISTANTE PAR CELLE-CI :
public List<AutomationHubCertificateLightDto> searchCertificates(SearchCertificateRequestDto searchCertificateRequestDto) throws FailedToSearchCertificatesException {
    List<AutomationHubCertificateLightDto> allCertificates = new ArrayList<>();
    boolean hasMore = true;
    int pageIndex = 1;

    // 1. On utilise le nouveau builder pour préparer le payload de manière transparente.
    SearchPayloadDto payload = new SearchPayloadBuilder(searchCertificateRequestDto).build();
    payload.setFields(new ArrayList<>(FIELDS_FOR_SEARCH_ENDPOINT)); // Garde la liste des champs à retourner

    while (hasMore) {
        payload.setPageSize(paginationConfig.getPageSize());
        payload.setPageIndex(pageIndex);
        
        LOGGER.info("Searching certificates with payload: {}", payload);

        // 2. On envoie directement l'objet 'payload'. Spring s'occupe de la conversion en JSON.
        // Assurez-vous que ResponseSearchDto est la bonne classe pour la réponse de l'API.
        ResponseSearchDto resp = automationHubRestTemplate.postForEntity(
                "/certificates/search",
                payload,
                ResponseSearchDto.class
        ).getBody();

        // 3. Le reste de la logique pour traiter la réponse et gérer la pagination
        if (resp != null && resp.getSearchResultDto() != null) {
            // Assurez-vous que votre mapper est compatible avec le nouveau format si nécessaire
            allCertificates.addAll(
                    responseSearchDtoToSearchResultDtoMapper.toSearchResponseDto(resp).getResults()
            );
            hasMore = resp.getSearchResultDto().getHasMore();
            pageIndex++;
        } else {
            hasMore = false;
        }
    }
    return allCertificates;
}
///
if (resp != null) {
    // On réutilise votre mapper existant pour extraire le SearchResultDto.
    // Assurez-vous que le nom du mapper et de la méthode sont corrects.
    SearchResultDto searchResult = responseSearchDtoToSearchResultDtoMapper.toSearchResultDtoMapper(resp);

    if (searchResult != null) {
        // On ajoute les résultats de la page courante à notre liste totale
        allCertificates.addAll(searchResult.getResults());
        
        // On met à jour la variable 'hasMore' pour savoir si on doit continuer la boucle
        hasMore = searchResult.getHasMore();
        pageIndex++;
    } else {
        // S'il n'y a pas de résultat, on arrête la boucle
        hasMore = false;
    }
} else {
    // Si la réponse est nulle, on arrête la boucle
    hasMore = false;
}
/////
/**
 * @deprecated Remplacé par la logique du {@link SearchPayloadBuilder}.
 * Cette méthode sera supprimée dans une future version.
 */
@Deprecated
private String buildAutomationHubQuery(SearchCertificateRequestDto searchCertificateRequestDto) {
    // ... le code original reste ici pour le moment ...
}

/**
 * @deprecated Fait partie de l'ancienne logique de construction de query.
 * À supprimer avec buildAutomationHubQuery.
 */
@Deprecated
private String buildCondition(ISearchCriterion criterion) {
    // ...
}
//////
public SearchPayloadDto build() {
    SearchPayloadDto payload = new SearchPayloadDto();

    // On vérifie s'il y a une 'rawQuery' dans la requête d'entrée
    if (StringUtils.hasText(requestDto.getRawQuery())) {
        
        // CORRECTION 1 : Le nom de la méthode est "setQuery", pas "setRawQuery"
        payload.setQuery(requestDto.getRawQuery());

    } else {
        
        // CORRECTION 2 : C'est ici qu'on appelle la méthode "setCriteria"
        payload.setCriteria(
            requestDto.getCriterionList().stream()
                .map(this::mapCriterion)
                .collect(Collectors.toList())
        );
    }

    // Le reste ne change pas
    payload.setCaseSensitive(requestDto.isCaseSensitive());
    return payload;
}
///////
public enum SearchCriterionDateOperatorEnum implements ISearchCriterionOperatorEnum {

    // 1. Associez une valeur textuelle à chaque membre de l'énumération
    EQ("EQ"),
    AFTER("AFTER"),
    BEFORE("BEFORE");

    // 2. Ajoutez un champ privé pour stocker cette valeur
    private final String value;

    // 3. Créez un constructeur pour initialiser ce champ
    SearchCriterionDateOperatorEnum(String value) {
        this.value = value;
    }

    // 4. Implémentez la méthode requise par l'interface (ce qui corrige l'erreur)
    @Override
    public String getValue() {
        return this.value;
    }
    
    // Votre autre méthode existante ne change pas
    public AllOperatorEnum getBaseOperatorEnum() {
        // La logique ici dépend de ce que fait votre AllOperatorEnum
        // C'est probablement quelque chose comme ça :
        return AllOperatorEnum.valueOf(this.name());
    }
}
//////

public enum SearchCriterionTextOperatorEnum implements ISearchCriterionOperatorEnum {

    // 1. Associez une valeur textuelle à chaque membre
    EQ("EQ"),
    NOT_EQ("NOT_EQ"),
    IN("IN"),
    NOT_IN("NOT_IN"),
    CONTAINS("CONTAINS"),
    NOT_CONTAINS("NOT_CONTAINS"),
    IN_CONTAINS("IN_CONTAINS"); // Note : Ce nom est inhabituel, vérifiez s'il est correct

    // 2. Ajoutez le champ pour stocker la valeur
    private final String value;

    // 3. Ajoutez le constructeur
    SearchCriterionTextOperatorEnum(String value) {
        this.value = value;
    }

    // 4. Implémentez la méthode de l'interface pour corriger l'erreur
    @Override
    public String getValue() {
        return this.value;
    }
    
    // Votre autre méthode ne change pas
    public AllOperatorEnum getBaseOperatorEnum() {
        // La logique ici dépend de ce que fait votre AllOperatorEnum
        // C'est probablement quelque chose comme ça :
        return AllOperatorEnum.valueOf(this.name());
    }
}
///////////////////////////// incident ///////////////////////////
private void createOrUpdateIncident(
    AutomationHubCertificateLightDto dto, 
    ReferenceRefiDto referenceRefiDto, 
    IncidentPriority priority, 
    // On suppose que le ProcessingContext est un champ de la classe, accessible via 'this.context'
    // ou qu'il faut le passer en paramètre pour accéder aux compteurs/listes.
    // Pour cet exemple, je vais le passer en paramètre.
    ProcessingContext<AutomationHubCertificateLightDto> context
) {
    
    // 1. Récupération de l'état actuel
    List<AutoItsmTaskDtoImpl> existingTasks = itsmTaskService.findByAutomationhubIdAndTypeAndCreationDate(...);
    AutoItsmTaskDtoImpl existingTask = itsmTaskService.getIncNumberByAutoItsmTaskList(existingTasks);
    SnowIncidentReadResponseDto snowIncident = (existingTask == null) ? null : snowService.getIncidentBySysId(existingTask.getItsmId());

    // 2. Préparation des données communes (comme dans vos images)
    String summary = dto.getAutomationHubId() + " : " + (existingTask == null ? "None" : existingTask.getItsmId()) + " : " + dto.getCommonName();
    String warningInfo = dto.getAutomationHubId() + " : " + dto.getCommonName();

    // 3. Logique de décision principale (avec try-catch et les bonnes signatures)
    try {
        // CAS 1 : Aucun incident
        if (snowIncident == null) {
            String actionMessage = "Création INC " + priority.name();
            LOGGER.info("Aucun INC actif pour {}. {}.", dto.getAutomationHubId(), actionMessage);
            
            // APPEL AVEC LA SIGNATURE DE VOS IMAGES
            this.createNewInc(dto, referenceRefiDto, priority, summary, warningInfo, actionMessage, context, null);

        // CAS 2 : Incident résolu
        } else if (isIncidentResolved(snowIncident)) {
            String actionMessage = "Recréation INC " + priority.name();
            LOGGER.info("L'INC précédent {} est résolu. {}.", snowIncident.getNumber(), actionMessage);
            
            // APPEL AVEC LA SIGNATURE DE VOS IMAGES
            this.recreateInc(dto, referenceRefiDto, priority, summary, warningInfo, actionMessage, context, existingTask);

        // CAS 3 : Incident ouvert et déjà prioritaire
        } else if (Integer.parseInt(snowIncident.getPriority()) <= IncidentPriority.URGENT.getValue()) {
            LOGGER.info("L'INC existant {} a déjà une priorité suffisante ({}). Aucune action requise.", snowIncident.getNumber(), snowIncident.getPriority());
            // Aucune action

        // CAS 4 : Incident ouvert, priorité basse -> Mise à jour
        } else {
            String actionMessage = "Mise à jour INC vers " + priority.name();
            LOGGER.info("L'INC existant {} (priorité {}) doit être mis à jour vers {}.", snowIncident.getNumber(), snowIncident.getPriority(), priority.name());
            
            // APPEL AVEC LA SIGNATURE DE VOS IMAGES
            this.updateInc(priority, actionMessage, context, existingTask);
        }
    } catch (NumberFormatException e) {
        // Gestion de l'erreur de parsing, comme vous l'avez implémenté
        LOGGER.error("Impossible de parser la priorité '{}' pour l'incident {}. La mise à jour est annulée.", snowIncident.getPriority(), snowIncident.getNumber(), e);
        context.recordError("Mise à jour annulée", "Priorité ITSM invalide", dto, null);
    
    } catch (Exception e) {
        // Un catch-all pour les erreurs vraiment inattendues
        LOGGER.error("Une erreur inattendue est survenue lors de la décision create/update pour le certificat {}.", dto.getAutomationHubId(), e);
        context.recordError("Erreur inattendue", e.getMessage(), dto, null);
    }
}
/////////////////////////
import com.bnpparibas.certis.api.enums.IncidentPriority;
import com.bnpparibas.certis.api.enums.InciTypeEnum;
import com.bnpparibas.certis.api.enums.SnowIncStateEnum;
import com.bnpparibas.certis.api.utils.ProcessingContext;
import com.bnpparibas.certis.automationhub.dto.AutomationHubCertificateLightDto;
import com.bnpparibas.certis.automationhub.dto.CertificateLabelDto;
import com.bnpparibas.certis.itsm.dto.AutoItsmTaskDto;
import com.bnpparibas.certis.itsm.dto.AutoItsmTaskDtoImpl;
import com.bnpparibas.certis.referential.dto.OwnerAndReferenceRefiResult;
import com.bnpparibas.certis.referential.dto.ReferenceRefiDto;
import com.bnpparibas.certis.snow.dto.SnowIncidentReadResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class IncidentAutoEnrollTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentAutoEnrollTask.class);

    // --- Dépendances (injectées par constructeur) ---
    private final ItsmTaskService itsmTaskService;
    private final CertificateOwnerService certificateOwnerService;
    private final AutomationHubService automationHubService;
    private final SnowService snowService;
    private final SendMailUtils sendMailUtils;

    // --- Configuration ---
    @Value("${incident.autoenroll.enabled:true}")
    private boolean isAutoEnrollEnabled;
    
    @Value("${autoenroll.priority.urgent.threshold.days:3}")
    private int urgentPriorityThresholdDays;

    @Value("${autoenroll.processing.window.days:15}")
    private int processingWindowDays;
    
    @Value("${certis.mail.ipkiTeam}")
    private String ipkiTeam;

    @Autowired
    public IncidentAutoEnrollTask(ItsmTaskService itsmTaskService, CertificateOwnerService certificateOwnerService, AutomationHubService automationHubService, SnowService snowService, SendMailUtils sendMailUtils) {
        this.itsmTaskService = itsmTaskService;
        this.certificateOwnerService = certificateOwnerService;
        this.automationHubService = automationHubService;
        this.snowService = snowService;
        this.sendMailUtils = sendMailUtils;
    }

    @Scheduled(cron = "${cron.task.incidentAutoEnroll:0 30 9 * * *}")
    public void processExpireCertificates() {
        if (!isAutoEnrollEnabled) {
            LOGGER.warn("La tâche IncidentAutoEnrollTask est désactivée par configuration. Aucune action ne sera effectuée.");
            return;
        }
        
        LOGGER.info("Démarrage de la tâche de traitement de l'expiration des certificats.");
        Instant startTime = Instant.now();
        
        Function<AutomationHubCertificateLightDto, Map<String, Object>> certificateMapper = cert -> {
            Map<String, Object> data = new HashMap<>();
            if (cert != null) {
                data.put("automationHubId", cert.getAutomationHubId());
                data.put("commonName", cert.getCommonName());
                data.put("expiryDate", formatDate(cert.getExpiryDate()));
                data.put("codeAp", getLabelByKey(cert, "APCode"));
            }
            return data;
        };
        
        ProcessingContext<AutomationHubCertificateLightDto> context = new ProcessingContext<>(certificateMapper);

        try {
            List<AutomationHubCertificateLightDto> certificates = automationHubService.searchAutoEnrollExpiring(processingWindowDays);
            if (certificates == null || certificates.isEmpty()) {
                LOGGER.info("Aucun certificat à traiter pour cette exécution.");
            } else {
                LOGGER.info("{} certificat(s) trouvé(s) à traiter.", certificates.size());
                this.processAllCertificates(certificates, context);
            }
        } catch (Exception e) {
            LOGGER.error("La tâche de traitement a échoué de manière critique. Impossible de continuer.", e);
            throw new RuntimeException("Échec critique de la tâche planifiée processExpiredCertificates", e);
        } finally {
            this.sendSummaryReports(context);
            Duration duration = Duration.between(startTime, Instant.now());
            LOGGER.info("Fin de la tâche de traitement. Temps d'exécution total : {} ms.", duration.toMillis());
        }
    }

    private void processAllCertificates(List<AutomationHubCertificateLightDto> certificates, ProcessingContext<AutomationHubCertificateLightDto> context) {
        for (AutomationHubCertificateLightDto certificate : certificates) {
            try {
                this.processSingleCertificate(certificate, context);
            } catch (Exception e) {
                LOGGER.error("Erreur système inattendue lors du traitement du certificat ID: {}. Le traitement continue.", certificate.getAutomationHubId(), e);
                context.recordError("Traitement échoué", "Erreur système : " + e.getMessage(), certificate, null);
            }
        }
    }

    private void processSingleCertificate(AutomationHubCertificateLightDto certificate, ProcessingContext<AutomationHubCertificateLightDto> context) {
        String environment = getLabelByKey(certificate, "ENVIRONMENT");
        if (!"PROD".equalsIgnoreCase(String.valueOf(environment))) {
            return;
        }
        
        String codeAp = getLabelByKey(certificate, "APCode");
        if (codeAp == null || codeAp.trim().isEmpty() || "N/A".equals(codeAp)) {
            LOGGER.warn("Traitement annulé pour certificat ID '{}': le label 'APCode' est manquant.", certificate.getAutomationHubId());
            context.recordValidationError(certificate);
            return;
        }
        
        OwnerAndReferenceRefiResult ownerResult = certificateOwnerService.findBestAvailableCertificateOwner(certificate, codeAp);

        if (ownerResult == null || ownerResult.getOwnerDTO() == null) {
            if (ownerResult == null) {
                LOGGER.warn("Traitement annulé pour certificat ID '{}': codeAp '{}' invalide.", certificate.getAutomationHubId(), codeAp);
            } else {
                LOGGER.warn("Traitement annulé pour certificat ID '{}': codeAp '{}' valide mais aucun propriétaire trouvé.", certificate.getAutomationHubId(), codeAp);
            }
            context.recordValidationError(certificate);
            return;
        }
        
        ReferenceRefiDto referenceRefiDto = ownerResult.getReferenceRefiDto();
        boolean isExpiringUrgently = certificate.getExpiryDate().compareTo(DateUtils.addDays(new Date(), urgentPriorityThresholdDays)) <= 0;
        IncidentPriority priority = isExpiringUrgently ? IncidentPriority.URGENT : IncidentPriority.STANDARD;
        
        createOrUpdateIncident(certificate, referenceRefiDto, priority, context);
    }
    
    private void createOrUpdateIncident(AutomationHubCertificateLightDto dto, ReferenceRefiDto referenceRefiDto, IncidentPriority priority, ProcessingContext<AutomationHubCertificateLightDto> context) {
        List<AutoItsmTaskDtoImpl> existingTasks = itsmTaskService.findByAutomationhubIdAndTypeAndCreationDate(dto.getAutomationHubId(), InciTypeEnum.AUTOENROLL, null);
        AutoItsmTaskDtoImpl existingTask = itsmTaskService.getIncNumberByAutoItsmTaskList(existingTasks);
        SnowIncidentReadResponseDto snowIncident = (existingTask == null) ? null : snowService.getIncidentBySysId(existingTask.getItsmId());

        if (snowIncident == null) {
            createNewInc(dto, referenceRefiDto, priority, context, null);
            return;
        }
        
        if (isIncidentResolved(snowIncident)) {
            recreateInc(dto, referenceRefiDto, priority, context, existingTask);
            return;
        }

        try {
            int existingPriorityValue = Integer.parseInt(snowIncident.getPriority());
            if (existingPriorityValue <= IncidentPriority.URGENT.getValue()) {
                LOGGER.info("L'INC existant {} a déjà une priorité élevée ({}). Aucune action requise.", snowIncident.getNumber(), existingPriorityValue);
            } else if (existingPriorityValue == priority.getValue()) {
                LOGGER.info("L'INC existant {} a déjà la priorité requise ({}). Aucune action requise.", snowIncident.getNumber(), priority.name());
            } else {
                updateInc(dto, referenceRefiDto, priority, context, existingTask);
            }
        } catch (NumberFormatException e) {
            LOGGER.error("Impossible de parser la priorité '{}' pour l'incident {}. La mise à jour est annulée.", snowIncident.getPriority(), snowIncident.getNumber(), e);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("priority", "INVALIDE (" + snowIncident.getPriority() + ")");
            context.recordError("Mise à jour annulée", "Priorité ITSM invalide", dto, errorDetails);
        }
    }

    private void createNewInc(AutomationHubCertificateLightDto dto, ReferenceRefiDto refiDto, IncidentPriority priority, ProcessingContext<AutomationHubCertificateLightDto> context, @Nullable AutoItsmTaskDtoImpl relatedTask) {
        String actionMessage = (relatedTask == null) ? "Création INC " : "Recréation INC ";
        actionMessage += priority.name();
        try {
            String title = ...; // Générer le titre, par ex: "[AUTOENROLL] Certificat " + dto.getCommonName()
            String description = ...; // Générer la description via un template Velocity
            
            AutoItsmTaskDto createdTask = itsmTaskService.createIncidentAutoEnroll(dto, refiDto, priority.getValue(), title, description, InciTypeEnum.AUTOENROLL, relatedTask);

            Map<String, Object> successDetails = new HashMap<>();
            successDetails.put("supportGroup", refiDto.getGroupSupportDto().getName());
            successDetails.put("priority", priority.name());
            successDetails.put("incidentNumber", createdTask.getItsmId());
            context.recordSuccess(actionMessage, dto, successDetails);
        } catch (Exception e) {
            LOGGER.error("Échec de l'action '{}' pour le certificat {}: {}", actionMessage, dto.getAutomationHubId(), e.getMessage());
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("supportGroup", (refiDto.getGroupSupportDto() != null) ? refiDto.getGroupSupportDto().getName() : "N/A");
            errorDetails.put("priority", priority.name());
            context.recordError(actionMessage, e.getMessage(), dto, errorDetails);
        }
    }

    private void recreateInc(AutomationHubCertificateLightDto dto, ReferenceRefiDto refiDto, IncidentPriority priority, ProcessingContext<AutomationHubCertificateLightDto> context, AutoItsmTaskDtoImpl existingTask) {
        createNewInc(dto, refiDto, priority, context, existingTask);
    }
    
    private void updateInc(AutomationHubCertificateLightDto dto, ReferenceRefiDto refiDto, IncidentPriority priority, ProcessingContext<AutomationHubCertificateLightDto> context, AutoItsmTaskDtoImpl existingTask) {
        String actionMessage = "Mise à jour INC vers " + priority.name();
        try {
            AutoItsmTaskDto updatedTask = itsmTaskService.upgradeIncidentAutoEnroll(priority.getValue(), existingTask);
            Map<String, Object> successDetails = new HashMap<>();
            successDetails.put("supportGroup", refiDto.getGroupSupportDto().getName());
            successDetails.put("priority", priority.name());
            successDetails.put("incidentNumber", updatedTask.getItsmId());
            context.recordSuccess(actionMessage, dto, successDetails);
        } catch (Exception e) {
            LOGGER.error("Échec de la mise à jour pour l'incident {}: {}", existingTask.getItsmId(), e.getMessage());
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("supportGroup", refiDto.getGroupSupportDto().getName());
            errorDetails.put("priority", priority.name());
            context.recordError(actionMessage, e.getMessage(), dto, errorDetails);
        }
    }

    private void sendSummaryReports(ProcessingContext<AutomationHubCertificateLightDto> context) {
        sendFinalReport(context);
        sendAutoEnrollCertificateNoCodeApReport(context);
    }
    
    private void sendFinalReport(ProcessingContext<AutomationHubCertificateLightDto> context) {
        if (!context.hasItemsForFinalReport()) {
            LOGGER.info("Rapport de synthèse non envoyé : aucune action de création/erreur à signaler.");
            return;
        }
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> allSuccesses = context.getSuccessReportItems();
        data.put("urgentSuccessItems", allSuccesses.stream().filter(i -> "URGENT".equals(i.get("priority"))).collect(Collectors.toList()));
        data.put("standardSuccessItems", allSuccesses.stream().filter(i -> "STANDARD".equals(i.get("priority"))).collect(Collectors.toList()));
        data.put("errorItems", context.getErrorReportItems());
        data.put("date", new Date());
        try {
            sendMailUtils.sendEmail("template/report-incident-summary.vm", data, List.of(ipkiTeam), "Rapport de Synthèse - Incidents Auto-Enroll");
            LOGGER.info("Rapport de synthèse envoyé avec {} succès et {} erreurs.", context.getSuccessCounter().get(), context.getErrorCounter().get());
        } catch (Exception e) {
            LOGGER.error("Échec de l'envoi de l'e-mail de synthèse.", e);
        }
    }
    
    private void sendAutoEnrollCertificateNoCodeApReport(ProcessingContext<AutomationHubCertificateLightDto> context) {
        List<AutomationHubCertificateLightDto> certsInError = context.getItemsWithValidationError();
        if (certsInError.isEmpty()) {
            LOGGER.info("Rapport d'alerte non envoyé : aucune erreur de validation à signaler.");
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("certsWithoutCodeAp", certsInError);
        data.put("date", new Date());
        try {
            sendMailUtils.sendEmail("template/report-no-codeap.vm", data, List.of(ipkiTeam), "ALERTE : Action Manuelle Requise - Certificats sans Propriétaire Valide");
            LOGGER.info("Rapport d'alerte envoyé avec succès.");
        } catch (Exception e) {
            LOGGER.error("Échec de l'envoi de l'e-mail d'alerte.", e);
        }
    }
	/////////////////////////////////
	import com.bnpparibas.certis.certificate.request.San; // L'entité JPA
import com.bnpparibas.certis.certificate.request.service.SanService;
// ... Ajoutez tous les autres imports nécessaires ...
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.apache.commons.lang3.StringUtils; // Assurez-vous d'avoir cet import
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SanServiceImpl implements SanService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SanServiceImpl.class);
    
    // ... Vos dépendances (@Autowired) et constantes restent ici ...

    /**
     * Méthode principale de validation des SANs.
     * Elle orchestre les différentes vérifications.
     */
    @Override
    public void validateSansPerRequest(RequestDto requestDto) {
        if (this.skipValidationIfDataMissing(requestDto)) {
            return;
        }

        // =========================================================================
        // ### CHANGEMENT 1 : Appel à la nouvelle méthode de validation de base ###
        // On vérifie la structure des entités San avant toute autre logique.
        // =========================================================================
        verifySanEntities(requestDto.getCertificate().getSans());

        if ("INT_USAGE".equalsIgnoreCase(requestDto.getUsage())) {
            this.verifySansLimitForInternalCertificates(requestDto);
        }

        if ("EXT_USAGE".equalsIgnoreCase(requestDto.getUsage())) {
            this.verifySansLimitForExternalCertificates(requestDto);
        }
    }

    /**
     * Vérifie la validité des SANs par rapport à la base de référence Refweb.
     */
    @Override
    public List<String> validateSansOnRefweb(RequestDto requestDto) throws Exception {
        ArrayList<String> sansInvalid = new ArrayList<>();
        // ... (logique de bypass inchangée) ...

        // =========================================================================
        // ### CHANGEMENT 2 : Utilisation de la liste d'entités `San` ###
        // Avant : On travaillait avec une List<SanDto> ou une List<String>.
        // Maintenant : On travaille directement avec la List<San> de l'entité Certificate.
        // =========================================================================
        List<San> sanList = requestDto.getCertificate().getSans();
        
        boolean hasNoSans = CollectionUtils.isEmpty(sanList);
        // ... (logique isWhitelistedPlatform inchangée) ...

        if (hasNoSans || isWhitelistedPlatform) {
            return Collections.emptyList();
        }

        for (San sanEntity : sanList) {
            // On utilise le getter de l'entité pour récupérer la valeur à valider.
            String valueToValidate = sanEntity.getSanValue(); 
            
            // Le reste de la logique est INCHANGÉ car les méthodes enfants attendent déjà une String.
            if (this.evaluateCnAndCertTypeForCheckRefWebSan(requestDto, valueToValidate)) {
                if (!this.checkSanUrlOnRefweb(valueToValidate)) {
                    sansInvalid.add(valueToValidate);
                }
            }
        }
        return sansInvalid;
    }

    /**
     * Applique la logique métier, comme l'ajout du "www." au Common Name si nécessaire.
     */
    @Override
    public RequestDto buildSANs(RequestDto requestDto) throws Exception {
        try {
            if (/* ... votre logique de condition existante ... */) {
                evaluateSan3W(requestDto); // Appel à la méthode privée adaptée
            }
        } catch (Exception e) {
            LOGGER.error("Exception in buildSANs {}", e.getMessage());
        }
        return requestDto;
    }
    
    // --- MÉTHODES PRIVÉES ---

    /**
     * =========================================================================
     * ### CHANGEMENT 3 : Adaptation de `evaluateSan3W` ###
     * Cette méthode manipule et crée maintenant des ENTITÉS `San` et non des DTOs.
     * =========================================================================
     */
    private void evaluateSan3W(RequestDto requestDto) {
        List<San> sanEntityList = requestDto.getCertificate().getSans();
        if (sanEntityList == null) {
            sanEntityList = new ArrayList<>();
            requestDto.getCertificate().setSans(sanEntityList);
        }

        String cn = requestDto.getCertificate().getCommonName();
        String domainWWW = cn.startsWith("www.") ? cn : "www." + cn;
        final String finalDomainWWW = domainWWW;

        boolean alreadyExists = sanEntityList.stream()
                .anyMatch(san -> finalDomainWWW.equalsIgnoreCase(san.getSanValue()));

        if (!alreadyExists) {
            San sanEntityWWW = new San(); // On crée une NOUVELLE ENTITÉ
            sanEntityWWW.setType(SanTypeEnum.DNSNAME);
            sanEntityWWW.setSanValue(finalDomainWWW);
            sanEntityWWW.setUrl(finalDomainWWW);
            sanEntityWWW.setCertificate(requestDto.getCertificate()); // On lie l'entité à son parent
            
            sanEntityList.add(0, sanEntityWWW);
        }
    }

    /**
     * =========================================================================
     * ### CHANGEMENT 4 : NOUVELLE méthode privée de validation ###
     * Cette méthode a été ajoutée pour vérifier la structure de base de chaque SAN
     * (type non null, valeur non vide).
     * =========================================================================
     */
    private void verifySanEntities(List<San> sanList) {
        if (CollectionUtils.isEmpty(sanList)) {
            return;
        }
        for (San san : sanList) {
            // Règle du ticket JIRA : "Soit la clé type est presente => doit etre un des type de san definit et donc pas etre null"
            if (san.getType() == null || StringUtils.isEmpty(san.getSanValue())) {
                throw new CertisRequestException("Type ou valeur de SAN invalide.", HttpStatus.BAD_REQUEST);
            }
        }
    }
    
    /**
     * =========================================================================
     * ### INCHANGÉ ###
     * Cette méthode n'a pas besoin de changer car son contrat (elle reçoit un DTO et une String)
     * est toujours respecté par la méthode qui l'appelle.
     * =========================================================================
     */
    private Boolean evaluateCnAndCertTypeForCheckRefWebSan(RequestDto requestDto, String san) {
        // ... (votre code existant ici) ...
        return result;
    }
    
    // ... (toutes les autres méthodes de votre classe restent ici) ...
}
/////// controller //////////////////

@RestController
// ... (autres annotations de votre contrôleur)
public class RequestController {
    
    // --- Vos injections de dépendances (@Autowired) restent ici ---
    @Autowired private RequestService requestService;
    @Autowired private SanService sanService;
    // ... etc.

    /**
     * Endpoint principal pour la création et la mise à jour des requêtes.
     */
    @Transactional
    @PostMapping("/save") // ou le chemin de votre endpoint
    public ResponseEntity<?> saveRequest(
            // =========================================================================
            // ### CHANGEMENT 1 : La signature de la méthode est modifiée ###
            // On reçoit le JSON en tant que String brute pour le contrôler manuellement.
            // =========================================================================
            @RequestBody String jsonPayload, 
            UriComponentsBuilder uriComponentsBuilder, 
            ServletRequest servletRequest, 
            ApiAuthentication apiAuthentication) throws Exception {

        // =========================================================================
        // ### CHANGEMENT 2 : Mapping manuel en deux temps ###
        // =========================================================================
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Premier passage : On utilise Jackson pour remplir tous les champs de RequestDtoImpl
        // SAUF le champ 'certificate', grâce à l'annotation @JsonIgnoreProperties.
        RequestDtoImpl requestDto = objectMapper.readValue(jsonPayload, RequestDtoImpl.class);
        
        // Deuxième passage : On appelle notre nouvelle méthode privée pour construire l'entité Certificate
        // à partir du JSON, puis on l'attache à notre DTO.
        Certificate certificateEntity = buildCertificateFromPayload(jsonPayload);
        requestDto.setCertificate(certificateEntity);

        // --- À PARTIR D'ICI, LE RESTE DE VOTRE MÉTHODE EST INCHANGÉ ---
        // Le reste de votre code peut maintenant utiliser `requestDto` comme si
        // tout avait été mappé automatiquement, car nous avons fait le travail pour lui.
        
        RequestDto requestDtoOngoing = requestService.findOngoingRequestByCommonName(requestDto.getCertificate().getCommonName());
        // ... (toute votre logique de validation, d'appels aux services, etc.)
        sanService.validateSansPerRequest(requestDto);
        sanService.buildSANs(requestDto);
        RequestDto requestDtoResult = requestService.createRequest(requestDto);
        // ... (toute votre logique post-création, envoi d'emails, etc.)
        
        return new ResponseEntity<>(/* ... votre objet de réponse ... */);
    }
    
    /**
     * =========================================================================
     * ### CHANGEMENT 3 : Nouvelle méthode privée pour le mapping manuel ###
     * Le rôle de cette méthode est de lire le JSON et de construire l'entité
     * Certificate avec sa liste d'entités San.
     * =========================================================================
     */
    private Certificate buildCertificateFromPayload(String jsonPayload) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonPayload);
        JsonNode certificateNode = rootNode.path("certificate");
    
        if (certificateNode.isMissingNode()) {
            return null; // ou lever une exception si le certificat est obligatoire
        }
    
        // On peut utiliser un DTO temporaire pour laisser Jackson faire une partie du travail.
        CertificateDtoImpl certDto = objectMapper.treeToValue(certificateNode, CertificateDtoImpl.class);

        // On construit l'entité finale à partir des données du DTO temporaire.
        Certificate certificateEntity = new Certificate();
        certificateEntity.setCommonName(certDto.getCommonName());
        certificateEntity.setPassword(certDto.getPassword());
        // ... mappez ici les autres champs simples du certificat ...
    
        List<San> sanEntitiesFromDto = certDto.getSans(); // Récupère la List<San> créée par Jackson
        if (!CollectionUtils.isEmpty(sanEntitiesFromDto)) {
            // Il faut absolument re-lier chaque SAN à son parent Certificate.
            for (San san : sanEntitiesFromDto) {
                // Règle du backend : si le type est null, on met DNSNAME par défaut.
                if (san.getType() == null) {
                    san.setType(SanTypeEnum.DNSNAME);
                }
                san.setCertificate(certificateEntity);
            }
            certificateEntity.setSans(sanEntitiesFromDto);
        }
    
        return certificateEntity;
    }

    // --- Le reste de vos méthodes (comme updateRequestInfo, isDraftRequest, etc.) est INCHANGÉ ---
}
/////// decoder ///////////////////////////
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.CollectionUtils;

public class CertificateDeserializer extends StdDeserializer<Certificate> {

    public CertificateDeserializer() {
        this(null);
    }

    public CertificateDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Certificate deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        // On récupère le JsonNode qui correspond à l'objet "certificate"
        JsonNode certificateNode = jp.getCodec().readTree(jp);

        // On crée notre entité vide
        Certificate certificateEntity = new Certificate();

        // On mappe manuellement les champs simples
        if (certificateNode.has("commonName")) {
            certificateEntity.setCommonName(certificateNode.get("commonName").asText());
        }
        if (certificateNode.has("password")) {
            certificateEntity.setPassword(certificateNode.get("password").asText());
        }
        // ... mappez ici tous les autres champs simples du certificat ...

        // On mappe la liste des SANs
        JsonNode sansNode = certificateNode.path("sans");
        if (sansNode.isArray()) {
            List<San> sanEntities = new ArrayList<>();
            for (JsonNode sanNode : sansNode) {
                San sanEntity = new San();
                
                if (sanNode.has("type") && !sanNode.get("type").isNull()) {
                    sanEntity.setType(SanTypeEnum.valueOf(sanNode.get("type").asText()));
                } else {
                    sanEntity.setType(SanTypeEnum.DNSNAME); // Règle du backend : défaut DNSNAME
                }
                
                if (sanNode.has("sanValue")) sanEntity.setSanValue(sanNode.get("sanValue").asText());
                if (sanNode.has("url")) sanEntity.setUrl(sanNode.get("url").asText());
                
                sanEntity.setCertificate(certificateEntity); // On lie le SAN à son parent
                sanEntities.add(sanEntity);
            }
            certificateEntity.setSans(sanEntities);
        }

        return certificateEntity;
    }
}

    private boolean isIncidentResolved(SnowIncidentReadResponseDto snowIncident) {
        try {
            return snowIncident != null && Integer.parseInt(snowIncident.getState()) >= SnowIncStateEnum.RESOLVED.getValue();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String getLabelByKey(AutomationHubCertificateLightDto dto, String key) {
        if (dto == null || dto.getLabels() == null || key == null) return null;
        return dto.getLabels().stream()
                  .filter(label -> key.equalsIgnoreCase(label.getKey()))
                  .map(CertificateLabelDto::getValue)
                  .findFirst()
                  .orElse(null);
    }
    
    private String formatDate(Date date) {
        if (date == null) return "N/A";
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}