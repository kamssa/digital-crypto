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

/////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&///////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// --- Imports des DTO et Services (à adapter à vos chemins réels) ---
// import com.bnpparibas.certis.api.tasks.dto.AutomationHubCertificateLightDto;
// import com.bnpparibas.certis.api.tasks.dto.CertificateOwnerDTO;
// import com.bnpparibas.certis.api.tasks.dto.ReferenceRefIDto;
// import com.bnpparibas.certis.api.tasks.dto.autoItsmTaskDto;
// import com.bnpparibas.certis.api.tasks.service.CertificateOwnerService;
// import com.bnpparibas.certis.api.tasks.service.ReferenceRefService;
// import com.bnpparibas.certis.api.tasks.service.ItsmTaskService;
// import com.bnpparibas.certis.api.tasks.service.EmailService;
// import com.bnpparibas.certis.api.tasks.exception.NoSupportGroupException;
// import com.bnpparibas.certis.api.tasks.exception.CreateIncidentException;
// import com.bnpparibas.certis.api.tasks.enums.PRIORITYTYPE;
// import com.bnpparibas.certis.api.tasks.enums.IncTypeEnum;
// import com.bnpparibas.certis.api.tasks.utils.DateUtils; // Importez votre DateUtils


// --- EXEMPLES DE CLASSES FACTICES (À REMPLACER PAR VOS VRAIES IMPLÉMENTATIONS) ---
// Si vous avez déjà ces classes, ignorez ces définitions.
class AutomationHubCertificateLightDto {
    private String automationId;
    private String commonName;
    private Date expiryDate;
    private Certificate certificate;
    private String environment;
    public String getAutomationId() { return automationId; }
    public String getCommonName() { return commonName; }
    public Date getExpiryDate() { return expiryDate; }
    public Certificate getCertificate() { return certificate; }
    public String getEnvironment() { return environment; }
    @Override public String toString() { return "AutomationId: " + automationId + ", CommonName: " + commonName; }

    public static class Certificate {
        private String serialNumber;
        public String getSerialNumber() { return serialNumber; }
    }
}

class CertificateOwnerDTO {
    private String auId;
    private String codeApp;
    public String getAuId() { return auId; }
    public String getCodeApp() { return codeApp; }
}

class ReferenceRefIDto { /* ... */ }
class autoItsmTaskDto { /* ... */ }

interface CertificateOwnerService {
    CertificateOwnerDTO getInfoByCodeAp(AutomationHubCertificateLightDto dto);
    CertificateOwnerDTO getInfoByUrl(AutomationHubCertificateLightDto dto);
    CertificateOwnerDTO getInfoByHostName(AutomationHubCertificateLightDto dto);
}

interface ReferenceRefService {
    ReferenceRefIDto findReferenceByCodeAp(String codeAp);
}

interface ItsmTaskService {
    autoItsmTaskDto createIncidentAutoEnroll(AutomationHubCertificateLightDto reqDto, ReferenceRefIDto refDto, PRIORITYTYPE priority, IncTypeEnum incType);
}

interface EmailService {
    void sendAlertEmail(String subject, String body, String recipientEmail);
}

enum PRIORITYTYPE { P2, P3 }
enum IncTypeEnum { AutOENROLL }

class NoSupportGroupException extends RuntimeException { public NoSupportGroupException(String message) { super(message); } }
class CreateIncidentException extends RuntimeException { public CreateIncidentException(String message) { super(message); } }

// *** VOTRE CLASSE DateUtils ***
// C'est un exemple basique. Si vous utilisez Apache Commons Lang DateUtils,
// vous n'aurez pas besoin de cette classe factice.
class DateUtils {
    public static Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days);
        // TRONQUE À MINUIT pour une comparaison de jour exact
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public static Date truncate(Date date, int field) {
        // Cette méthode serait utilisée par Apache Commons DateUtils par exemple.
        // Ici, nous l'incorporons directement dans addDays pour la simplicité.
        // Si vous utilisez une lib, utilisez leur truncate si besoin.
        return date; // Placeholder
    }
}
// --- FIN DES EXEMPLES DE CLASSES FACTICES ---


