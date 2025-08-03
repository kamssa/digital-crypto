 // =================================================================================
  //     ▼▼▼   AJOUTEZ CETTE NOUVELLE MÉTHODE DE CHARGEMENT   ▼▼▼
  // =================================================================================
  private loadRequestData(): void {
    // Ce code est déplacé depuis ngOnInit
    const id = this.id; // On utilise this.id qui est déjà défini
    const isUserCertificate = this.isUserCertificateQueryParam; // On utilise la propriété

    this.CERTIFICATE_FILE_BY_REQUEST_ID = this.api.buildUrl(Global.WS_DOWNLOAD_FILE_REQUEST, { id }) + '?filetype=certificate';
    this.CSR_FILE_BY_REQUEST_ID = this.api.buildUrl(Global.WS_DOWNLOAD_FILE_REQUEST, { id });

    this.getRequest(isUserCertificate).subscribe(
      requestJson => {
        this.certificateRequest = CertificateRequest.fromJSON(requestJson);
        this.isRequestOwner = (this.certificateRequest.creator === this.currentUserID);

        this.api_lookUpFor(this.certificateRequest.creator).subscribe(user => {
          this.createdByUser = user;
        });

        if (this.certificateRequest.takenBy) {
          this.api_lookUpFor(this.certificateRequest.takenBy).subscribe(user => {
            this.takenByUser = user;
          });
        }

        this.api_lookUpRef(this.certificateRequest.application.code).subscribe(application => {
          this.certificateRequest.application.name = application.name;
        });

        this.requestService.validateCommonNameInRefWeb(this.certificateRequest.id).subscribe(
          (data: RequestRefWebValidation) => {
            // ... votre logique switch ...
          },
          err => {
            this.refwebStatus.checked = true;
          }
        );

        this.api_getFile(this.certificateRequest.id, FileType.ARCHIVE).subscribe(
          res => {
            this.hasArchive = true;
          },
          err => {
            this.hasArchive = false;
            if (err.status === 200) {
              this.hasArchive = true;
            }
          }
        );

        this.initCertificateExtensionDropdown();
        this.translate.onLangChange.pipe(takeUntil(this.destroy$)).subscribe(() => this.initCertificateExtensionDropdown());
        this.formConstraintService.setCertStateFromCertificateRequest(this.certificateRequest);

        const editOptions = [
          this.actionsService.canEdit(this.certificateRequest, this.type),
          this.actionsService.canUpdateInformations(this.certificateRequest, this.type),
          this.actionsService.canEditContacts(this.certificateRequest, this.type),
        ];
        for (let i = 0; i < editOptions.length; i++) {
          if (editOptions[i]) {
            this.nrEditDropdowns++;
          }
        }
        this.initEditLabelDropdown();
      },
      err => {
        if (err.status) {
          this.router.navigate(['/pageNotFound']);
        }
      }
    );
  }
// ... fin de la méthode loadRequestData()
  }

  // =================================================================================
  //     ▼▼▼   AJOUTEZ LES MÉTHODES POUR GÉRER LE DIALOGUE ICI   ▼▼▼
  // =================================================================================
  
  public showUpdateDialog(): void {
    this.displayUpdateModal = true;
  }

  public onUpdateSuccess(): void {
    this.displayUpdateModal = false;
    console.log("Mise à jour réussie. Rafraîchissement des données...");
    // On rappelle notre nouvelle méthode pour rafraîchir l'UI sans recharger la page.
    this.loadRequestData();
  }
  ngOnInit() {
  // ...
  this.loadRequestData(); // Appel n°1
}

onUpdateSuccess() {
  // ...
  this.loadRequestData(); // Appel n°2
}
//////////////////// code complet ///////////////////////////
import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { combineLatest } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
// ... et tous vos autres imports (services, modèles, MenuItem, etc.)

@Component({
  selector: 'app-certificate-details',
  templateUrl: './certificate-details.component.html',
  styleUrls: ['./certificate-details.component.scss']
})
export class CertificateDetailsComponent extends SubscribeAware implements OnInit {

  // =======================================================
  //     ▼▼▼   1. PROPRIÉTÉS AJOUTÉES POUR LA NOUVELLE FONCTIONNALITÉ   ▼▼▼
  // =======================================================
  public displayUpdateModal = false;
  private isUserCertificateQueryParam = false;

  // -- Début des propriétés existantes reconstituées --
  id: number;
  certificateRequest: CertificateRequest = new CertificateRequest();
  constraint: FormConstraint = Global.DEFAULT_CONSTRAINTS;

  CERTIFICATE_FILE_BY_REQUEST_ID: string;
  CSR_FILE_BY_REQUEST_ID: string;
  
