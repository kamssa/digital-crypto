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