@Service
public class DiscoveryTaskService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryTaskService.class);

    @Autowired
    private CertificateOwnerService certificateOwnerService;
    @Autowired
    private ReferenceRefService referenceRefService;
    @Autowired
    private ItsmTaskService itsmTaskService;
    @Autowired
    private EmailService emailService;
    // @Autowired // Si DateUtils est un Spring Bean et non une utilitaire statique
    // private DateUtils dateUtils; // DÉCOMMENTEZ SI VOTRE DATEUTILS EST UN BEAN


    // --- Méthodes privées utilitaires (non modifiées par cette requête) ---

    private boolean isAuIdMissing(CertificateOwnerDTO dto) {
        return dto == null || dto.getAuId() == null;
    }

    private CertificateOwnerDTO getBestAvailableCertificateOwner(AutomationHubCertificateLightDto automationHubCertificateLightDto) {
        CertificateOwnerDTO dtoByCodeAp = certificateOwnerService.getInfoByCodeAp(automationHubCertificateLightDto);
        if (!isAuIdMissing(dtoByCodeAp)) {
            LOGGER.debug("CertificateOwnerDTO trouvé via CodeAp avec AuId: {}", dtoByCodeAp.getAuId());
            return dtoByCodeAp;
        } else {
            LOGGER.debug("CertificateOwnerDTO non valide trouvé par CodeAp pour automationId: {}", automationHubCertificateLightDto.getAutomationId());
        }

        CertificateOwnerDTO dtoByUrl = certificateOwnerService.getInfoByUrl(automationHubCertificateLightDto);
        if (!isAuIdMissing(dtoByUrl)) {
            LOGGER.debug("CertificateOwnerDTO trouvé via URL avec AuId: {}", dtoByUrl.getAuId());
            return dtoByUrl;
        } else {
            LOGGER.debug("CertificateOwnerDTO non valide trouvé par URL pour automationId: {}", automationHubCertificateLightDto.getAutomationId());
        }

        CertificateOwnerDTO dtoByHostName = certificateOwnerService.getInfoByHostName(automationHubCertificateLightDto);
        if (!isAuIdMissing(dtoByHostName)) {
            LOGGER.debug("CertificateOwnerDTO trouvé via HostName avec AuId: {}", dtoByHostName.getAuId());
            return dtoByHostName;
        } else {
            LOGGER.debug("CertificateOwnerDTO non valide trouvé par HostName pour automationId: {}", automationHubCertificateLightDto.getAutomationId());
        }

        LOGGER.warn("Aucun CertificateOwnerDTO valide (AuId manquant) trouvé pour automationId: {} après vérification CodeAp, URL, HostName.", automationHubCertificateLightDto.getAutomationId());
        return null;
    }

    // Ancienne méthode private Date addDaysToCurrentDate(int days) a été supprimée ou commentée,
    // car nous utilisons DateUtils.addDays(new Date(), ...) directement comme demandé.

    private void createAndSendIncident(
        AutomationHubCertificateLightDto requestDto,
        String incidentLogMessage,
        PRIORITYTYPE priority,
        CertificateOwnerDTO selectedCertificateOwnerDTO,
        AtomicInteger successCounter,
        AtomicInteger errorCounter,
        List<String> noSuppReport,
        List<String> errorReport
    ) {
        LOGGER.info(incidentLogMessage);

        ReferenceRefIDto referenceRefIDto = null;
        if (selectedCertificateOwnerDTO != null && selectedCertificateOwnerDTO.getCodeApp() != null) {
            referenceRefIDto = referenceRefService.findReferenceByCodeAp(selectedCertificateOwnerDTO.getCodeApp());
        }
        if (referenceRefIDto == null) {
            LOGGER.warn("donnée non retrouvée : referenceRefIDto pour le codeApp: {}. Pour la récupération du support groupé.",
                        selectedCertificateOwnerDTO != null ? selectedCertificateOwnerDTO.getCodeApp() : "N/A");
        }

        String subjectIncident = "Alerte Certificat: " + (requestDto.getCertificate() != null ? requestDto.getCertificate().getSerialNumber() : "N/A");
        // String ipsString = "N/A"; // Si vous avez besoin de cette variable, dérivez-la
        // Date dateExpireDiscovery = requestDto.getExpiryDate(); // Pas directement utilisé ici pour la comparaison, mais pour le log
        // Map<String, String> data = new HashMap<>(); // Si vous collectez des données pour l'incident
        // data.put("dateExpiration", dateExpireDiscovery != null ? dateExpireDiscovery.toString() : "N/A");
        // data.put("serialDiscovery", requestDto.getCertificate() != null ? requestDto.getCertificate().getSerialNumber() : "N/A");


        try {
            autoItsmTaskDto itsmTaskDto = itsmTaskService.createIncidentAutoEnroll(
                requestDto, referenceRefIDto, priority, IncTypeEnum.AutOENROLL
            );
            successCounter.incrementAndGet();
            LOGGER.info("Incident créé avec succès pour AutomationId: {}", requestDto.getAutomationId());
        } catch (NoSupportGroupException e) {
            String warningInfo = selectedCertificateOwnerDTO.getAuId() + "," + requestDto.getCommonName();
            noSuppReport.add(warningInfo);
            errorCounter.incrementAndGet();
            LOGGER.error("Support group not found for request {}: {}. Incident non créé.", requestDto.getAutomationId(), e.getMessage());
            errorReport.add("Support group missing for " + requestDto.getAutomationId() + ": " + e.getMessage());
        } catch (CreateIncidentException e) {
            String warningInfo = selectedCertificateOwnerDTO.getAuId() + "," + requestDto.getCommonName();
            errorReport.add(warningInfo);
            errorCounter.incrementAndGet();
            LOGGER.error("Incident failed to create on snow for {}: {}", requestDto.getAutomationId(), e.getMessage());
            errorReport.add("Incident creation failed for " + requestDto.getAutomationId() + ": " + e.getMessage());
        }
    }


    // --- Refonte des méthodes principales ---

    public void processAutoEnrollCertificate(AutomationHubCertificateLightDto automationHubCertificateLightDto) {
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger errorCounter = new AtomicInteger(0);
        List<String> noSuppReport = new ArrayList<>();
        List<String> errorReport = new ArrayList<>();

        CertificateOwnerDTO selectedCertificateOwnerDTO = getBestAvailableCertificateOwner(automationHubCertificateLightDto);

        if (selectedCertificateOwnerDTO == null) {
            // selectedCertificateOwnerDTO == null : Envoyer un email d’alerte
            String subject = "ALERTE: Propriétaire de Certificat Non Trouvé";
            String body = String.format(
                "Impossible de trouver un propriétaire valide (AuId manquant) pour le certificat avec AutomationId: '%s' et CommonName: '%s'. " +
                "Toutes les sources (CodeAp, URL, HostName) ont été vérifiées et aucun AuId valide n'a été identifié. " +
                "Le traitement Auto-Enroll est stoppé pour ce certificat. Veuillez investiguer.",
                automationHubCertificateLightDto.getAutomationId(),
                automationHubCertificateLightDto.getCommonName()
            );
            emailService.sendAlertEmail(subject, body, "alerte.certificats@votreentreprise.com"); // Adaptez l'adresse
            LOGGER.warn("Alerte email envoyée car aucun CertificateOwnerDTO valide n'a été trouvé pour AutomationId: {}. Traitement stoppé.", automationHubCertificateLightDto.getAutomationId());
            return;
        } else {
            // selectedCertificateOwnerDTO != null : Créer incident P2 ou P3
            LOGGER.info("CertificateOwnerDTO valide trouvé (AuId: {}) pour AutomationId: {}. Procéder à la vérification d'expiration pour incident.",
                        selectedCertificateOwnerDTO.getAuId(), automationHubCertificateLightDto.getAutomationId());

            Date certificateExpiryDate = automationHubCertificateLightDto.getExpiryDate();
            if (certificateExpiryDate == null) {
                LOGGER.warn("La date d'expiration est manquante pour AutomationId: {}. Impossible de créer un incident P2/P3.", automationHubCertificateLightDto.getAutomationId());
                return;
            }

            // --- Utilisation de DateUtils.addDays(new Date(), X) comme demandé ---
            Date today = new Date(); // La date actuelle
            Date threshold3Days = DateUtils.addDays(today, 3);
            Date threshold15Days = DateUtils.addDays(today, 15);

            // Si date d’expiration <= aujourd’hui + 3 jours → créer incident P2
            if (certificateExpiryDate.compareTo(threshold3Days) <= 0) {
                createAndSendIncident(
                    automationHubCertificateLightDto,
                    "Création incident P2 car le certificat certis expire dans moins de 3 jours (ou aujourd'hui). AutomationId: " + automationHubCertificateLightDto.getAutomationId(),
                    PRIORITYTYPE.P2, selectedCertificateOwnerDTO,
                    successCounter, errorCounter, noSuppReport, errorReport
                );
            }
            // Sinon (c'est-à-dire > 3 jours), si date d’expiration <= aujourd’hui + 15 jours → créer incident P3
            else if (certificateExpiryDate.compareTo(threshold15Days) <= 0) {
                createAndSendIncident(
                    automationHubCertificateLightDto,
                    "Création incident P3 car le certificat certis expire dans moins de 15 jours (mais plus de 3). AutomationId: " + automationHubCertificateLightDto.getAutomationId(),
                    PRIORITYTYPE.P3, selectedCertificateOwnerDTO,
                    successCounter, errorCounter, noSuppReport, errorReport
                );
            } else {
                LOGGER.info("Certificat pour AutomationId: {} expire dans plus de 15 jours. Pas d'incident créé pour l'instant (niveau P3 non atteint).", automationHubCertificateLightDto.getAutomationId());
            }
        }
    }

    public void processCertificateEsp3(AutomationHubCertificateLightDto automationHubCertificateLightDto) {
        CertificateOwnerDTO certificateOwnerDTO = getBestAvailableCertificateOwner(automationHubCertificateLightDto);

        if (certificateOwnerDTO == null) {
            LOGGER.warn("Impossible de procéder dans processCertificateEsp3 : aucun CertificateOwnerDTO valide trouvé pour AutomationId: {}.", automationHubCertificateLightDto.getAutomationId());
            return;
        }

        LOGGER.info("Procédure processCertificateEsp3 pour AutomationId: {} avec AuId: {}.",
                    automationHubCertificateLightDto.getAutomationId(), certificateOwnerDTO.getAuId());

        // ... Le reste de votre logique pour processCertificateEsp3 utilise maintenant `certificateOwnerDTO`
    }

    public void processExpireCertificate(List<AutomationHubCertificateLightDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            LOGGER.info("Aucun DTO à traiter dans processExpireCertificate.");
            return;
        }

        LOGGER.info("Début du traitement de {} certificats dans processExpireCertificate.", dtos.size());

        for (AutomationHubCertificateLightDto dto : dtos) {
            try {
                // Appel de la méthode refactorisée pour chaque DTO
                processAutoEnrollCertificate(dto); // Ou processCertificateEsp3(dto) selon votre besoin réel
            } catch (Exception e) {
                LOGGER.error("Erreur inattendue lors du traitement du certificat {}: {}", dto.getAutomationId(), e.getMessage(), e);
            }
        }
        LOGGER.info("Fin du traitement de {} certificats dans processExpireCertificate.", dtos.size());
    }

    // ... d'autres méthodes de votre classe ...
}
////////////////////////////opendata////////////////////////////////////////////////////
@Override
public InformationDto getByCNFromServerClasses(String cn) {
    // 1. Préparer le terme de recherche pour une correspondance partielle (avec jokers)
    String searchLike = "%" + cn + "%";

    // 2. Une liste des classes de serveurs (il serait préférable de la déplacer dans une constante ou un fichier de config)
    String serverClasses = "'cmdb_ci_appliance_server'," +
                           "'cmdb_ci_esx_server'," +
                           "'cmdb_ci_mainframe_lpar'," +
                           "'cmdb_ci_linux_server'," +
                           "'cmdb_ci_win_server'," +
                           "'cmdb_ci_netware_server'," +
                           "'cmdb_ci_server'," +
                           "'cmdb_ci_aix_server'," +
                           "'cmdb_ci_hpux_server'," +
                           "'cmdb_ci_mainframe'," +
                           "'cmdb_ci_solaris_server'," +
                           "'cmdb_ci_unix_server'";

    // 3. Une seule requête SQL combinée et plus efficace
    String sql = "SELECT " +
                 "  ba.sys_id, " +
                 "  ba.name " +
                 "FROM " +
                 "  servicenow.cmdb_ci_all_classes server " + // Alias pour le CI serveur
                 "LEFT JOIN " +
                 "  servicenow.business_app_childs rel ON rel.child_sys_id = server.sys_id " +
                 "LEFT JOIN " +
                 "  servicenow.cmdb_all_classes ba ON rel.be_sys_id = ba.sys_id " + // Alias pour l'application métier
                 "WHERE " +
                 "  server.sys_class_name IN (" + serverClasses + ") " +
                 "  AND (server.name ILIKE ? OR server.fqdn ILIKE ? OR server.host_name ILIKE ?) " +
                 "  AND ba.sys_id IS NOT NULL " + // S'assurer qu'on a bien trouvé une application métier liée
                 "ORDER BY " +
                 "  server.sys_updated_on DESC, ba.sys_id DESC " + // 4. Un tri plus significatif
                 "LIMIT 1";

    try {
        // 5. Utiliser queryForObject qui attend exactement un résultat
        return openDataJdbcTemplate.queryForObject(
            sql,
            new Object[]{searchLike, searchLike, searchLike},
            (rs, rowNum) -> {
                InformationDtoImpl dto = new InformationDtoImpl();
                dto.setSysId(rs.getString("sys_id"));
                dto.setName(rs.getString("name"));
                return dto;
            }
        );
    } catch (EmptyResultDataAccessException e) {
        // 6. Gérer élégamment le cas où aucun résultat n'est trouvé
        LOGGER.info("Liste CMDB vide pour cn: {}", cn);
        return new InformationDtoImpl(); // Retourner un DTO vide
    }
}
//////////////////////////////////////////////////////////oendata 1///////////////////////////////////////
// Variable de classe pour la liste des serveurs, pour éviter de la reconstruire à chaque appel.
// J'ai ajouté 'u_cmdb_ci_appliance_server' qui était dans votre image.
private static final String SERVER_CLASSES = 
    "'u_cmdb_ci_appliance_server'," +
    "'cmdb_ci_osx_server'," +
    "'cmdb_ci_esx_server'," +
    "'cmdb_ci_mainframe_lpar'," +
    "'cmdb_ci_linux_server'," +
    "'cmdb_ci_win_server'," +
    "'cmdb_ci_netware_server'," +
    "'cmdb_ci_server'," +
    "'cmdb_ci_aix_server'," +
    "'cmdb_ci_hpux_server'," +
    "'cmdb_ci_mainframe'," +
    "'cmdb_ci_solaris_server'," +
    "'cmdb_ci_unix_server'";

