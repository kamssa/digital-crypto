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