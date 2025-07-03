List<String> envsÀIgnorer = Arrays.asList("dev", "staging", "test");

if (environment != null && !envsÀIgnorer.contains(environment.toLowerCase())) {
    if (codeAp != null && !codeAp.trim().isEmpty()) {
        // Traitement spécial : codeAp existe ET environnement n'est pas ignoré
        System.out.println("Traitement spécial pour codeAp " + codeAp + " en environnement " + environment);
        
        // ... ton traitement ici
        
    } else {
        System.out.println("codeAp manquant pour environnement " + environment);
    }
}
List<String> envsÀIgnorer = Arrays.asList("dev", "staging", "test");

requests.forEach(requestDto -> {
    try {
        // Récupération des infos depuis le certificat
        String codeAp = requestDto.getCertificate().getLabels().get("APcode");
        String environment = requestDto.getCertificate().getLabels().get("environment");

        // Vérification : codeAp présent + environnement non ignoré
        if (codeAp != null && !codeAp.trim().isEmpty()
                && environment != null
                && !envsÀIgnorer.contains(environment.toLowerCase())) {

            // Affectation du codeAp à requestDto (nécessite un setter)
            requestDto.setCodeAp(codeAp);  // <-- assure-toi que cette méthode existe

            // Vérifier s’il y a déjà un incident pour cette requête
            List<ItsmTaskDtoImpl> itsmTaskDtos = itsmTaskService
                .findByIdRequestAndStatusAndTypeAndCreationDate(IncTypeEnum.CERTIS, requestDto.getId());

            if (!CollectionUtils.isEmpty(itsmTaskDtos)) {
                return; // un incident existe déjà
            }

            String commonName = requestDto.getCertificate().getCommonName();
            String warningInfo = requestDto.getId() + " , " + commonName + ", " + requestDto.getCertificate().getCertisEntity().getName();

            ItsmTaskDto itsmTaskDto;
            try {
                // Création de l’incident
                itsmTaskDto = itsmTaskService.createIncident(requestDto, PRIORITY, IncTypeEnum.CERTIS);

                // Envoi d’email (tu dois avoir un service ou méthode pour cela)
                mailService.sendIncidentNotification(requestDto, itsmTaskDto); // <-- à implémenter

            } catch (NoSupportGroupException e) {
                noSuppReport.add(warningInfo);
                LOGGER.error("Support group not found for request {}", requestDto.getId());
                return;
            } catch (CreateIncidentException e) {
                errorReport.add(warningInfo);
                errorCounter.incrementAndGet();
                LOGGER.error("Incident null, failed to create on snow {}", requestDto.getId());
                return;
            }

            successCounter.incrementAndGet();
            LOGGER.info("Incident sent {} {}", requestDto.getId(), itsmTaskDto.getId());
            report.add(requestDto.getId() + "," + itsmTaskDto.getId() + "," + commonName + "," + codeAp);

        } else {
            LOGGER.info("Environnement ignoré ou codeAp manquant pour request {}", requestDto.getId());
        }

    } catch (Exception e) {
        LOGGER.error("Erreur inattendue dans le traitement de la request {}", requestDto.getId(), e);
    }
});
