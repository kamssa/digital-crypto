Absolument. J'ai bien compris votre demande. Vous voulez intégrer la logique de gestion des incidents, mais spécifiquement pour les certificats qui nécessitent une Priorité 2.
Cela signifie que le flux principal décide d'abord que le certificat est suffisamment critique pour une P2, puis il appelle une méthode qui applique la logique de votre image : créer, recréer, mettre à jour ou ignorer l'incident.
Voici comment l'intégrer de la "bonne manière" et de "façon pratique". L'approche la plus propre est de ne pas créer une méthode séparée pour chaque priorité, mais de rendre votre méthode de création/mise à jour plus intelligente en lui passant la priorité en paramètre.
Étape 1 : Déterminer la Priorité dans la Boucle Principale
D'abord, dans votre méthode processCertificates, vous devez décider si un certificat doit avoir une priorité 2 (P2) ou une autre (par exemple, P3). En regardant votre code, cela semble être géré par isExpiringSoon et les constantes PRIORITY2 et PRIORITY3.
Modifions la boucle principale pour qu'elle détermine la priorité, puis appelle la méthode de traitement.
Generated java
// Dans la classe IncidentAutoEnrollTask

// Définissez vos constantes de priorité en haut de la classe
private static final Integer PRIORITY3 = 3;
private static final Integer PRIORITY2 = 2;

// ...

private void processCertificates(List<AutomationHubCertificateLightDto> hubCertificateDtos) {
    // ... (initialisation des compteurs, etc.)

    for (AutomationHubCertificateLightDto automationHubCertificate : hubCertificateDtos) {
        try {
            // ... (logique pour récupérer codeAp, certificateOwnerDTO, etc.)

            // --- DÉTERMINATION DE LA PRIORITÉ ---
            Integer priority;
            if (isExpiringSoon(automationHubCertificate)) {
                // Le certificat expire très bientôt -> PRIORITÉ 2
                priority = PRIORITY2; 
            } else {
                // Le certificat expire dans un futur plus lointain -> PRIORITÉ 3
                priority = PRIORITY3;
            }
            
            // On appelle la méthode de traitement en lui passant la priorité décidée
            this.createIncidentAndLogResult(
                automationHubCertificate,
                referenceRefiDto,
                successCounter, 
                errorCounter, 
                report, 
                noSuppReport, 
                errorReport,
                priority // <-- On passe la priorité ici !
            );

        } catch (Exception e) {
            // ... (gestion des erreurs)
        }
    }
}
Use code with caution.
Java
Étape 2 : Adapter la Méthode createIncidentAndLogResult
Maintenant, nous allons intégrer la logique de votre image dans la méthode createIncidentAndLogResult. Elle va maintenant recevoir la priority et l'utiliser. C'est ici que la logique spécifique à la P2 sera appliquée lorsque priority vaut 2.
Generated java
private AutoItsmTaskDto createIncidentAndLogResult(
    AutomationHubCertificateLightDto automationHubCertificate,
    ReferenceRefiDto referenceRefiDto,
    AtomicInteger successCounter,
    // ... autres paramètres ...
    List<String> noUpdateReport, // Assurez-vous d'avoir cette liste
    List<String> errorReport,
    Integer priority // Le paramètre clé !
) {
    // ... (vérification du groupe de support, etc.)

    SnowIncidentDto existingIncident = snowService.getSnowIncidentBySysId(...);
    String summary = "AutoEnroll pour " + automationHubCertificate.getCommonName();
    AutoItsmTaskDto itsmTaskDto = null;

    // =========================================================================
    //               INTÉGRATION DE VOTRE LOGIQUE SPÉCIFIQUE
    // =========================================================================

    if (existingIncident == null) {
        // Cas 1 : Aucun incident n'existe. On en crée un nouveau avec la priorité donnée.
        LOGGER.info("Aucun incident précédent. Création d'un nouvel incident avec Priorité {}.", priority);
        itsmTaskDto = this.createNewIncident(automationHubCertificate, referenceRefiDto, priority);

    } else {
        // Cas 2 : Un incident existe déjà.
        LOGGER.info("Incident précédent trouvé: {} (État: {}, Priorité: {})", 
            existingIncident.getNumber(), existingIncident.getState(), existingIncident.getPriority());

        if (isIncidentClosedOrResolved(existingIncident)) {
            // Cas 2a : L'incident est clos. On en recrée un nouveau.
            LOGGER.info("L'incident {} est clos. Recréation avec Priorité {}.", existingIncident.getNumber(), priority);
            itsmTaskDto = this.recreateIncident(automationHubCertificate, referenceRefiDto, priority);

        } else if (priority == PRIORITY2 && isIncidentHighPriority(existingIncident, PRIORITY2)) {
            // Cas 2b - LA LOGIQUE SPÉCIFIQUE P2 :
            // On veut créer un P2, mais il existe déjà un incident ouvert qui est P2 ou P1.
            LOGGER.warn("L'incident {} est déjà en haute priorité ({}). Aucune mise à jour effectuée pour la demande P2.", 
                        existingIncident.getNumber(), existingIncident.getPriority());
            noUpdateReport.add(summary + " (Incident " + existingIncident.getNumber() + " déjà P" + existingIncident.getPriority() + ")");
            // On ne fait rien, itsmTaskDto reste null.

        } else {
            // Cas 2c : L'incident est ouvert et de priorité plus basse (ou on veut créer un P3).
            // On le met à jour.
            LOGGER.info("Mise à jour de l'incident {} vers Priorité {}.", existingIncident.getNumber(), priority);
            itsmTaskDto = this.updateIncident(existingIncident, automationHubCertificate, priority);
        }
    }

    // =========================================================================
    //                             FIN DE LA LOGIQUE
    // =========================================================================

    // Logique de comptage des succès/erreurs...
    // (adaptez cette partie pour ne pas compter comme une erreur le cas où on ne fait rien volontairement)
    if (itsmTaskDto != null) {
        successCounter.incrementAndGet();
        // ...
    } else if (!(priority == PRIORITY2 && isIncidentHighPriority(existingIncident, PRIORITY2))) {
        // C'est une erreur seulement si on n'est pas dans le cas "ne rien faire"
        errorCounter.incrementAndGet();
        // ...
    }

    return itsmTaskDto;
}
Use code with caution.
Java
Étape 3 : Garder les Méthodes Auxiliaires Propres
Pour que ce code soit vraiment pratique, assurez-vous d'utiliser les méthodes auxiliaires que nous avons définies précédemment. Elles rendent la condition if beaucoup plus lisible.
Generated java
// Dans la classe IncidentAutoEnrollTask

