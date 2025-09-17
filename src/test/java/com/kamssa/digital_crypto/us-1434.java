Étape 1 : Mettre à jour les dépendances de SanServiceImpl
Pour que le service puisse extraire et décoder le CSR, il a besoin de nouveaux outils.
Fichier à modifier : SanServiceImpl.java
Action : Injecter FileManageService et CertificateCsrDecoder via le constructeur.
code
Java
// Dans SanServiceImpl.java

@Service
public class SanServiceImpl implements SanService {
    // ... (dépendances existantes comme automationHubProfileService, sanTypeRuleRepository...)

    // --- NOUVELLES DÉPENDANCES à ajouter ---
    private final FileManageService fileManageService;
    private final CertificateCsrDecoder csrDecoder;

    // --- METTRE À JOUR LE CONSTRUCTEUR ---
    public SanServiceImpl(AutomationHubProfileService automationHubProfileService, 
                          SanTypeRuleRepository sanTypeRuleRepository,
                          FileManageService fileManageService, // Ajouter
                          CertificateCsrDecoder csrDecoder,   // Ajouter
                          /* ... autres dépendances ... */) {
        // ... (assignations existantes)
        this.fileManageService = fileManageService;
        this.csrDecoder = csrDecoder;
    }
    
    // ...
}
Explication : On donne au SanServiceImpl les capacités techniques pour lire les fichiers et décoder les CSRs, ce qui le rend autonome.
Étape 2 : Remplacer l'ancienne logique de validation
Le point d'entrée de la validation, validateSansPerRequest, doit être simplifié pour appeler notre nouvelle méthode.
Fichier à modifier : SanServiceImpl.java
Action : Nettoyer la méthode validateSansPerRequest et supprimer tout l'ancien code (constantes et méthodes de validation statiques).
code
Java
// Dans SanServiceImpl.java

@Service
public class SanServiceImpl implements SanService {
    // ... (dépendances et constructeur mis à jour)

    // ===============================================================================
    // --- PARTIE À SUPPRIMER ---
    // Supprimez toutes les anciennes constantes de limites (ex: INT_SSL_SRVR_LIMIT)
    // et les anciennes méthodes de validation (ex: verifySansLimitForInternalCertificates).
    // ===============================================================================


    /**
     * Méthode principale de validation, maintenant simplifiée.
     * Elle orchestre les différentes étapes de validation des SANs.
     */
    @Override
    public void validateSansPerRequest(RequestDto requestDto) throws CertisRequestException {
        // Cette méthode devient le chef d'orchestre

        // 1. Validation dynamique des limites (min/max/types autorisés)
        // C'est notre nouvelle méthode autonome.
        this.verifySansLimitsDynamically(requestDto);

        // 2. Conserver les autres validations nécessaires qui ne sont pas liées aux limites.
        // Par exemple, la validation du format, ou des vérifications sur Refweb.
        // this.verifySanFormats(requestDto);
        // this.validateSansOnRefweb(requestDto);
    }
    
