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