/**
 * Méthode publique principale qui orchestre la recherche.
 * 1. Trouve le sys_id d'un serveur par son nom.
 * 2. Si trouvé, utilise ce sys_id pour trouver l'application métier parente.
 */
@Override
public InformationDto getByCNFromServerClasses(String cn) {
    // Étape 1 : Appeler la première méthode pour trouver le sys_id du serveur.
    // L'utilisation de Optional est une manière propre de gérer un résultat qui peut être absent.
    Optional<String> serverSysIdOptional = findServerSysIdByName(cn);

    if (!serverSysIdOptional.isPresent()) {
        LOGGER.info("Aucun serveur trouvé pour le CN : {}", cn);
        return new InformationDtoImpl(); // Retourne un DTO vide si aucun serveur n'est trouvé.
    }

    // Un sys_id a été trouvé, on peut passer à l'étape 2.
    String serverSysId = serverSysIdOptional.get();
    LOGGER.info("Serveur trouvé avec sys_id : {}. Recherche de l'application parente...", serverSysId);

    // Étape 2 : Appeler la seconde méthode pour trouver l'application métier parente.
    return findBusinessAppByChildSysId(serverSysId);
}

/**
 * MÉTHODE 1 : Trouve le sys_id d'un serveur correspondant à la première requête de l'image.
 * @param cn Le nom du serveur à rechercher (name).
 * @return Un Optional contenant le sys_id si trouvé, sinon un Optional vide.
 */