    // ... (la suite du code est juste en dessous)
}
Explication : On fait le ménage. L'ancienne logique complexe est remplacée par un appel unique à notre nouvelle méthode intelligente.
Étape 3 : Le Cœur de la Solution - La méthode verifySansLimitsDynamically
Voici le code complet de la nouvelle méthode autonome, prête à être copiée-collée dans SanServiceImpl.
Fichier à modifier : SanServiceImpl.java
Action : Ajouter cette nouvelle méthode privée.
code
Java
/**
     * Valide les SANs d'une requête en récupérant et fusionnant les SANs
     * depuis la requête elle-même ET depuis le fichier CSR, puis en les comparant
     * aux règles dynamiques stockées en base de données.
     *
     * @param requestDto L'objet contenant la demande de certificat à valider.
     * @throws CertisRequestException Si une règle de validation est violée.
     */
    private void verifySansLimitsDynamically(RequestDto requestDto) throws CertisRequestException {
        
        // --- PARTIE 1 : VÉRIFICATIONS PRÉLIMINAIRES ---
        if (requestDto.getCertificate() == null || requestDto.getCertificate().getType() == null) {
            // Pas de type de certificat, on ne peut pas trouver les règles.
            // On suppose qu'une autre validation gère ce cas d'erreur.
            return; 
        }

        // --- PARTIE 2 : RÉCUPÉRATION ET FUSION DE TOUS LES SANS ---
        LOGGER.debug("Début de la récupération et fusion des SANs depuis la requête et le CSR.");
        Set<San> finalUniqueSans = new LinkedHashSet<>();

        // 2a. Récupérer les SANs saisis directement par l'utilisateur dans la requête.
        if (requestDto.getCertificate().getSans() != null) {
            finalUniqueSans.addAll(requestDto.getCertificate().getSans());
        }

        // 2b. Extraire le CSR, le décoder et y récupérer les SANs.
        try {
            String csr = this.fileManageService.extractCsr(requestDto, Boolean.TRUE);
            if (csr != null && !csr.isEmpty()) {
                List<San> sansFromCsr = this.csrDecoder.extractSansWithTypesFromCsr(csr);
                if (sansFromCsr != null) {
                    finalUniqueSans.addAll(sansFromCsr);
                }
            }
        } catch (Exception e) {
            // Si le décodage du CSR échoue, c'est une erreur bloquante.
            LOGGER.error("Erreur lors de l'extraction des SANs depuis le fichier CSR.", e);
            throw new CertisRequestException("Le fichier CSR est invalide ou illisible.", HttpStatus.BAD_REQUEST);
        }
        
        // On convertit notre Set de SANs uniques en une List pour la suite du traitement.
        List<San> sansInRequest = new ArrayList<>(finalUniqueSans);
        LOGGER.info("Validation dynamique effectuée sur un total de {} SANs (fusion de la requête et du CSR).", sansInRequest.size());


        // --- PARTIE 3 : TROUVER LE PROFIL TECHNIQUE CORRESPONDANT ---
        Long typeId = requestDto.getCertificate().getType().getId();
        Long subTypeId = (requestDto.getCertificate().getSubType() != null) ? requestDto.getCertificate().getSubType().getId() : null;

        try {
            // On utilise le service "traducteur" pour trouver le profil technique (AutomationHubProfile)
            // à partir du profil public (CertificateType) choisi par l'utilisateur.
            AutomationHubProfile profile = automationHubProfileService.findProfileEntityByTypeAndSubType(typeId, subTypeId);
            
            // --- PARTIE 4 : CHARGER LES RÈGLES ET APPLIQUER LA VALIDATION ---
            
            // 4a. On charge les règles de notre base de données pour le profil trouvé.
            List<SanTypeRule> rules = sanTypeRuleRepository.findByAutomationHubProfile(profile);
            
            // 4b. On applique la logique de validation (le code que nous avons déjà finalisé ensemble).
            if (rules.isEmpty()) {
                if (!sansInRequest.isEmpty()) {
                    throw new CertisRequestException("error.san.not_allowed_for_profile", new Object[]{profile.getProfileName()}, HttpStatus.BAD_REQUEST);
                }
                return; // Cas valide : pas de règles et pas de SANs.
            }

            Map<SanType, Long> sanCountsByType = sansInRequest.stream()
                    .filter(san -> san.getType() != null)
                    .collect(Collectors.groupingBy(San::getType, Collectors.counting()));

            for (SanType requestedSanType : sanCountsByType.keySet()) {
                if (rules.stream().noneMatch(rule -> rule.getType().equals(requestedSanType))) {
                    throw new CertisRequestException("error.san.type.unauthorized", new Object[]{requestedSanType.name(), profile.getProfileName()}, HttpStatus.BAD_REQUEST);
                }
            }

            for (SanTypeRule rule : rules) {
                long countInRequest = sanCountsByType.getOrDefault(rule.getType(), 0L);
                if (countInRequest > rule.getMaxValue()) {
                    throw new CertisRequestException("error.san.max.violation", new Object[]{rule.getType().name(), rule.getMaxValue()}, HttpStatus.BAD_REQUEST);
                }
                if (countInRequest < rule.getMinValue()) {
                    throw new CertisRequestException("error.san.min.violation", new Object[]{rule.getType().name(), rule.getMinValue()}, HttpStatus.BAD_REQUEST);
                }
            }

        } catch (FailedToRetrieveProfileException e) {
            LOGGER.error("Impossible de mapper le type de certificat ({}/{}) à un profil technique.", typeId, subTypeId, e);
            throw new CertisRequestException("Le profil de certificat sélectionné n'est pas valide ou n'a pas de règles de SANs configurées.", HttpStatus.BAD_REQUEST);
        }
    }
Explication : Cette méthode est maintenant le "couteau suisse" de la validation des SANs. Elle orchestre la récupération des données, leur fusion, la recherche du bon contexte (le profil), et enfin l'application des règles. En la plaçant dans SanServiceImpl, vous respectez bien le principe de responsabilité unique.
//////////////////////////////////////////////////////////////////////////////////
code
Java
// Dans l'interface AutomationHubProfileService

public interface AutomationHubProfileService {
    // ... (vos méthodes existantes comme `getProfileByTypeAndSubType`)

    // --- AJOUTEZ LA SIGNATURE DE LA NOUVELLE MÉTHODE CI-DESSOUS ---

