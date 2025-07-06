private void processCertificates(List<AutomationHubCertificateLightDto> hubCertificateDtos, List<String> toList) {
    hubCertificateDtos.forEach(automationHubCertificateLightDto -> {
        String environment = this.getLabelsByKey(automationHubCertificateLightDto, "environment");
        String codeAp = this.getLabelsByKey(automationHubCertificateLightDto, "APcode");

        if (environment == null || environment.trim().isEmpty() || environment.equals("PROD")) {
            if (codeAp != null && !codeAp.trim().isEmpty()) {
                try {
                    LOGGER.info("Traitement automationHubId: {}", automationHubCertificateLightDto.getAutomationHubId());

                    // Vérifie s’il existe déjà un incident
                    AutoItsmTaskDtoImpl existingIncident = (AutoItsmTaskDtoImpl) itsmTaskService.findByAutomationHubLightDto(automationHubCertificateLightDto);
                    SnowIncidentReadResponseDto snowIncident = existingIncident == null ? null : snowService.getSnowIncidentBySysId(existingIncident.getSysId());

                    if (snowIncident != null) {
                        LOGGER.info("Incident déjà existant pour automationHubId: {}", automationHubCertificateLightDto.getAutomationHubId());
                        return;
                    }

                    // Vérification des CertificateOwnerDTOs
                    CertificateOwnerDTO dto1 = certificateOwnerService.getInfoByCodeAp(automationHubCertificateLightDto);
                    CertificateOwnerDTO dto2 = certificateOwnerService.getInfoByUrl(automationHubCertificateLightDto);
                    CertificateOwnerDTO dto3 = certificateOwnerService.getInfoByHostName(automationHubCertificateLightDto);

                    boolean notFoundInAll =
                        (dto1 == null || dto1.getAuId() == null) &&
                        (dto2 == null || dto2.getAuId() == null) &&
                        (dto3 == null || dto3.getAuId() == null);

                    if (notFoundInAll) {
                        // Envoi d’un email d’alerte
                        mailService.sendMissingCodeApAlert(codeAp, automationHubCertificateLightDto.getAutomationHubId());

                        // Création d’un incident P2 ou P3 selon la date
                        LocalDate expirationDate = automationHubCertificateLightDto.getValidUntil().toLocalDate();
                        LocalDate today = LocalDate.now();

                        PriorityEnum priority = expirationDate.compareTo(today.plusDays(3)) == 0 ? PRIORITYP2 : PRIORITYP3;

                        itsmTaskService.createIncidentAutoEnroll(
                            automationHubCertificateLightDto,
                            priority,
                            IncTypeEnum.AUTENROLL
                        );
                        LOGGER.info("Incident {} créé pour codeAp inconnu {}", priority, codeAp);
                    }

                } catch (Exception e) {
                    LOGGER.error("Erreur traitement automationHubId {} : {}", automationHubCertificateLightDto.getAutomationHubId(), e.getMessage(), e);
                }
            }
        }
    });
}
public void sendMissingCodeApAlert(String codeAp, String automationHubId) {
    String subject = "[ALERTE] Code AP non trouvé dans les CertificateOwnerDTO";
    String body = "Le codeAp '" + codeAp + "' n’a été trouvé dans aucun des DTO (CodeAp, URL, Hostname).\nAutomationHubId : " + automationHubId;
    emailService.send("support@tonentreprise.com", subject, body);
}