private Optional<String> findServerSysIdByName(String cn) {
    String sql = "SELECT sys_id FROM servicenow.cmdb_all_classes " +
                 "WHERE sys_class_name IN (" + SERVER_CLASSES + ") " +
                 "AND name ILIKE ? " + // Utilise ILIKE comme dans l'image (correspondance exacte insensible à la casse)
                 "ORDER BY sys_id ASC LIMIT 1"; // Utilise ASC comme dans l'image, et LIMIT 1 pour être efficace.

    try {
        // queryForObject est idéal pour récupérer une seule valeur d'une seule colonne.
        String sysId = openDataJdbcTemplate.queryForObject(sql, new Object[]{cn}, String.class);
        return Optional.of(sysId);
    } catch (EmptyResultDataAccessException e) {
        // C'est normal si rien n'est trouvé, on retourne simplement un résultat vide.
        return Optional.empty();
    }
}

/**
 * MÉTHODE 2 : Trouve une application métier parente à partir du sys_id de son enfant (le serveur).
 * Correspond à la deuxième requête de l'image.
 * @param childSysId Le sys_id du serveur enfant.
 * @return Un InformationDto rempli si une application parente est trouvée, sinon un DTO vide.
 */
private InformationDto findBusinessAppByChildSysId(String childSysId) {
    // ATTENTION : Notez la correction du nom de la colonne 'rel.ba_sys_id' selon votre image.
    String sql = "SELECT ba.sys_id, ba.name " + // IMPORTANT : On sélectionne les colonnes dont on a besoin !
                 "FROM servicenow.cmdb_all_classes AS ba " +
                 "LEFT JOIN servicenow.business_app_childs AS rel ON rel.ba_sys_id = ba.sys_id " + // 'ba_sys_id' comme dans l'image
                 "WHERE rel.child_sys_id = ? " +
                 "ORDER BY ba.sys_id ASC LIMIT 1";

    try {
        return openDataJdbcTemplate.queryForObject(
            sql,
            new Object[]{childSysId},
            (rs, rowNum) -> {
                InformationDtoImpl dto = new InformationDtoImpl();
                dto.setSysId(rs.getString("sys_id"));
                dto.setName(rs.getString("name"));
                return dto;
            }
        );
    } catch (EmptyResultDataAccessException e) {
        LOGGER.warn("Aucune application métier parente trouvée pour le serveur avec sys_id : {}", childSysId);
        return new InformationDtoImpl(); // Retourne un DTO vide si aucun parent n'est trouvé.
    }
}
//////////////////////////////////////execption ////////////////////////////////
private void createIncidentAndLogResult(...) {
    // ...
    SnowIncidentReadResponseDto snowIncident = null;
    try {
        snowIncident = (existingIncident == null) ? null : snowService.getSnowIncidentBySysId(existingIncident.getSysId());
    } catch (NotFoundIncidentException e) {
        // CE N'EST PAS UNE ERREUR CRITIQUE.
        // Cela signifie simplement que l'incident n'existe pas dans Snow. On peut donc continuer.
        LOGGER.info("L'incident référencé localement n'existe pas/plus dans ServiceNow. On va en créer un nouveau.");
        snowIncident = null; // On s'assure qu'il est bien null pour la suite.
    } catch (MultipleIncidentException e) {
        // CECI EST UNE ERREUR. Il ne devrait pas y avoir plusieurs incidents.
        LOGGER.error("Plusieurs incidents trouvés pour le certificat {}, situation anormale. Aucune création ne sera effectuée.", dto.getAutomationHubId(), e);
        errorCounter.incrementAndGet();
        errorReport.add(dto.getAutomationHubId() + " ; Multiple incidents found");
        return; // On arrête le traitement pour ce certificat.
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
        // CECI EST UNE ERREUR TECHNIQUE GRAVE.
        LOGGER.error("Erreur de sécurité/configuration lors de l'appel à ServiceNow pour le certificat {}", dto.getAutomationHubId(), e);
        errorCounter.incrementAndGet();
        errorReport.add(dto.getAutomationHubId() + " ; Technical security error");
        return; // On arrête le traitement pour ce certificat.
    }
    
    if (snowIncident != null) {
        // ...
        return;
    }

    // ... reste de la logique pour créer l'incident
}
////////////////////////////////////////////////////
Requête 2 : findBusinessAppByChildSysId
Generated sql
SELECT ... FROM ... LEFT JOIN servicenow.business_app_childs AS rel ...
WHERE rel.child_sys_id = ?
Use code with caution.
SQL
Pour que cette requête soit rapide, il est impératif que la colonne rel.child_sys_id dans la table servicenow.business_app_childs soit indexée.
✅ Solution : Vérifier/Ajouter un index.
Assurez-vous qu'un index existe sur cette colonne.
Generated sql
CREATE INDEX idx_business_app_childs_child_sys_id ON servicenow.business_app_childs (child_sys_id);
Use code with caution.
SQL
Comment vérifier ?
Utilisez la commande EXPLAIN ANALYZE de votre base de données devant votre requête. Si vous voyez les mots Seq Scan (Sequential Scan) sur une grande table, c'est le signe qu'un index manque. Vous devriez voir Index Scan.
Solution n°2 : Fusionner les deux requêtes en une seule
Faire deux allers-retours vers la base de données est moins efficace qu'en faire un seul. Vous pouvez combiner les deux recherches en une seule requête SQL complexe qui fait tout le travail.
L'idée est de dire à la base de données : "Trouve-moi l'application métier dont un des enfants est un serveur avec tel nom et telle classe".
✅ Solution : Réécrire la logique avec une seule requête.
Generated java
public InformationDto getByCNFromSercerClasses(String cn) {
    String sql = "SELECT ba.sys_id, ba.name " +
                 "FROM servicenow.cmdb_ci_business_app AS ba " +
                 "JOIN servicenow.business_app_childs AS rel ON rel.ba_sys_id = ba.sys_id " +
                 "JOIN servicenow.cmdb_alt_classes AS server ON rel.child_sys_id = server.sys_id " +
                 "WHERE server.sys_class_name IN (" + SERVER_CLASSES + ") " +
                 "AND lower(server.name) = ? " +
                 "ORDER BY ba.sys_id DESC LIMIT 1";

    try {
        // Le RowMapper est maintenant directement dans la requête
        return openDataJdbcTemplate.queryForObject(sql, new Object[]{cn.toLowerCase()}, (rs, rowNum) -> {
            InformationDtoImpl dto = new InformationDtoImpl();
            dto.setSysId(rs.getString("sys_id"));
            dto.setName(rs.getString("name"));
            return dto;
        });
    } catch (EmptyResultDataAccessException e) {
        LOGGER.warn("Aucune application métier parente trouvée pour un serveur avec le CN : {}", cn);
        return new InformationDtoImpl();
    }
}
Use code with caution.
Java
Avantages :
Un seul appel réseau à la base de données.
Le planificateur de la base de données peut trouver la manière la plus optimale d'exécuter la requête globale.
Le code Java est plus simple.
Note : Cette solution est très efficace, mais elle ne dispense pas d'avoir les bons index (Solution n°1) ! Les deux solutions se complètent.
Solution n°3 : Mettre en place un cache
Si vous appelez souvent cette méthode avec les mêmes noms de serveurs (cn), les résultats ne changent probablement pas toutes les secondes. Mettre en cache le résultat peut considérablement accélérer les appels répétés.
✅ Solution : Utiliser un cache (ex: Spring Cache, Ehcache, Caffeine).
Avec Spring, c'est très simple. Il suffit d'ajouter une annotation.
Activez le cache dans votre configuration Spring (@EnableCaching).
Ajoutez l'annotation @Cacheable à votre méthode.
Generated java
import org.springframework.cache.annotation.Cacheable;

