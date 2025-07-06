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
/////////////////////////////////////////////////////////////////////////////////////////////
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;

// Supposons que ces classes existent
// import com.example.dto.AutomationHubCertificateLightDto;
// import com.example.dto.CertificateOwnerDTO;
// import com.example.service.CertificateOwnerService;
// import com.example.service.ReferenceRefService; // Pour findReferenceByCodeAp
// import com.example.service.ItsmTaskService; // Pour createIncidentAutoEnroll
// import com.example.enums.PRIORITYTYPE; // Si c'est un enum
// import com.example.enums.IncTypeEnum; // Si c'est un enum
// import com.example.exception.NoSupportGroupException; // Si c'est une exception personnalisée
// import com.example.exception.CreateIncidentException; // Si c'est une exception personnalisée


@Service // Ou @Component, ou autre annotation Spring appropriée
public class DiscoveryTaskService { // C'est un exemple, ajustez le nom de la classe
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryTaskService.class);

    @Autowired
    private CertificateOwnerService certificateOwnerService;

    @Autowired
    private ReferenceRefService referenceRefService; // Supposons que ce service existe pour findReferenceByCodeAp

    @Autowired
    private ItsmTaskService itsmTaskService; // Supposons que ce service existe

    // ... d'autres autowired si nécessaire


    /**
     * Méthode privée utilitaire pour vérifier si un CertificateOwnerDTO est valide.
     * Un DTO est considéré valide s'il n'est pas null ET si son auId n'est pas null.
     *
     * @param dto Le CertificateOwnerDTO à vérifier.
     * @return true si le DTO est null ou si son auId est null, false sinon.
     */
    private boolean isAuIdMissing(CertificateOwnerDTO dto) {
        return dto == null || dto.getAuId() == null;
    }

    /**
     * Méthode privée unifiée pour récupérer le CertificateOwnerDTO le plus pertinent.
     * Elle tente de récupérer le DTO par CodeAp, puis par URL, puis par HostName.
     * Elle renvoie le premier DTO trouvé qui a un auId non null, suivant cet ordre de priorité.
     *
     * @param automationHubCertificateLightDto Le DTO léger contenant les informations de recherche.
     * @return Le CertificateOwnerDTO valide trouvé, ou null si aucun n'a pu être trouvé avec un auId non null.
     */
    private CertificateOwnerDTO getBestAvailableCertificateOwner(AutomationHubCertificateLightDto automationHubCertificateLightDto) {
        // 1. Essai de récupération par CodeAp
        CertificateOwnerDTO dtoByCodeAp = certificateOwnerService.getInfoByCodeAp(automationHubCertificateLightDto);
        if (!isAuIdMissing(dtoByCodeAp)) {
            LOGGER.info("CertificateOwnerDTO trouvé via CodeAp avec AuId: {}", dtoByCodeAp.getAuId());
            return dtoByCodeAp;
        } else {
            LOGGER.debug("CertificateOwnerDTO non valide trouvé par CodeAp pour AutomationId: {}", automationHubCertificateLightDto.getAutomationId());
        }

        // 2. Si CodeAp échoue, essai par URL
        CertificateOwnerDTO dtoByUrl = certificateOwnerService.getInfoByUrl(automationHubCertificateLightDto);
        if (!isAuIdMissing(dtoByUrl)) {
            LOGGER.info("CertificateOwnerDTO trouvé via URL avec AuId: {}", dtoByUrl.getAuId());
            return dtoByUrl;
        } else {
            LOGGER.debug("CertificateOwnerDTO non valide trouvé par URL pour AutomationId: {}", automationHubCertificateLightDto.getAutomationId());
        }

        // 3. Si URL échoue, essai par HostName
        CertificateOwnerDTO dtoByHostName = certificateOwnerService.getInfoByHostName(automationHubCertificateLightDto);
        if (!isAuIdMissing(dtoByHostName)) {
            LOGGER.info("CertificateOwnerDTO trouvé via HostName avec AuId: {}", dtoByHostName.getAuId());
            return dtoByHostName;
        } else {
            LOGGER.debug("CertificateOwnerDTO non valide trouvé par HostName pour AutomationId: {}", automationHubCertificateLightDto.getAutomationId());
        }

        // Si aucun n'a été trouvé avec un AuId valide
        LOGGER.warn("Aucun CertificateOwnerDTO valide (AuId manquant) trouvé pour AutomationId: {} après vérification CodeAp, URL, HostName.", automationHubCertificateLightDto.getAutomationId());
        return null;
    }


    // --- Début de la refonte de vos méthodes ---

    // La méthode qui correspond au premier bloc d'image (probablement processAutoEnrollCertificate ou similaire)
    public void processAutoEnrollCertificate(AutomationHubCertificateLightDto automationHubCertificateLightDto) {
        // Anciens appels :
        // CertificateOwnerDTO certificateOwnerDTO = certificateOwnerService.getInfoByCodeAp(automationHubCertificateLightDto);
        // ... (puis d'autres appels pour certificateOwnerDTO1 et certificateOwnerDTO2)

        // Nouvelle logique unifiée
        CertificateOwnerDTO selectedCertificateOwnerDTO = getBestAvailableCertificateOwner(automationHubCertificateLightDto);

        // Si aucun CertificateOwnerDTO valide n'a été trouvé après toutes les tentatives
        if (selectedCertificateOwnerDTO == null) {
            // Le message d'erreur est déjà loggé dans getBestAvailableCertificateOwner
            return; // On arrête le traitement pour ce certificat
        }

        // Le DTO sélectionné est garanti d'être non null et d'avoir un auId non null
        // Vous pouvez maintenant utiliser selectedCertificateOwnerDTO en toute sécurité.
        LOGGER.info("Processing automationHubCertificateLightDto: {} with selected CertificateOwnerDTO AuId: {}",
                    automationHubCertificateLightDto.getAutomationId(), selectedCertificateOwnerDTO.getAuId());

        // Remplacement des anciennes variables par selectedCertificateOwnerDTO
        ReferenceRefIDto referenceRefIDto = referenceRefService.findReferenceByCodeAp(selectedCertificateOwnerDTO.getCodeApp()); // Assurez-vous que selectedCertificateOwnerDTO.getCodeApp() est le bon argument ici.
        if(referenceRefIDto == null){
            LOGGER.warn("Donnée non retrouvée : referenceRefIDto. Pour la récupération du support groupé.");
        }

        String commonName = automationHubCertificateLightDto.getCommonName();
        // Ici, utilisez selectedCertificateOwnerDTO.getAuId()
        String warningInfo = selectedCertificateOwnerDTO.getAuId() + "," + commonName;

        try {
            // Assurez-vous d'avoir les constantes PRIORITYTYPE, IncTypeEnum etc.
            autoItsmTaskDto itsmTaskDto = itsmTaskService.createIncidentAutoEnroll(automationHubCertificateLightDto, referenceRefIDto, PRIORITYTYPE.P3, IncTypeEnum.AutOENROLL);
        } catch (NoSupportGroupException e) {
            // ... Votre logique de gestion d'erreur existante
            // noSuppReport.add(warningInfo); // Si ces listes sont définies dans la portée de cette méthode
            LOGGER.error("Support group not found for request {}: {}", automationHubCertificateLightDto.getAutomationId(), e.getMessage());
            return;
        } catch (CreateIncidentException e) {
            // ... Votre logique de gestion d'erreur existante
            // errorReport.add(warningInfo); // Si ces listes sont définies
            // errorCounter.incrementAndGet(); // Si ces compteurs sont définis
            LOGGER.error("Incident null failed to create on snow {} : {}", automationHubCertificateLightDto.getAutomationId(), e.getMessage());
            return;
        }
    }


    // Refonte de la méthode processCertificateEsp3
    // Adaptez la signature de la méthode si elle a d'autres paramètres ou retourne quelque chose
    public void processCertificateEsp3(AutomationHubCertificateLightDto automationHubCertificateLightDto) {
        // Ancien appel :
        // CertificateOwnerDTO certificateOwnerDTO = certificateOwnerService.getInfoByCodeAp(automationHubCertificateLightDto);
        // if (certificateOwnerDTO.getAuId() == null) { return; }

        // Nouvelle logique unifiée
        CertificateOwnerDTO certificateOwnerDTO = getBestAvailableCertificateOwner(automationHubCertificateLightDto);

        // Si aucun CertificateOwnerDTO valide n'a été trouvé après toutes les tentatives
        if (certificateOwnerDTO == null) {
            // Le message d'erreur est déjà loggé dans getBestAvailableCertificateOwner
            return; // On arrête le traitement pour ce certificat
        }

        // Le DTO `certificateOwnerDTO` est maintenant garanti d'être non null et d'avoir un auId non null
        LOGGER.info("Processing CertificateEsp3 for AutomationId: {} with CertificateOwnerDTO AuId: {}",
                    automationHubCertificateLightDto.getAutomationId(), certificateOwnerDTO.getAuId());

        // ... Le reste de votre logique utilise maintenant `certificateOwnerDTO` qui est fiable.
        // Par exemple :
        // if (codeAp != null && !codeAp.trim().isEmpty()) { ... }
    }


    // Exemple de structure pour processExpireCertificate, ajustez selon votre code réel
    public void processExpireCertificate(List<AutomationHubCertificateLightDto> dtos) {
        // ... (Votre code existant)
        if (!dtos.isEmpty()) {
            List<String> tolist = new ArrayList<>();
            for (AutomationHubCertificateLightDto dto : dtos) {
                // Appel à processCertificateEsp3 pour chaque DTO
                processCertificateEsp3(dto); // Ou une autre méthode qui encapsule la logique d'appel
            }
        }
        // ... (Votre code existant)
    }


    // NOTE: Les classes DTO et Service (AutomationHubCertificateLightDto, CertificateOwnerDTO,
    // CertificateOwnerService, ReferenceRefIDto, ReferenceRefService, ItsmTaskService, autoItsmTaskDto, etc.)
    // doivent être correctement importées ou définies dans votre projet.
}

// ---- Exemples de classes DTO et Service (à adapter selon votre projet) ----

class AutomationHubCertificateLightDto {
    private String automationId;
    private String commonName;
    // ... d'autres champs
    public String getAutomationId() { return automationId; }
    public String getCommonName() { return commonName; }
    // ... getters/setters
    @Override
    public String toString() { return "AutomationHubCertificateLightDto{" + "automationId='" + automationId + '\'' + '}'; }
}

class CertificateOwnerDTO {
    private String auId;
    private String codeApp;
    // ... d'autres champs
    public String getAuId() { return auId; }
    public String getCodeApp() { return codeApp; }
    // ... getters/setters
}

interface CertificateOwnerService {
    CertificateOwnerDTO getInfoByCodeAp(AutomationHubCertificateLightDto dto);
    CertificateOwnerDTO getInfoByUrl(AutomationHubCertificateLightDto dto);
    CertificateOwnerDTO getInfoByHostName(AutomationHubCertificateLightDto dto);
}

// Les autres interfaces/classes comme ReferenceRefIDto, ItsmTaskService, etc.,
// devraient aussi être définies dans votre projet pour que le code compile.