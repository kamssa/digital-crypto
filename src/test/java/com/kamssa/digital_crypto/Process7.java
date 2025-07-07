private void createIncidentAndLogResult(AutomationHubCertificateLightDto dto, String referenceRefDto, AtomicInteger successCounter, AtomicInteger errorCounter, List<String> report, List<String> noSuppReport, List<String> errorReport) {
    try {
        AutoItemTaskDto autoItemTaskDto = itemTaskService.createIncidentAutoEnroll(dto, referenceRefDto, PRIORITYP3, IncTypeEnum.AUTOENROLL);
        successCounter.incrementAndGet();
        String commonName = dto.getCommonName();
        LOGGER.info("Incident {} créé avec succès pour l'automationHubId: {}", autoItemTaskDto.getId(), dto.getAutomationHubId());
        report.add(dto.getAutomationHubId() + " ; " + autoItemTaskDto.getId() + " ; " + commonName);
    } catch (NoSupportGroupException e) {
        LOGGER.error("Aucun groupe de support trouvé pour l'automationHubId: {}", dto.getAutomationHubId(), e);
        noSuppReport.add(dto.getAutomationHubId());
        errorCounter.incrementAndGet();
    } catch (CreateIncidentException e) {
        LOGGER.error("Échec de la création de l'incident pour {}: {}", dto.getAutomationHubId(), e.getMessage());
        String warningInfo = dto.getAutomationHubId() + " ; " + dto.getCommonName();
        errorReport.add(warningInfo);
        errorCounter.incrementAndGet();
    }
}

/**
 * MÉTHODE CENTRALISÉE POUR L'ENVOI DE L'EMAIL
 * (Cette méthode reste également la même)
 */
private void sendFinalReport(int successCount, int errorCount, List<String> report, List<String> noSuppReport, List<String> errorReport) {
    if (successCount > 0 || errorCount > 0 || !noSuppReport.isEmpty()) {
        Map<String, Object> data = new HashMap<>();
        data.put("report", report);
        data.put("error", errorReport);
        data.put("noSupp", noSuppReport);
        try {
            List<String> toList = ... ; 
            sendMailUtils.sendEmail("/template/report-incident-p3.vm", data, toList, "Report | Incident P3");
            LOGGER.info("Email de rapport de traitement des certificats envoyé.");
        } catch (Exception e) {
            LOGGER.error("Échec de l'envoi de l'email de synthèse.", e);
        }
    } else {
        LOGGER.info("Traitement terminé. Aucune action à rapporter.");
    }
}
/////////////////////////////////////////////
private void processCertificates(List<AutomationHubCertificateLightDto> hubCertificateDtos) {
    // Étape 1 : Initialisation des compteurs et des listes pour le rapport
    AtomicInteger successCounter = new AtomicInteger(0);
    AtomicInteger errorCounter = new AtomicInteger(0);
    List<String> report = new ArrayList<>();
    List<String> noSuppReport = new ArrayList<>();
    List<String> errorReport = new ArrayList<>();

    hubCertificateDtos.forEach(hubCertificateLightDto -> {
        try {
            // Étape 2 : Vérifications initiales (environnement PROD, etc.)
            String environment = this.getLabelsByKey(hubCertificateLightDto, "environment");
            String codeAp = this.getLabelsByKey(hubCertificateLightDto, "APcode");
            if (environment == null || codeAp == null || codeAp.trim().isEmpty() || !"PROD".equals(environment)) {
                return; // On ignore ce certificat, il n'est pas dans le périmètre
            }

            // Étape 3 : Vérifier si un incident existe déjà pour éviter les doublons
            // Votre logique de recherche d'incident existant
            AutoItemTaskDtoImpl existingIncident = ... ; 
            if (existingIncident != null) {
                LOGGER.info("Incident {} déjà existant pour automationHubId: {}. Aucune action.", existingIncident.getSysId(), hubCertificateLightDto.getAutomationHubId());
                return;
            }

            // Étape 4 : La condition principale est la date d'expiration
            if (isCertificateExpiringSoon(hubCertificateLightDto)) {
                // Le certificat est sur le point d'expirer, il FAUT créer un incident.
                LOGGER.warn("Le certificat {} va bientôt expirer. Tentative de création d'un incident.", hubCertificateLightDto.getAutomationHubId());

                String referenceRefDto = "..."; // Votre logique pour obtenir la référence
                
                // Appel à la méthode centralisée pour créer l'incident et gérer le résultat
                this.createIncidentAndLogResult(hubCertificateLightDto, referenceRefDto, successCounter, errorCounter, report, noSuppReport, errorReport);
            
            } else {
                // Si le certificat n'est pas sur le point d'expirer, tout va bien.
                LOGGER.info("Le certificat {} n'est pas encore expiré. Aucune action requise.", hubCertificateLightDto.getAutomationHubId());
            }

        } catch (Exception e) {
            // Gestion d'une erreur inattendue pour UN SEUL certificat
            // Cela permet au reste de la liste d'être traité.
            LOGGER.error("Erreur de traitement pour automationHubId {}: {}", hubCertificateLightDto.getAutomationHubId(), e.getMessage(), e);
            errorReport.add(hubCertificateLightDto.getAutomationHubId() + " ; " + e.getMessage());
            errorCounter.incrementAndGet();
        }
    }); // Fin de la boucle forEach

    // Étape 5 : Envoi de l'email de rapport FINAL, une seule fois à la fin.
    this.sendFinalReport(successCounter.get(), errorCounter.get(), report, noSuppReport, errorReport);
    
    LOGGER.info(String.format("Traitement des certificats terminé : OK : %d || ERREUR : %d", successCounter.get(), errorCounter.get()));
}


/**
 * Vérifie si un certificat va expirer bientôt (ex: dans les 3 jours).
 */
private boolean isCertificateExpiringSoon(AutomationHubCertificateLightDto dto) {
    if (dto.getExpiryDate() == null) {
        LOGGER.warn("La date d'expiration est manquante pour AutomationId: {}. Impossible de vérifier.", dto.getAutomationHubId());
        return false;
    }
    // Retourne vrai si la date d'expiration est dans moins de 3 jours
    return dto.getExpiryDate().compareTo(DateUtils.addDays(new Date(), 3)) < 0;
}


// VOS MÉTHODES HELPER (elles sont déjà bonnes, ne les changez pas)

private void createIncidentAndLogResult(...) {
    // ... votre code existant pour cette méthode
}

private void sendFinalReport(...) {
    // ... votre code existant pour cette méthode
}