// ...

@Override
@Cacheable(value = "business-apps-by-server-cn", unless="#result.sysId == null")
public InformationDto getByCNFromSercerClasses(String cn) {
    // ... votre code actuel (de préférence celui avec la requête fusionnée)
}
///////////////////////////////indexer dans la base de donnée //////////////////////////////////////////////

C'est la meilleure partie de cette solution : vous n'avez absolument rien à changer dans votre code Java !
Laissez-moi vous expliquer pourquoi.
Les index sont transparents pour l'application
Un index de base de données est une structure de données interne, gérée exclusivement par le système de gestion de base de données (SGBD) lui-même (comme PostgreSQL, Oracle, etc.). Son but est d'accélérer la recherche de données, un peu comme l'index à la fin d'un livre vous permet de trouver rapidement une page sans avoir à lire le livre en entier.
Quand votre application Java envoie une requête SQL comme celle-ci :
Generated sql
SELECT sys_id 
FROM servicenow.cmdb_alt_classes 
WHERE lower(name) = ?
Use code with caution.
SQL
Le processus est le suivant :
Votre code Java envoie la commande SQL textuelle à la base de données. Il ne sait rien des index et n'a pas besoin de le savoir.
La base de données reçoit la requête.
L'optimiseur de requêtes (le "cerveau" de la base de données) analyse la requête.
Il regarde la clause WHERE lower(name) = ?.
Il consulte son catalogue interne et se dit : "Ah, je vois qu'il existe un index nommé idx_cmdb_alt_classes_lower_name qui correspond exactement à l'expression lower(name). C'est parfait !"
L'optimiseur décide d'utiliser cet index pour trouver la ou les lignes correspondantes de manière quasi-instantanée, au lieu de parcourir toute la table.
Il exécute la requête en utilisant ce plan optimisé et renvoie le résultat à votre application Java.
Votre code Java ne fait que poser une question ("Donne-moi les lignes où lower(name) est égal à X"). C'est le travail de la base de données de trouver la manière la plus intelligente et la plus rapide de répondre à cette question. En créant l'index, vous avez simplement donné à la base de données un nouvel outil très puissant pour le faire.
Ce que vous devez faire maintenant
Assurez-vous que l'index a bien été créé. Vous devriez avoir reçu un message de succès après avoir exécuté la commande CREATE INDEX.
Relancez votre test ou votre application. Exécutez à nouveau le processus qui appelle la méthode findServerSysIdByName.
Observez la différence de performance. Le temps de réponse, qui était peut-être de plusieurs secondes (ou dizaines de secondes), devrait maintenant être de quelques millisecondes. La différence devrait être spectaculaire.
Il n'y a aucune modification de code à faire. Le code que vous avez écrit était déjà "correct" d'un point de vue fonctionnel. Le problème était un problème de "configuration de la performance" de la base de données, que vous venez de résoudre.
Si vous aviez créé l'index composite
Même si vous avez créé l'index composite plus avancé :
Generated sql
CREATE INDEX idx_cmdb_alt_classes_lower_name_class 
ON servicenow.cmdb_alt_classes (lower(name), sys_class_name);
Use code with caution.
SQL
La conclusion est la même. Quand l'optimiseur verra votre requête :
Generated sql
... WHERE sys_class_name IN (...) AND lower(name) = ? ...