    /**
     * Récupère l'ENTITÉ JPA AutomationHubProfile correspondante pour un type et sous-type de certificat.
     * Cette méthode est utilisée en interne par d'autres services qui ont besoin de l'objet Entité.
     *
     * @param typeId L'ID du CertificateType.
     * @param subTypeId L'ID du CertificateSubType (peut être null).
     * @return L'entité AutomationHubProfile correspondante.
     * @throws FailedToRetrieveProfileException si aucun mapping n'est trouvé en base de données.
     */
    AutomationHubProfile findProfileEntityByTypeAndSubType(Long typeId, Long subTypeId) throws FailedToRetrieveProfileException;
}
Étape 2 : Implémenter la nouvelle méthode dans le Service
Maintenant, ouvrez la classe d'implémentation pour y ajouter le code de cette nouvelle méthode. La logique sera très similaire à celle de getProfileByTypeAndSubType, mais au lieu de retourner un DTO, elle retournera directement l'entité.
Fichier : AutomationHubProfileServiceImpl.java
code
Java
@Service
public class AutomationHubProfileServiceImpl implements AutomationHubProfileService {
    
    // ... (vos dépendances existantes comme certisTypeToAutomationHubProfileDao et automationHubProfileMapper)

    /**
     * Votre méthode existante, qui retourne un DTO. On n'y touche pas.
     */
    @Override
    public AutomationHubProfileDto getProfileByTypeAndSubType(Long typeId, Long subTypeId) throws FailedToRetrieveProfileException {
        AutomationHubProfile automationHubProfile = findProfileEntityByTypeAndSubType(typeId, subTypeId);
        return automationHubProfileMapper.toDto(automationHubProfile);
    }
    
    // --- AJOUTEZ LA NOUVELLE MÉTHODE CI-DESSOUS ---

    /**
     * Implémentation de la nouvelle méthode qui retourne l'entité.
     * Elle utilise le DAO de la table de mapping pour faire la "traduction".
     */
    @Override
    public AutomationHubProfile findProfileEntityByTypeAndSubType(Long typeId, Long subTypeId) throws FailedToRetrieveProfileException {
        
        // On suppose ici que votre DAO "certisTypeToAutomationHubProfileDao"
        // a une méthode pour trouver l'entité de mapping CertisTypeToAutomationHubProfile.
        // Si la méthode n'existe pas, il faudra la créer dans l'interface du DAO.
        
        CertisTypeToAutomationHubProfile mapping = certisTypeToAutomationHubProfileDao
                .findByCertificateTypeIdAndCertificateSubTypeId(typeId, subTypeId)
                .orElseThrow(() -> new FailedToRetrieveProfileException(String.valueOf(typeId), String.valueOf(subTypeId)));
        
        // Une fois qu'on a trouvé l'objet de mapping, on retourne le profil technique qui lui est associé.
        // C'est l'entité que notre SanServiceImpl pourra utiliser.
        return mapping.getAutomationHubProfile();
    }
}
Étape 3 (Si nécessaire) : Créer la méthode dans le DAO
Il est possible que la méthode findByCertificateTypeIdAndCertificateSubTypeId n'existe pas encore dans votre interface CertisTypeToAutomationHubProfileDao. Si c'est le cas, ajoutez-la. Spring Data JPA s'occupera de l'implémenter pour vous.
Fichier : CertisTypeToAutomationHubProfileDao.java
code
Java
// Dans l'interface CertisTypeToAutomationHubProfileDao

public interface CertisTypeToAutomationHubProfileDao extends JpaRepository<CertisTypeToAutomationHubProfile, Long> {
    
    // Spring Data JPA va automatiquement générer la requête SQL pour cette méthode
    // en se basant sur les noms des champs dans l'entité CertisTypeToAutomationHubProfile.
    Optional<CertisTypeToAutomationHubProfile> findByCertificateTypeIdAndCertificateSubTypeId(Long certificateTypeId, Long certificateSubTypeId);

}
Résumé
Vous ajoutez une nouvelle "capacité" à votre AutomationHubProfileService en lui demandant de pouvoir retourner l'entité AutomationHubProfile brute.
Vous implémentez cette capacité en utilisant la logique de mapping qui existe déjà dans votre application.
Votre SanServiceImpl peut maintenant utiliser cette nouvelle capacité pour obtenir l'entité dont il a besoin pour sa validation, sans avoir à connaître les détails complexes du mapping.
C'est une excellente façon de réutiliser le code et de respecter l'architecture de votre projet. Une fois ces modifications faites, l'erreur "cannot find symbol" sur la méthode findProfileEntityByTypeAndSubType disparaîtra.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
