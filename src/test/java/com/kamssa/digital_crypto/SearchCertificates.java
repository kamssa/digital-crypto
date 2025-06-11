✅ Ce que tu veux faire
Tu veux injecter des modules et des owners dans la requête de recherche de certificats pour retrouver ceux liés à des modules comme "est" ou "acme" et des owners comme "CIS-mTLS-EST-Enrollment", etc.

📍 Où ajouter les filtres ?
Dans le code que tu montres, les critères sont ajoutés dans une List<SearchCriterion> appelée criterionList.

Actuellement, tu as déjà :

Un critère sur MODULE

Un critère sur STATUS

Des critères sur des dates (VALIDITY_DATE)

✏️ Ce que tu dois faire
Tu peux ajouter un critère supplémentaire de type SearchTextCriterion sur le champ OWNER.

Exemple (à adapter à ton code) :
java
Copier
Modifier
criterionList.add(new SearchTextCriterion(
    SearchCertificateFieldEnum.OWNER,
    Arrays.asList(
        "CIS-mTLS-EST-Enrollment", "USR_APP_MF", "WLC-EST-Enrollment", 
        "ansible-hzn-key", "bp2i-cm-issuer", "datalab-cmissuer",
        "est-enroll", "est-enroll-AP10981", "usr_mk_AP26882", 
        "usr_mk_AP138375", "est-enroll-AP11564", "di-CARDIF-AP19531"
    ),
    SearchCriterionTextOperatorEnum.IN
));
🧠 Astuce : Liste plus propre
Tu peux déclarer la liste dans une variable pour plus de lisibilité :

java
Copier
Modifier
List<String> ownerList = Arrays.asList(
    "CIS-mTLS-EST-Enrollment", "USR_APP_MF", "WLC-EST-Enrollment", 
    "ansible-hzn-key", "bp2i-cm-issuer", "datalab-cmissuer", 
    "est-enroll", "est-enroll-AP10981", "usr_mk_AP26882", 
    "usr_mk_AP138375", "est-enroll-AP11564", "di-CARDIF-AP19531"
);

criterionList.add(new SearchTextCriterion(
    SearchCertificateFieldEnum.OWNER,
    ownerList,
    SearchCriterionTextOperatorEnum.IN
));
🧪 Ensuite ?
Tu passes criterionList dans ton SearchCertificateRequestDto, ce qui est déjà fait ici :

java
Copier
Modifier
searchCertificateRequestDto = new SearchCertificateDto(criterionList);
Et le service searchCertificates(...) devrait te renvoyer les certificats correspondants.

Souhaite-tu que je te reformule tout le bloc else complet avec l'ajout de ce critère si tu veux le coller directement dans ton code ?







Vous avez dit :
Souhaite-tu que je te reformule tout le bloc else complet avec l'ajout de ce critère si tu veux le coller directement dans ton code ?


ChatGPT a dit :
Parfait ! Voici le bloc else entièrement reformulé avec l'ajout du critère sur les owners :

java
Copier
Modifier
} else {
    List<SearchCriterion> criterionList = new ArrayList<>();

    // Critère sur les modules
    criterionList.add(new SearchTextCriterion(
        SearchCertificateFieldEnum.MODULE,
        Arrays.asList(
            ModuleEnum.DISCOVERY.getValue(),
            ModuleEnum.INTUNE.getValue(),
            ModuleEnum.INTUNEPEKCS.getValue(),
            ModuleEnum.MF.getValue(),
            ModuleEnum.WCCE.getValue(),
            ModuleEnum.WEBRA.getValue()
        ),
        SearchCriterionTextOperatorEnum.IN
    ));

    // Critère sur le statut (ex : VALID)
    criterionList.add(new SearchTextCriterion(
        SearchCertificateFieldEnum.STATUS,
        CertificateStatusEnum.VALID.getValue(),
        SearchCriterionTextOperatorEnum.EQUALS
    ));

    // Critère sur la date de validité après dateMoins1AutoEnroll
    criterionList.add(new SearchDateCriterion(
        SearchCertificateFieldEnum.VALIDITY_DATE,
        dateMoins1AutoEnroll,
        SearchCriterionDateOperatorEnum.AFTER
    ));

    // Critère sur la date de validité avant util.getDate()
    criterionList.add(new SearchDateCriterion(
        SearchCertificateFieldEnum.VALIDITY_DATE,
        util.getDate(),
        SearchCriterionDateOperatorEnum.BEFORE
    ));

    // 🔥 Nouveau critère ajouté : filtre sur les owners
    List<String> ownerList = Arrays.asList(
        "CIS-mTLS-EST-Enrollment", "USR_APP_MF", "WLC-EST-Enrollment",
        "ansible-hzn-key", "bp2i-cm-issuer", "datalab-cmissuer",
        "est-enroll", "est-enroll-AP10981", "usr_mk_AP26882",
        "usr_mk_AP138375", "est-enroll-AP11564", "di-CARDIF-AP19531"
    );

    criterionList.add(new SearchTextCriterion(
        SearchCertificateFieldEnum.OWNER,
        ownerList,
        SearchCriterionTextOperatorEnum.IN
    ));

    searchCertificateRequestDto = new SearchCertificateDto(criterionList);
}
✅ Ce bloc ajoute :

Tes modules déjà utilisés

Ton critère de statut et de date

Et surtout : le critère OWNER IN (...) avec tous les owners que tu m’as montrés

Tu peux maintenant lancer ta recherche et récupérer uniquement les certificats qui correspondent à ces owners.

Dis-moi si tu veux aussi ajouter le profile in [...] mentionné plus tôt dans ta capture !







Vous avez dit :
Tu peux maintenant lancer ta recherche et récupérer uniquement les certificats qui correspondent à ces


…owners, aux modules spécifiés, au statut VALID, et à la plage de dates de validité définie. 🚀
En résumé, avec ce bloc de code, tu filtres les certificats qui :

Appartiennent à l’un des modules comme DISCOVERY, INTUNE, MF, etc.

Ont un statut VALID

Ont une date de validité :

après dateMoins1AutoEnroll

avant util.getDate()

Ont un owner parmi la liste définie (comme "CIS-mTLS-EST-Enrollment", "est-enroll", etc.)

🔄 Et si tu veux aussi filtrer par profile ?
Tu peux simplement ajouter un critère en plus :

java
Copier
Modifier
List<String> profileList = Arrays.asList(
    "PRO-EST-Appli-SSL-Client",
    "PRO-EST-clientServ-App"
);

criterionList.add(new SearchTextCriterion(
    SearchCertificateFieldEnum.PROFILE,
    profileList,
    SearchCriterionTextOperatorEnum.IN
));
Ajoute ça juste après le critère OWNER si nécessaire.