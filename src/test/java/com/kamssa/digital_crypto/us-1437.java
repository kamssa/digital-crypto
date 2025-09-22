pour ce profil en base (comme le fait la task de 1259)."
Traduction :
Si une demande de certificat échoue à cause d'une validation de SANs, cela signifie que nos règles en base de données sont peut-être obsolètes. Il y a eu une "dérive de configuration" : quelqu'un a modifié les règles sur Horizon, mais notre tâche de synchronisation nocturne n'a pas encore tourné.
Au lieu d'attendre la nuit suivante, nous devons déclencher une synchronisation immédiate et ciblée pour le profil qui a causé l'erreur.
La deuxième partie du ticket est une optimisation :
"> Si erreur sur un profil alors relancer la tache de mise a jour (soit tous les profils ou juste sur le profil en question et"
"soit tous les profils" : Facile à faire, mais moins performant.
"ou juste sur le profil en question" : C'est la meilleure approche, la plus ciblée et la plus efficace. C'est celle que nous allons implémenter.
Plan d'Action et Solution
Heureusement, vous avez déjà fait 95% du travail. Toute la logique de synchronisation pour un profil unique existe déjà. Il nous suffit de l'exposer d'une manière différente et de l'appeler au bon moment.
Étape 1 : Exposer la logique de synchronisation d'un seul profil
Votre service AutomationHubProfileServiceImpl a une méthode privée processSingleProfile. Nous allons simplement créer une nouvelle méthode publique qui l'appelle.
Fichier à modifier : AutomationHubProfileServiceImpl.java
code
Java
// Dans AutomationHubProfileServiceImpl.java

@Service
public class AutomationHubProfileServiceImpl implements AutomationHubProfileService {

    // ... (dépendances et méthodes existantes)

    // --- NOUVELLE MÉTHODE PUBLIQUE ---

    /**
     * Déclenche la synchronisation immédiate des règles de SANs pour un seul profil.
     * Cette méthode peut être appelée lorsqu'une dérive de configuration est détectée.
     *
     * @param profileName Le nom du profil à synchroniser.
     */
    @Override
    @Transactional
    public void syncSingleProfileSanRules(String profileName) {
        LOGGER.info("Déclenchement de la synchronisation à la demande pour le profil : {}", profileName);
        
        // 1. On récupère l'entité de profil de notre base locale.
        AutomationHubProfile internalProfile = automationHubProfileDao.findByProfileName(profileName)
                .orElse(null); // On ne lève pas d'erreur si le profil est inconnu

        if (internalProfile == null) {
            LOGGER.error("Impossible de lancer la synchronisation pour le profil '{}' car il est inconnu dans la table AUTOMATIONHUB_PROFILE.", profileName);
            // On pourrait décider de quand même essayer de l'appeler sur Horizon,
            // mais il est plus sûr de se baser sur nos profils connus.
            return;
        }

        // 2. On appelle notre méthode privée existante qui contient toute la logique.
        // On la met dans un try-catch pour s'assurer que l'appelant ne reçoit pas d'exception inattendue.
        try {
            this.processSingleProfile(internalProfile);
            LOGGER.info("Synchronisation à la demande pour le profil '{}' terminée avec succès.", profileName);
        } catch (Exception e) {
            LOGGER.error("Échec de la synchronisation à la demande pour le profil '{}'.", profileName, e);
            // On ne propage pas l'exception pour ne pas interrompre le flux qui a appelé cette méthode.
        }
    }


    // La méthode syncAllSanRulesFromHorizonApi() reste la même.
    // La méthode privée processSingleProfile(AutomationHubProfile internalProfile) reste la même.
}
N'oubliez pas d'ajouter la signature void syncSingleProfileSanRules(String profileName); à votre interface AutomationHubProfileService.java.
Étape 2 : Appeler cette nouvelle méthode au bon moment
C'est l'étape la plus importante. Il faut identifier l'endroit dans votre code où une demande est rejetée à cause d'une erreur de validation de SANs.
Cet endroit est très probablement là où vous appelez sanService.validateSansPerRequest(requestDto);. C'est probablement dans RequestServiceImpl.
Fichier à modifier : RequestServiceImpl.java
code
Java
// Dans RequestServiceImpl.java

