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
};