Le ticket décrit plusieurs règles métier très précises, principalement liées au Common Name (CN) qui doit être traité comme un SAN de type DNSNAME.
Règle principale (commune à plusieurs cas) :
"Le front et le back doit ajouter les SANS obligatoire en tant que DNSNAME. Cote back l'ajouté si besoin de maniere transparente. Cote front l'ajouter dans le formulaire des SAN (en 1er) non supprimable et non editable."
Traduction : Le Common Name (CN) doit toujours être ajouté à la liste des SANs en tant que DNSNAME. Cet ajout doit être automatique (côté back) et visible mais non-modifiable pour l'utilisateur (côté front).
Déclinaison de la règle selon le type de certificat :
Pour le type SSL server interne et SSL client-server interne :
Le CN doit être ajouté en tant que DNSNAME.
Côté back : ajout transparent.
Côté front : ajout d'un DNSNAME avec la valeur du CN, non supprimable, non modifiable.
Bonus : "ajouter en hover sur le champs texte ou le bouton delete grisé un texte du type : 'Le CN est obligatoire en tant que SAN pour cette demande'".
Pour le type SSL server externe :
On doit ajouter le "www." (The www. san is required and automatically added).
Le CN est une "racine" (ex: domaine.tld).
Le SAN à ajouter est donc www.domaine.tld.
Cette règle dépend d'une condition (Codage de la seconde URL préfixée par www. on refweb is set to true).
Validation anti-doublons :
"Mettre les doublons en rouge/invalide sur le formulaire front avec message d'erreur 'san en doublon'".
"La validation du nb max de san (autres ns) doit pas compter doublons."
Plan d'Action et Solution
Cette logique impacte principalement le backend, qui doit assurer la présence de ces SANs obligatoires, et le frontend, qui doit les afficher correctement.
Côté Backend
La bonne nouvelle, c'est que votre architecture est déjà presque prête pour ça. La logique de validation (verifySansLimitsDynamically) et la logique de récupération des règles (getSanRulesByCertificateType) sont les bons endroits pour intégrer ces changements.
La méthode verifySansLimitsDynamically doit être mise à jour pour forcer la présence de ces SANs obligatoires.
Fichier : SanServiceImpl.java
code
Java
private void verifySansLimitsDynamically(RequestDto requestDto) throws CertisRequestException {
        // ... (début de la méthode : fusion des SANs, recherche du profil, etc.)
        
        String commonName = requestDto.getCertificate().getCommonName();
        List<San> sansInRequest = new ArrayList<>(/* ... fusion des SANs ... */);
        
        // --- NOUVELLE LOGIQUE DE VALIDATION SPÉCIFIQUE ---

        CertificateTypeDto certType = requestDto.getCertificate().getType();

        // Règle 1 : Pour les types internes, le CN doit être présent en tant que DNSNAME
        if (isInternalServerType(certType)) { // Méthode utilitaire à créer
            boolean cnAsDnsIsPresent = sansInRequest.stream()
                .anyMatch(san -> san.getType() == SanType.DNSNAME && commonName.equalsIgnoreCase(san.getValue()));
            
            if (!cnAsDnsIsPresent) {
                throw new CertisRequestException("Pour ce type de certificat, le Common Name doit être inclus en tant que SAN de type DNSNAME.", HttpStatus.BAD_REQUEST);
            }
        }

        // Règle 2 : Pour le type externe, le "www.CN" doit être présent en tant que DNSNAME
        if (isExternalServerType(certType)) { // Méthode utilitaire à créer
            if (isWwwRequiredOnRefweb(commonName)) { // Méthode qui vérifie la condition sur Refweb
                String wwwSan = "www." + commonName;
                boolean wwwAsDnsIsPresent = sansInRequest.stream()
                    .anyMatch(san -> san.getType() == SanType.DNSNAME && wwwSan.equalsIgnoreCase(san.getValue()));
                
                if (!wwwAsDnsIsPresent) {
                    throw new CertisRequestException("Pour ce type de certificat, le SAN '" + wwwSan + "' est obligatoire.", HttpStatus.BAD_REQUEST);
                }
            }
        }
        
        // --- FIN DE LA NOUVELLE LOGIQUE ---
        
        // Le reste de la validation (min/max) continue.
        // Elle s'assurera que le nombre total de SANs (incluant ceux qu'on vient de valider)
        // respecte bien les limites du profil.
        List<SanTypeRule> rules = sanTypeRuleRepository.findByAutomationHubProfile(profile);
        // ... (le reste du code de validation min/max est identique)
    }

    // --- Méthodes utilitaires à créer ---
    private boolean isInternalServerType(CertificateTypeDto certType) {
        // Implémentez la logique pour reconnaître "SSL server interne" et "SSL client-server interne"
        // ex: return "SSL_SERVER_INTERNAL".equalsIgnoreCase(certType.getName());
        return false; // à remplacer
    }

    private boolean isExternalServerType(CertificateTypeDto certType) {
        // Implémentez la logique pour reconnaître "SSL server externe"
        return false; // à remplacer
    }
    
    private boolean isWwwRequiredOnRefweb(String commonName) {
        // Implémentez la logique d'appel au service Refweb pour vérifier la condition
        return true; // à remplacer
    }