/**
 * Vérifie si un incident est dans un état "fermé" (Résolu, Clos, etc.).
 */
private boolean isIncidentClosedOrResolved(SnowIncidentDto incident) {
    // ... (logique avec try-catch pour Integer.parseInt)
}

/**
 * Vérifie si la priorité d'un incident est égale ou supérieure à un seuil.
 * Ex: priorityThreshold = 2 vérifie si la priorité est 1 ou 2.
 */
private boolean isIncidentHighPriority(SnowIncidentDto incident, int priorityThreshold) {
    // ... (logique avec try-catch pour Integer.parseInt)
}
Use code with caution.
Java
Pourquoi c'est la "Bonne Manière" ?
Non-Répétition (DRY - Don't Repeat Yourself) : Vous avez une seule méthode createIncidentAndLogResult qui gère tous les cas, au lieu de dupliquer la logique pour chaque priorité.
Flexibilité : Si demain une règle pour la P3 change, vous modifiez cette unique méthode. Si une P4 est introduite, le système est déjà prêt à la gérer.
Lisibilité : La logique est centralisée. La boucle principale se concentre sur "quel certificat traiter" et "quelle priorité lui donner", tandis que la méthode createIncidentAndLogResult se concentre sur "comment gérer un incident existant".
Pratique : L'utilisation de méthodes auxiliaires (isIncidentClosedOrResolved, etc.) rend les conditions complexes faciles à lire et à maintenir.