  STATUS = Status;
  certificateExtensionItems: MenuItem[] = [];
  itsmTasks: ItsmTask[] = [];
  isRequestOwner = false;
  
  showLicenseNumModal = false;
  licenseNumberText: string = null;
  
  cancelRevokeRequest: any = {};
  type: string;
  isAdminView = false;
  isUserAdmin = false;
  isCertDownloadMenuOpen = false;
  revokeRequest: { id?: number, action?: 'ACCEPT' | 'REJECT' } = {};
  FILE_TYPE = FileType;
  extensionDownload: string;

  currentUserID: string;
  permissions: {
    canRead: boolean, canEdit: boolean, canComment: boolean, canRefuse: boolean,
    canValidate: boolean, canRevoke: boolean, canUpload: boolean, canView: boolean
  } = { /* ... initialisé à false ... */ };

  passwordCrypted = true;
  password: string = null;
  askPassphrase = false;
  hasArchive = true;
  
  public isEditDropdownShow = false;
  public nrEditDropdowns: number = 0;
  public labelEditDropdown: string;
  public iconEditDropdown: string;
  passphrase = '';

  canPurge: boolean;
  createdByUser: userInfo = { refoguid: '', firstname: '', lastname: '' };
  takenByUser: userInfo = { refoguid: '', firstname: '', lastname: '' };
  refwebStatus = { checked: false, exists: false, active: false };
  request: CertificateRequest = new CertificateRequest();

  askInfoDisplay = false;
  askInfoObj = { id: 0, question: '' };
  headerShowHistory: string;
  infoShowHistory: string;
  isInfo: boolean = false;
  
  @Input() motif: string;
  @Input() status: string;
  isInfoBoolean: boolean = false;

  constructor(
    private translate: TranslateService,
    private formConstraintService: FormConstraintService,
    private api: ApiService,
    private requestService: RequestService,
    private route: ActivatedRoute,
    private router: Router,
    private jurisdictionService: JurisdictionService,
    private messageService: MessageService,
    private dataFeedingService: DataFeedingService,
    private actionsService: ActionsService,
    private statusChangeService: StatusChangeService
  ) {
    super();
  }

  // =======================================================
  //     ▼▼▼   2. ngOnInit MODIFIÉ POUR ÊTRE PLUS LISIBLE ET RÉUTILISABLE   ▼▼▼
  // =======================================================
  ngOnInit() {
    this.currentUserID = this.jurisdictionService.refoguid;
    this.isUserAdmin = this.jurisdictionService.isAdmin();

    this.route.data.pipe(combineLatest(this.route.params, this.route.queryParams)).subscribe(([routeData, params, queryParams]) => {
      this.type = routeData.type;
      this.id = params['id'];
      this.isUserCertificateQueryParam = queryParams['userCertificate'] || false;
      this.isAdminView = this.type === 'admin' || this.type === 'waf';

      let serviceName = Global.SERVICE_REQUEST;
      switch (this.type) {
        case 'admin': serviceName = Global.SERVICE_REQUESTS_TO_BE_PROCESSED; break;
        case 'waf': serviceName = Global.SERVICE_WAF_REQUESTS; break;
      }

      this.permissions.canRead = this.jurisdictionService.canRead(serviceName);
      // ... autres permissions ...
      this.permissions.canView = this.jurisdictionService.canView(serviceName);

      this.loadRequestData();

      this.requestService.getTasks(this.id).subscribe(
        tasks => { if (tasks && tasks.length > 0) this.itsmTasks = tasks; },
        err => console.error(err)
      );

      this.formConstraintService.getConstraint().subscribe(constraint => {
        this.constraint = constraint;
      });

      this.api_get<any>(Global.PROPERTIES_FILE).subscribe(props => {
        this.canPurge = props.purge.canPurge;
      });
    });
  }
  
