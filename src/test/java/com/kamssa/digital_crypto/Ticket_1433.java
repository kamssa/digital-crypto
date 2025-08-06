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