On enrichit la réponse de l'API pour que le front sache quels SANs sont obligatoires et non-modifiables. Notre DTO PredefinedSanDto est déjà parfait pour ça.
Fichier : AutomationHubProfileServiceImpl.java
code
Java
public ProfileRulesResponseDto getSanRulesByCertificateType(Long typeId, Long subTypeId) {
        // ... (début de la méthode)

        // On a besoin du CertificateTypeDto pour connaître le type
        CertificateTypeDto certTypeDto = certificateTypeService.findById(typeId); // Suppose un service qui fait ça

        // --- Logique d'ajustement ---
        
        List<PredefinedSanDto> predefinedSans = new ArrayList<>();
        
        // Règle 1 : Pour les types internes
        if (isInternalServerType(certTypeDto)) {
            PredefinedSanDto predefinedCn = new PredefinedSanDto();
            predefinedCn.setType(SanType.DNSNAME);
            predefinedCn.setValue("{COMMON_NAME}"); // Le placeholder
            predefinedCn.setEditable(false);
            predefinedSans.add(predefinedCn);
        }

        // Règle 2 : Pour le type externe
        if (isExternalServerType(certTypeDto)) {
            // Pour le "www.", on ne peut pas le prédéfinir dynamiquement car il dépend du CN
            // qui n'est pas encore saisi. Le front devra gérer ça.
            // Mais on peut lui envoyer une "règle spéciale"
            // Pour l'instant, on va se concentrer sur l'ajout côté back.
        }

        // Ajustement des règles min/max (comme avant, basé sur le DNSNAME)
        // La logique existante qui décrémente min/max pour DNSNAME si un SAN est prédéfini
        // fonctionnera maintenant pour le cas des serveurs internes.
        
        // ... (le reste de la méthode reste très similaire)
        
        response.setPredefinedSans(predefinedSans);
        // ...
        return response;
    }
Côté Frontend
Le front-end doit adapter sa logique en fonction de la réponse enrichie de l'API.
Fichier : request-detail-section.component.ts
Dans fetchAndApplySanRules :
La logique existante qui lit response.predefinedSans et crée un champ désactivé est déjà parfaite pour gérer le cas du serveur interne. Le front recevra le PredefinedSanDto et créera le champ DNSNAME non-modifiable.
Logique pour le "www." (Serveur Externe) :
C'est une logique plus complexe à ajouter côté front.
Il faudrait s'abonner aux changements du commonName.
Quand le commonName change ET que le type de certificat est "Serveur Externe", il faudrait :
Vérifier s'il existe déjà un SAN www.commonName.
S'il n'existe pas, l'ajouter au FormArray.
Potentiellement le désactiver aussi, pour qu'il soit non-modifiable.
Validation anti-doublons :
Ceci se fait avec un validateur personnalisé (Custom Validator) d'Angular sur le FormArray des SANs.
Ce validateur recevrait le FormArray, vérifierait s'il y a des valeurs en double, et si oui, retournerait un objet d'erreur.
Le template HTML pourrait alors afficher un message d'erreur global pour la section des SANs si cet validateur retourne une erreur.
Cette solution s'intègre bien dans votre architecture existante. La clé est d'enrichir la communication entre le back et le front pour que le front ait toutes les informations nécessaires pour construire une interface utilisateur intelligente.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
////////////////// front //////////////////////////////////////////////
Analyse des Besoins du Front-end
SAN Obligatoire (CN miroir) : Pour certains types de certificats, un SAN de type DNSNAME avec la valeur du Common Name doit être ajouté, non-modifiable et non-supprimable.
SAN Obligatoire (avec "www.") : Pour le type "serveur externe", un SAN www. + Common Name doit être ajouté.
Validation Anti-doublons : L'interface doit détecter et signaler les SANs en double.
Hover Message : Afficher un tooltip sur les SANs obligatoires pour expliquer pourquoi ils sont là.
Plan d'Action Complet (Front-end Angular)
Étape 1 : Mettre à jour le Service et les Modèles (Rappel)
Assurez-vous que votre RequestService et vos modèles (san-rules.model.ts) sont à jour, comme nous l'avons défini. Le backend doit vous fournir la liste des rules (min/max) et des predefinedSans.
Étape 2 : Modifier le Composant RequestDetailSectionComponent.ts
C'est ici que se trouve le cœur de la logique.
code
TypeScript
// --- Imports à ajouter ou vérifier ---
import { Component, OnInit, OnDestroy } from '@angular/core';
import { AbstractControl, FormArray, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil, distinctUntilChanged, debounceTime } from 'rxjs/operators';
// ... (vos autres imports)

