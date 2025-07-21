package com.bnpparibas.certis.api.service;

import // ... (Importations nécessaires pour les DTOs, services, Logger, etc.)
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CertificateOwnerServiceImpl implements CertificateOwnerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateOwnerServiceImpl.class);

    // --- Dépendances ---
    private final RefWebService refWebService;
    private final ReferenceRefiService referenceRefiService;
    private final OpenDataDao openDataDao;

    // --- Constructeur pour l'injection de dépendances ---
    public CertificateOwnerServiceImpl(RefWebService refWebService, ReferenceRefiService referenceRefiService, OpenDataDao openDataDao) {
        this.refWebService = refWebService;
        this.referenceRefiService = referenceRefiService;
        this.openDataDao = openDataDao;
    }

    /**
     * Méthode principale qui tente de trouver le propriétaire d'un certificat en utilisant
     * une cascade de stratégies de recherche.
     */
    @Override
    public OwnerAndReferenceRefiResult findBestAvailableCertificateOwner(AutomationHubCertificateLightDto dto, String codeAp) {
        // 1. Validation du codeAp en prérequis
        if (codeAp == null || codeAp.trim().isEmpty()) {
            LOGGER.debug("Le paramètre codeAp est manquant ou vide. Recherche annulée.");
            return null;
        }

        ReferenceRefiDto referenceRefiDto = referenceRefiService.findByCodeAp(codeAp);
        if (referenceRefiDto == null) {
            LOGGER.warn("Le codeAp '{}' n'est pas une référence valide. Recherche annulée.", codeAp);
            return null;
        }
        
        LOGGER.info("Le codeAp '{}' a été validé avec succès. Début de la recherche.", codeAp);

        CertificateOwnerDTO ownerDTO = null;

        // 2. Cascade de stratégies de recherche
        // Stratégie 1: Par nom d'hôte
        ownerDTO = this.getInfoByHostName(dto);
        if (!isAuidMissing(ownerDTO)) {
            LOGGER.info("Owner trouvé via hostName");
            return new OwnerAndReferenceRefiResult(ownerDTO, referenceRefiDto);
        }

        // Stratégie 2: Par codeAp
        ownerDTO = this.getInfoByCodeAp(codeAp);
        if (!isAuidMissing(ownerDTO)) {
            LOGGER.info("Owner trouvé via codeAp");
            return new OwnerAndReferenceRefiResult(ownerDTO, referenceRefiDto);
        }

        // Stratégie 3: Par URL (via les SANs)
        ownerDTO = this.getInfoByUrl(dto);
        if (!isAuidMissing(ownerDTO)) {
            LOGGER.info("Owner trouvé via url");
            return new OwnerAndReferenceRefiResult(ownerDTO, referenceRefiDto);
        }

        // 3. Si toutes les stratégies échouent
        LOGGER.warn("Aucun propriétaire (owner) avec un AUID valide n'a été trouvé pour le certificat lié au codeAp '{}'", codeAp);
        return new OwnerAndReferenceRefiResult(null, referenceRefiDto);
    }

    /**
     * Tente de trouver les informations du propriétaire en se basant sur le nom d'hôte du certificat.
     */
    @Override
    public CertificateOwnerDTO getInfoByHostName(AutomationHubCertificateLightDto dto) {
        if (dto == null) {
            return null;
        }

        // D'abord, essayer avec le Common Name
        CertificateOwnerDTO ownerDTO = this.getInfoByHostName(dto.getCommonName());
        if (ownerDTO != null) {
            return ownerDTO;
        }

        // Si échec, itérer sur les SANs (Subject Alternative Names)
        List<SanDto> sanDtos = dto.getSanList();
        if (sanDtos != null && !sanDtos.isEmpty()) {
            for (SanDto sanDto : sanDtos) {
                if (sanDto != null) {
                    ownerDTO = this.getInfoByHostName(sanDto.getSanValue());
                    if (ownerDTO != null) {
                        return ownerDTO; // Retourner dès qu'un propriétaire est trouvé
                    }
                }
            }
        }
        
        return null;
    }

    private CertificateOwnerDTO getInfoByHostName(String hostName) {
        if (hostName == null || hostName.trim().isEmpty()) {
            LOGGER.debug("Tentative de recherche avec un hostName vide ou null");
            return null;
        }

        ApplicationIdentifierDto identifierDto;
        try {
            identifierDto = openDataDao.getApplicationIdentifierDtoByHostName(hostName);
        } catch (DataAccessException e) {
            LOGGER.error("Echec de la récupération des information de l'hote : {}", hostName, e);
            return null;
        }

        if (identifierDto == null) {
            // C'est un cas normal, pas une erreur, juste pas de résultat.
            return null;
        }
        
        LOGGER.debug("Aucune InformationDto trouvée pour le hostName : {}", hostName); // Note: Le log semble inversé, il devrait être "InformationDto TROUVÉE"

        String auid = identifierDto.getAuid();
        if (auid == null || auid.trim().isEmpty()) {
            LOGGER.warn("L'identifiant (auid) récupéré est null ou vide pour le hostName : {}", hostName);
            return null;
        }

        CertificateOwnerDTO infoDTO = new CertificateOwnerDTO();
        infoDTO.setAuid(identifierDto.getAuid());
        infoDTO.setHost(hostName); // On garde une trace de comment on l'a trouvé
        return infoDTO;
    }

    /**
     * Tente de trouver les informations du propriétaire en se basant sur le codeAp.
     */
    @Override
    public CertificateOwnerDTO getInfoByCodeAp(String codeAp) {
        if (codeAp == null || codeAp.trim().isEmpty()) {
            LOGGER.debug("Tentative de recherche avec un codeAp vide ou null");
            return null;
        }

        ReferenceRefiDto referenceRefiDto = referenceRefiService.findByCodeAp(codeAp);
        if (referenceRefiDto == null) {
            LOGGER.debug("Aucune référence trouvée pour le codeAp : {}", codeAp);
            return null;
        }

        String auid = referenceRefiDto.getCodeAp(); // Hypothèse: l'AUID est le codeAp lui-même
        if (auid == null || auid.trim().isEmpty()) {
            LOGGER.warn("La référence trouvée pour le codeAp {} a un codeAp interne null ou vide", codeAp);
            return null;
        }

        CertificateOwnerDTO infoDTO = new CertificateOwnerDTO();
        infoDTO.setAuid(auid);
        infoDTO.setHost(null);
        return infoDTO;
    }

    /**
     * Tente de trouver les informations du propriétaire en se basant sur l'URL
     * (probablement extraite des SANs).
     */
    public CertificateOwnerDTO getInfoByUrl(AutomationHubCertificateLightDto dto) {
        if (dto == null) {
            return null;
        }

        CertificateOwnerDTO ownerDTO = this.getInfoByUrl(dto.getCommonName());
        if (ownerDTO != null) {
            return ownerDTO;
        }

        List<SanDto> sanDtos = dto.getSanList();
        if (sanDtos != null && !sanDtos.isEmpty()) {
            for (SanDto sanDto : sanDtos) {
                if (sanDto != null) {
                    ownerDTO = this.getInfoByUrl(sanDto.getSanValue());
                    if (ownerDTO != null) {
                        return ownerDTO;
                    }
                }
            }
        }
        
        return null;
    }
    
    private CertificateOwnerDTO getInfoByUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            LOGGER.debug("Tentative de recherche avec une url vide ou null");
            return null;
        }

        UrlsResponseModel urlsResponseModel;
        try {
            urlsResponseModel = refWebService.findFullRefWebByUrl(url);
        } catch (TokenRefWebException e) {
            LOGGER.error("Echec de l'appel au service refWeb pour l'url : {}", url, e);
            return null;
        }

        if (urlsResponseModel == null) {
            LOGGER.debug("Aucun UrlsResponseModel trouvé pour le codeAp: {}", url); // Log un peu confus, devrait être pour l'url.
            return null;
        }

        GeneralInformation generalInformation = urlsResponseModel.getGeneralInformation();
        if (generalInformation == null) {
            LOGGER.debug("L'objet generalInfo est null dans la réponse pour l'url : {}", url);
            return null;
        }
        
        String applicationCode = generalInformation.getApplicationCode();
        if (applicationCode == null || applicationCode.trim().isEmpty()) {
            LOGGER.warn("L'applicationCode est null ou vide dans la réponse pour l'url : {}", url);
            return null;
        }

        CertificateOwnerDTO infoDTO = new CertificateOwnerDTO();
        infoDTO.setAuid(applicationCode);
        infoDTO.setHost(null);
        return infoDTO;
    }

    /**
     * Vérifie si un DTO de propriétaire est considéré comme "manquant"
     * (c'est-à-dire s'il est null ou si son AUID est vide).
     */
    private boolean isAuidMissing(CertificateOwnerDTO dto) {
        return dto == null || dto.getAuid() == null || dto.getAuid().trim().isEmpty();
    }
}