@Service
public class RequestServiceImpl implements RequestService {

    // ... (dépendances existantes)
    private final SanService sanService;
    private final AutomationHubProfileService automationHubProfileService; // Assurez-vous d'injecter ce service

    // ... (constructeur mis à jour)

    @Override
    public RequestDto createRequest(RequestDto requestDto) throws Exception {
        
        // ... (votre code existant)

        // --- DÉBUT DE LA MODIFICATION ---
        
        try {
            // On appelle la validation comme avant.
            sanService.validateSansPerRequest(requestDto);
            
        } catch (CertisRequestException e) {
            // On intercepte spécifiquement l'exception de validation.
            
            // On vérifie si l'erreur est bien liée à une validation de SANs (en se basant sur les clés d'erreur).
            // C'est une bonne pratique pour ne pas déclencher la synchro pour d'autres types d'erreurs.
            String errorCode = e.getMsgidx(); // Suppose que votre exception contient une clé d'erreur
            if (errorCode != null && errorCode.startsWith("error.san.")) {
                
                // Si c'est une erreur de SAN, on déclenche la synchronisation en arrière-plan.
                LOGGER.warn("Une erreur de validation de SANs a été détectée pour le profil '{}'. " + 
                          "Déclenchement d'une synchronisation immédiate des règles.", 
                          requestDto.getCertificate().getType().getName());
                
                try {
                    // On doit trouver le nom du profil technique.
                    Long typeId = requestDto.getCertificate().getType().getId();
                    Long subTypeId = (requestDto.getCertificate().getSubType() != null) ? requestDto.getCertificate().getSubType().getId() : null;
                    
                    AutomationHubProfile profileToSync = automationHubProfileService.findProfileEntityByTypeAndSubType(typeId, subTypeId);
                    
                    // On appelle notre nouvelle méthode de synchronisation ciblée.
                    automationHubProfileService.syncSingleProfileSanRules(profileToSync.getProfileName());

                } catch (Exception syncEx) {
                    // On loggue une erreur si la "traduction" du profil échoue, mais on ne bloque pas.
                    LOGGER.error("Impossible de trouver le nom du profil technique pour lancer la synchronisation.", syncEx);
                }
            }
            
            // Quoi qu'il arrive, on repropage l'exception de validation originale
            // pour que l'utilisateur soit bien notifié que sa demande a échoué.
            throw e;
        }

        // --- FIN DE LA MODIFICATION ---
        
        // ... (le reste de votre logique de création de requête)
    }
}
Explication du Flux Final
Un utilisateur soumet une demande avec un nombre incorrect de SANs.
La méthode createRequest dans RequestServiceImpl est appelée.
Elle appelle sanService.validateSansPerRequest(...).
La validation échoue et lève une CertisRequestException avec un code d'erreur comme error.san.max.violation.
Le bloc try-catch dans createRequest intercepte cette exception.
Il vérifie que le code d'erreur commence bien par error.san..
Si c'est le cas, il utilise automationHubProfileService pour trouver le nom du profil technique (ex: "SSL_SRVR").
Il appelle la nouvelle méthode automationHubProfileService.syncSingleProfileSanRules("SSL_SRVR").
Cette méthode va contacter l'API d'Horizon, récupérer les nouvelles règles pour "SSL_SRVR" et mettre à jour la base de données.
Très important : Le catch repropage (throw e;) l'exception originale. Cela garantit que le processus de création de la demande est bien arrêté et que l'utilisateur reçoit le message d'erreur lui disant que sa demande est invalide.
La prochaine fois qu'un utilisateur essaiera de faire une demande pour ce profil, la validation se fera contre les règles fraîchement mises à jour. C'est un système d'auto-correction très efficace.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