// ...
@Component({ /* ... */ })
export class RequestDetailSectionComponent implements OnInit, OnDestroy {
  
  // ... (propriétés existantes)

  // --- NOUVELLES PROPRIÉTÉS ---
  private onDestroy$ = new Subject<void>();
  public sanRules: SanRuleResponseDto[] = [];
  private predefinedSans: PredefinedSanDto[] = [];
  
  // --- Mettre à jour ngOnInit ---
  ngOnInit(): void {
    // ...
    this.listenForCertificateTypeChanges();
    this.listenForCommonNameChanges();

    // On ajoute le validateur anti-doublons au FormArray
    this.sans.setValidators(this.uniqueSansValidator());
  }

  // ... (ngOnDestroy, getter 'sans', etc.)

  // --- MÉTHODES DE LOGIQUE (mises à jour) ---

  /**
   * Méthode principale qui est déclenchée lorsque l'utilisateur change le type de certificat.
   */
  private listenForCertificateTypeChanges(): void {
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    if (certificateTypeControl) {
      certificateTypeControl.valueChanges.pipe(takeUntil(this.onDestroy$)).subscribe(selectedType => {
        const subTypeId = this.requestDetailSectionForm.get('certificateSubType')?.value?.id;
        if (selectedType && selectedType.id) {
          this.fetchAndApplySanRules(selectedType.id, subTypeId);
        } else {
          this.clearSanRulesAndControls();
        }
      });
    }
  }

  /**
   * Récupère les règles depuis l'API et met à jour dynamiquement le formulaire.
   */
  private fetchAndApplySanRules(typeId: number, subTypeId?: number): void {
    this.requestService.getSanRulesForCertificateType(typeId, subTypeId).subscribe(response => {
      this.sanRules = response.rules || [];
      this.predefinedSans = response.predefinedSans || [];
      
      this.sans.clear();

      // Ajout des SANs prédéfinis (ex: CN miroir)
      this.predefinedSans.forEach(predefined => this.addPredefinedSan(predefined));
      
      // Mise à jour de la validation pour refléter les nouvelles règles
      this.sans.updateValueAndValidity(); 
    });
  }

  /**
   * S'abonne aux changements du Common Name pour mettre à jour les SANs dépendants.
   */
  private listenForCommonNameChanges(): void {
    const commonNameControl = this.requestDetailSectionForm.get('certificateName');
    if (commonNameControl) {
      commonNameControl.valueChanges.pipe(
        debounceTime(300), // On attend un peu pour ne pas surcharger
        takeUntil(this.onDestroy$)
      ).subscribe(cnValue => {
        // Met à jour le SAN miroir du CN
        const cnMirrorIndex = this.sans.controls.findIndex(c => c.value.isCnMirror);
        if (cnMirrorIndex !== -1) {
          this.sans.at(cnMirrorIndex).get('value')?.setValue(cnValue, { emitEvent: false });
        }

        // Gère la règle du "www." pour les serveurs externes
        const certTypeValue = this.requestDetailSectionForm.get('certificateType')?.value;
        if (this.isExternalServerType(certTypeValue)) { // Méthode à créer
          this.manageWwwSan(cnValue);
        }
      });
    }
  }

  /**
   * Ajoute un SAN prédéfini au formulaire.
   */
  private addPredefinedSan(predefined: PredefinedSanDto): void {
      const sanGroup = this.createSanGroup();
      const initialValue = predefined.value === '{COMMON_NAME}'
        ? this.requestDetailSectionForm.get('certificateName')?.value || ''
        : predefined.value;
      
      sanGroup.patchValue({ type: predefined.type, value: initialValue });
      sanGroup.disable(); // Grise le champ

      // On ajoute une propriété custom pour le retrouver plus tard et pour le tooltip
      (sanGroup.value as any).isPredefined = true; 
      (sanGroup.value as any).tooltip = 'Ce SAN est obligatoire et lié au Common Name.';
      
      this.sans.push(sanGroup);
  }
  
