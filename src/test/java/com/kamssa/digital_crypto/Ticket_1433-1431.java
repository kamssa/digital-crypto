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