  // =================================================================================
  //     ▼▼▼   3. NOUVELLE MÉTHODE PRIVÉE POUR CENTRALISER LE CHARGEMENT DES DONNÉES   ▼▼▼
  // =================================================================================
  private loadRequestData(): void {
    this.CERTIFICATE_FILE_BY_REQUEST_ID = this.api.buildUrl(Global.WS_DOWNLOAD_FILE_REQUEST, { id: this.id }) + '?filetype=certificate';
    this.CSR_FILE_BY_REQUEST_ID = this.api.buildUrl(Global.WS_DOWNLOAD_FILE_REQUEST, { id: this.id });

    this.getRequest(this.isUserCertificateQueryParam).subscribe(
      requestJson => {
        this.certificateRequest = CertificateRequest.fromJSON(requestJson);
        this.isRequestOwner = (this.certificateRequest.creator.refoguid === this.currentUserID);

        this.api_lookUpFor(this.certificateRequest.creator).subscribe(user => this.createdByUser = user);
        if (this.certificateRequest.takenBy) {
          this.api_lookUpFor(this.certificateRequest.takenBy).subscribe(user => this.takenByUser = user);
        }
        this.api_lookUpRef(this.certificateRequest.application.code).subscribe(application => this.certificateRequest.application.name = application.name);

        this.requestService.validateCommonNameInRefWeb(this.certificateRequest.id).subscribe(
          (data: RequestRefWebValidation) => { /* ... votre logique switch ... */ },
          () => this.refwebStatus.checked = true
        );

        this.api_getFile(this.certificateRequest.id, FileType.ARCHIVE).subscribe(
          () => this.hasArchive = true,
          err => { if (err.status !== 200) this.hasArchive = false; }
        );

        // CORRECTION DE LA FAUTE DE FRAPPE ICI
        this.initCertificatExtenisonDropdown();
        this.translate.onLangChange.pipe(takeUntil(this.destroy$)).subscribe(() => this.initCertificatExtenisonDropdown());
        this.formConstraintService.setCertStateFromCertificateRequest(this.certificateRequest);

        const editOptions = [
          this.actionsService.canEdit(this.certificateRequest, this.type),
          this.actionsService.canUpdateInformations(this.certificateRequest, this.type),
          this.actionsService.canEditContacts(this.certificateRequest, this.type),
        ];
        this.nrEditDropdowns = editOptions.filter(opt => opt).length;
        this.initEditLabelDropdown();
      },
      err => {
        if (err.status) this.router.navigate(['/pageNotFound']);
      }
    );
  }

  // =================================================================================
  //     ▼▼▼   4. NOUVELLES MÉTHODES PUBLIQUES POUR GÉRER LE DIALOGUE   ▼▼▼
  // =================================================================================
  public showUpdateDialog(): void {
    this.displayUpdateModal = true;
  }

  public onUpdateSuccess(): void {
    this.displayUpdateModal = false;
    console.log("Mise à jour réussie. Rafraîchissement des données...");
    this.loadRequestData();
  }

  // -- Début des méthodes existantes reconstituées --
  
  back() {
    let route = localStorage.getItem('paginator.route');
    let advancedSearch = localStorage.getItem('paginator.advancedSearch');
    route = advancedSearch == 'YES' ? 'advancedSearch' : route;
    this.router.navigate(['/' + route]);
  }

  getShowableFiles() {
    return this.certificateRequest.files.filter(f => f.filetype !== FileType.licence);
  }

  sendLicenseNumber() {
    this.requestService.addLicenseNumber(this.certificateRequest.id, { numberlicense: this.licenseNumberText }).subscribe(
      () => {
        this.messageService.success({ message: this.translate.instant('certificateInformationSection.successMessages.resendlicencenumber') });
        this.certificateRequest.certificate.numberLicense = this.licenseNumberText;
        this.closeLicenseModal();
      },
      () => this.messageService.fail({ message: this.translate.instant('certificateInformationSection.failureMessages.resendlicencenumber') })
    );
  }

  closeLicenseModal() {
    this.showLicenseNumModal = false;
    this.licenseNumberText = null;
  }
  
  // ... et toutes les autres méthodes de votre fichier ...
  // isGPG, extention, showPassphrase, etc., jusqu'à la fin de la classe.
  
  private getUserCertificate(isUserCertificate: boolean): Observable<CertificateRequest> {
    if (isUserCertificate) {
      return this.requestService.getUserCertificateRequest(this.id);
    }
    return this.requestService.getRequest(this.id, this.type);
  }

  // CORRECTION DE LA FAUTE DE FRAPPE DANS LE NOM DE LA MÉTHODE
  private initCertificatExtenisonDropdown() {
    this.certificateExtensionItems = [];
    Object.keys(CertificateFileExtension)
      .filter(key => this.excludeP7BCertificateExtension(key))
      .map(key => ({
        label: this.translate.instant('CertificateFileExtension.' + CertificateFileExtension[key]),
        icon: 'fa fa-download',
        title: CertificateFileExtension[key]
      }))
      .forEach(menuItem => this.certificateExtensionItems.push(menuItem));
  }
  
  private excludeP7BCertificateExtension(key: string): boolean {
    const certificateFileExtension = CertificateFileExtension[key];
    return !(certificateFileExtension === CertificateFileExtension.P7B && (this.certificateRequest.nature === 'USER' || this.certificateRequest.certificate.usage === USAGE.EXTERNAL));
  }
}