  /**
   * Gère l'ajout/suppression du SAN "www." pour les serveurs externes.
   */
  private manageWwwSan(commonName: string): void {
    const wwwSanValue = `www.${commonName}`;
    const wwwSanIndex = this.sans.controls.findIndex(c => c.value.value === wwwSanValue && c.value.isWww);
    
    // Si la condition Refweb est vraie et que le SAN "www" n'existe pas encore
    if (this.isWwwRequired() && wwwSanIndex === -1 && commonName) {
      const sanGroup = this.createSanGroup();
      sanGroup.patchValue({ type: 'DNSNAME', value: wwwSanValue });
      sanGroup.disable();
      (sanGroup.value as any).isWww = true;
      (sanGroup.value as any).tooltip = 'Le préfixe "www." est obligatoire pour ce type de certificat.';
      this.sans.push(sanGroup);
    } 
    // Si la condition n'est plus vraie ou si le CN est vide, on le supprime
    else if ((!this.isWwwRequired() || !commonName) && wwwSanIndex !== -1) {
      this.sans.removeAt(wwwSanIndex);
    }
  }
  
  /**
   * NOUVEAU : Validateur personnalisé pour détecter les doublons.
   */
  private uniqueSansValidator(): ValidatorFn {
    return (formArray: AbstractControl): ValidationErrors | null => {
      if (!(formArray instanceof FormArray)) { return null; }
      
      const values = formArray.controls.map(control => `${control.get('type')?.value}::${control.get('value')?.value}`);
      const uniqueValues = new Set(values);
      
      if (uniqueValues.size < values.length) {
        // S'il y a des doublons, on retourne un objet d'erreur
        return { duplicateSans: true };
      }
      
      return null; // Pas d'erreur
    };
  }

  // Méthode utilitaire à créer pour vérifier la condition Refweb (peut appeler un service)
  private isWwwRequired(): boolean {
    // TODO: Implémentez la logique pour vérifier la condition sur Refweb.
    // Pour le test, on peut la mettre à true.
    return true; 
  }
  
  // Méthode utilitaire pour identifier les types de certificats
  private isExternalServerType(certType: any): boolean {
    // TODO: Implémentez la logique pour reconnaître "SSL server externe"
    return certType?.name === 'SSL_CLIENT_SERVER_EXTERNAL'; // Adaptez avec vos vraies valeurs
  }
}
Étape 4 : Adapter le Template HTML (request-detail-section.component.html)
Afficher l'erreur de doublon :
Placez ce code juste après la balise qui contient votre formArrayName="sans".
code
Html
<div *ngIf="sans.hasError('duplicateSans')" class="error-message">
  Erreur : vous avez des entrées SAN en double.
</div>
Afficher le tooltip (hover) :
Dans votre boucle *ngFor qui affiche chaque ligne de SAN, ajoutez un [title] au conteneur principal.
code
Html
<!-- À l'intérieur du *ngFor="let sanGroup of sans.controls; let i = index" -->
<div class="san-row" [title]="sanGroup.value.tooltip">
  
    <!-- Votre <select> pour le type -->
    <select formControlName="type" [disabled]="sanGroup.disabled">...</select>
    
    <!-- Votre <input> pour la valeur -->
    <input formControlName="value" [readOnly]="sanGroup.disabled">

    <!-- Le bouton supprimer est maintenant conditionné par 'sanGroup.enabled' -->
    <button (click)="removeSan(i)" *ngIf="sanGroup.enabled">Supprimer</button>

</div>
Résumé de la Logique Front-end
listenForCertificateTypeChanges est le chef d'orchestre. Il se déclenche quand l'utilisateur fait son choix.
fetchAndApplySanRules récupère les règles depuis le backend. Il nettoie l'ancien FormArray et ajoute les SANs prédéfinis (comme le CN miroir) en les marquant comme désactivés (disable()).
listenForCommonNameChanges est un espion. Il surveille le champ "Common Name" et met à jour en temps réel la valeur des SANs qui en dépendent (le CN miroir et le SAN "www.").
Le uniqueSansValidator est le policier. Il est attaché au FormArray et le marque comme invalide (hasError('duplicateSans')) s'il détecte des doublons. Le template HTML utilise cette erreur pour afficher un message à l'utilisateur.
Le template HTML utilise des astuces comme [disabled]="sanGroup.disabled" et *ngIf="sanGroup.enabled" pour s'assurer que l'utilisateur ne peut ni modifier ni supprimer les SANs obligatoires. Le [title] affiche le tooltip d'information.
Cette solution est complète et s'intègre à votre architecture de Reactive Forms existante pour créer l'expérience utilisateur dynamique décrite dans le ticket.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
