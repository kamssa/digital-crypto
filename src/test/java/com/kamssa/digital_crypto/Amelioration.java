package com.bnpparibas.certis.api.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Classe utilitaire GÉNÉRIQUE et réutilisable qui agit comme un conteneur
 * pour stocker l'état et les résultats d'un traitement par lot. Elle ne
 * requiert aucune modification des DTOs qu'elle traite.
 *
 * @param <T> Le type de l'objet principal traité par la tâche.
 */
public class ProcessingContext<T> {

    // --- Champs ---
    private final AtomicInteger successCounter = new AtomicInteger(0);
    private final AtomicInteger errorCounter = new AtomicInteger(0);
    
    private final List<Map<String, Object>> successReportItems = new ArrayList<>();
    private final List<Map<String, Object>> errorReportItems = new ArrayList<>();
    
    private final List<T> itemsWithValidationError = new ArrayList<>();

    private final Function<T, Map<String, Object>> reportDataMapper;

    /**
     * Constructeur qui accepte une fonction de mapping.
     * @param reportDataMapper une fonction qui prend un objet de type T et retourne
     *                         une Map<String, Object> pour les rapports.
     */
    public ProcessingContext(Function<T, Map<String, Object>> reportDataMapper) {
        if (reportDataMapper == null) {
            throw new IllegalArgumentException("Report data mapper function cannot be null.");
        }
        this.reportDataMapper = reportDataMapper;
    }

    // --- Méthodes Publiques ---

    public boolean hasItemsForFinalReport() {
        return successCounter.get() > 0 || errorCounter.get() > 0;
    }

    public void recordSuccess(String actionMessage, T item, Map<String, Object> additionalData) {
        successCounter.incrementAndGet();
        
        Map<String, Object> reportData = new HashMap<>(this.reportDataMapper.apply(item));
        reportData.put("actionMessage", actionMessage);
        
        if (additionalData != null) {
            reportData.putAll(additionalData);
        }
        successReportItems.add(reportData);
    }

    public void recordError(String actionMessage, String errorMessage, T item, Map<String, Object> additionalData) {
        errorCounter.incrementAndGet();
        
        Map<String, Object> reportData = (item != null) ? new HashMap<>(this.reportDataMapper.apply(item)) : new HashMap<>();
        reportData.put("actionMessage", actionMessage);
        reportData.put("errorMessage", errorMessage);
        
        if (additionalData != null) {
            reportData.putAll(additionalData);
        }
        errorReportItems.add(reportData);
    }
    
    public void recordValidationError(T item) {
        itemsWithValidationError.add(item);
    }

    // --- Getters ---
    public AtomicInteger getSuccessCounter() { return successCounter; }
    public AtomicInteger getErrorCounter() { return errorCounter; }
    public List<Map<String, Object>> getSuccessReportItems() { return successReportItems; }
    public List<Map<String, Object>> getErrorReportItems() { return errorReportItems; }
    public List<T> getItemsWithValidationError() { return itemsWithValidationError; }
}
Use code with caution.
Java
2. Extraits Clés de IncidentAutoEnrollTask.java
Ce code montre comment initialiser et utiliser le contexte.
Generated java
// Dans IncidentAutoEnrollTask.java

@Component
public class IncidentAutoEnrollTask {
    // ... (dépendances)

    @Scheduled(cron = "${cron.task.incidentAutoEnroll:0 30 9 * * *}")
    public void processExpireCertificates() {
        LOGGER.info("Démarrage de la tâche de traitement de l'expiration des certificats.");
        Instant startTime = Instant.now();
        
        // Définition de la fonction de mapping pour les certificats
        Function<AutomationHubCertificateLightDto, Map<String, Object>> certificateMapper = cert -> {
            Map<String, Object> data = new HashMap<>();
            if (cert != null) {
                data.put("automationHubId", cert.getAutomationHubId());
                data.put("commonName", cert.getCommonName());
                data.put("expiryDate", formatDate(cert.getExpiryDate())); // Utilise une méthode helper
                data.put("codeAp", getLabelByKey(cert, "APCode")); // Utilise une méthode helper
            }
            return data;
        };
        
        ProcessingContext<AutomationHubCertificateLightDto> context = new ProcessingContext<>(certificateMapper);

        // ... (try-catch, boucle, etc.)
        
        finally {
            this.sendSummaryReports(context);
            // ... (log de la durée)
        }
    }

    // --- Exemple d'utilisation dans une méthode d'action ---
    
    private AutoItsmTaskDto createNewInc(AutomationHubCertificateLightDto dto, ReferenceRefiDto referenceRefiDto, IncidentPriority priority, ..., ProcessingContext<AutomationHubCertificateLightDto> context) {
        try {
            AutoItsmTaskDto autoItsmTaskDto = itsmTaskService.createIncidentAutoEnroll(...);
            
            // Enregistrement du succès
            Map<String, Object> successDetails = new HashMap<>();
            successDetails.put("supportGroup", referenceRefiDto.getGroupSupportDto().getName());
            successDetails.put("priority", priority.name());
            successDetails.put("incidentNumber", autoItsmTaskDto.getItsmId());
            context.recordSuccess("Création INC", dto, successDetails);
            
            return autoItsmTaskDto;

        } catch (NoSupportGroupException e) {
            // Enregistrement de l'échec
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("supportGroup", "N/A");
            errorDetails.put("priority", priority.name());
            context.recordError("Création INC", e.getMessage(), dto, errorDetails);
            
            LOGGER.error(...);
            return null;
        }
    }

    // --- Méthode d'envoi de l'e-mail ---
    
    private void sendFinalReport(ProcessingContext<AutomationHubCertificateLightDto> context) {
        if (!context.hasItemsForFinalReport()) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("successItems", context.getSuccessReportItems());
        data.put("errorItems", context.getErrorReportItems());
        data.put("date", new Date());
        
        try {
            sendMailUtils.sendEmail("template/report-incident-p3.vm", data, ...);
            LOGGER.info(...);
        } catch (Exception e) {
            LOGGER.error(...);
        }
    }

    // --- Méthodes d'aide pour le mapping (helpers) ---

    private String getLabelByKey(AutomationHubCertificateLightDto dto, String key) {
        if (dto == null || dto.getLabels() == null || key == null) return "N/A";
        return dto.getLabels().stream()
                  .filter(label -> key.equalsIgnoreCase(label.getKey()))
                  .map(CertificateLabelDto::getValue)
                  .findFirst()
                  .orElse("N/A");
    }

    private String formatDate(Date date) {
        if (date == null) return "N/A";
        return date.toInstant()
                   .atZone(ZoneId.systemDefault())
                   .toLocalDate()
                   .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
/////////////////////////////////////////////
private void sendAutoEnrollCertificateNoCodeApReport(ProcessingContext<AutomationHubCertificateLightDto> context) {
    
    // On récupère la liste des certificats qui ont échoué à l'étape de validation du propriétaire.
    List<AutomationHubCertificateLightDto> certsInError = context.getItemsWithValidationError();

    // Condition de garde : s'il n'y a aucun certificat dans cette liste, on ne fait rien.
    if (certsInError == null || certsInError.isEmpty()) {
        LOGGER.info("Rapport 'sans codeAp' non envoyé : aucune erreur de validation à signaler.");
        return;
    }

    LOGGER.info("Préparation du rapport pour {} certificat(s) nécessitant une action manuelle.", certsInError.size());

    // Préparation des destinataires et des données pour le template
    List<String> toList = new ArrayList<>();
    toList.add(ipkiTeam);
    
    Map<String, Object> data = new HashMap<>();
    data.put("certsWithoutCodeAp", certsInError);
    data.put("date", new Date());

    // Envoi de l'e-mail
    try {
        sendMailUtils.sendEmail(
            "template/report-no-codeap.vm", // Template dédié pour ce rapport
            data, 
            toList, 
            "ALERTE : Action Manuelle Requise - Certificats sans Propriétaire Valide" // Sujet clair et alarmant
        );
        LOGGER.info("Rapport pour les certificats sans propriétaire valide envoyé avec succès.");
    } catch (Exception e) {
        LOGGER.error("Échec critique de l'envoi de l'e-mail pour les certificats sans propriétaire valide.", e);
    }
}
////////////////////////////////////////////////////////////////////
private void sendFinalReport(ProcessingContext<AutomationHubCertificateLightDto> context) {
    
    // --- 1. Condition de Garde ---
    // On vérifie s'il y a eu au moins un succès ou une erreur d'action.
    // Si ce n'est pas le cas, il n'y a rien d'intéressant à rapporter dans ce mail.
    if (!context.hasItemsForFinalReport()) {
        LOGGER.info("Rapport de synthèse non envoyé : aucune action de création ou d'erreur à signaler.");
        return;
    }

    // --- 2. Préparation des Données pour l'E-mail ---
    
    // Définition du destinataire
    List<String> toList = new ArrayList<>();
    toList.add(ipkiTeam);
    
    // Création de la Map de données qui sera injectée dans le template Velocity.
    Map<String, Object> data = new HashMap<>();
    
    // --- Séparation des incidents urgents et standards pour un affichage clair ---
    
    // On récupère la liste complète de tous les succès
    List<Map<String, Object>> allSuccesses = context.getSuccessReportItems();

    // On utilise les Streams Java pour filtrer et créer une liste des succès URGENTS
    List<Map<String, Object>> urgentSuccesses = allSuccesses.stream()
        .filter(item -> "URGENT".equals(item.get("priority")))
        .toList(); // ou .collect(Collectors.toList()) pour Java < 16

    // On fait de même pour créer une liste des succès STANDARDS
    List<Map<String, Object>> standardSuccesses = allSuccesses.stream()
        .filter(item -> "STANDARD".equals(item.get("priority")))
        .toList();

    // On ajoute ces listes séparées à notre Map de données.
    // Le template pourra y accéder via les clés "urgentSuccessItems" et "standardSuccessItems".
    data.put("urgentSuccessItems", urgentSuccesses);
    data.put("standardSuccessItems", standardSuccesses);
    
    // La liste des erreurs n'a pas besoin d'être filtrée, on la passe telle quelle.
    data.put("errorItems", context.getErrorReportItems());
    
    // On ajoute la date pour l'afficher dans le rapport.
    data.put("date", new Date());

    // --- 3. Envoi de l'E-mail ---
    try {
        // Appel à l'utilitaire d'envoi d'e-mails
        sendMailUtils.sendEmail(
            "template/report-incident-summary.vm", // Le chemin vers le template dédié à ce rapport
            data,                                  // Les données que le template va utiliser
            toList,                                // La liste des destinataires
            "Rapport de Synthèse - Incidents Auto-Enroll" // Le sujet de l'e-mail
        );
        
        // Log de confirmation avec les compteurs pour un suivi facile
        LOGGER.info("Rapport de synthèse envoyé avec {} succès et {} erreurs.", 
            context.getSuccessCounter().get(), 
            context.getErrorCounter().get());
            
    } catch (Exception e) {
        // En cas d'échec de l'envoi, on logue une erreur critique.
        LOGGER.error("Échec de l'envoi de l'e-mail de synthèse.", e);
    }
}
///////////////////////////////////////////////////////////
// Remplacer l'ancienne méthode par celle-ci
private void sendAutoEnrollCertificateNoCodeApReport(ProcessingContext<AutomationHubCertificateLightDto> context) {
    
    // --- 1. Récupération des données pertinentes ---
    // On demande au contexte la liste des certificats qui ont échoué à l'étape de validation.
    // C'est la seule source d'information qui nous intéresse pour ce rapport.
    List<AutomationHubCertificateLightDto> certsInError = context.getItemsWithValidationError();

    // --- 2. Condition de Garde ---
    // Si cette liste est vide, cela signifie qu'il n'y a aucune erreur de validation
    // à signaler. On arrête donc la méthode ici pour ne pas envoyer d'e-mail vide.
    if (certsInError == null || certsInError.isEmpty()) {
        LOGGER.info("Rapport 'sans codeAp' non envoyé : aucune erreur de validation de propriétaire à signaler.");
        return;
    }

    LOGGER.info("Préparation du rapport d'alerte pour {} certificat(s) nécessitant une action manuelle.", certsInError.size());

    // --- 3. Préparation des données pour l'e-mail ---
    
    // Définition du destinataire
    List<String> toList = new ArrayList<>();
    toList.add(ipkiTeam);
    
    // Création de la Map de données qui sera injectée dans le template Velocity.
    Map<String, Object> data = new HashMap<>();
    
    // On passe la liste complète des objets certificats au template.
    // Le template pourra accéder à toutes les propriétés de ces objets (commonName, id, etc.).
    data.put("certsWithoutCodeAp", certsInError);
    data.put("date", new Date());

    // --- 4. Envoi de l'e-mail ---
    try {
        // Appel à l'utilitaire d'envoi d'e-mails
        sendMailUtils.sendEmail(
            "template/report-no-codeap.vm", // Le chemin vers le template dédié à ce rapport d'alerte.
            data,                           // Les données que le template va utiliser.
            toList,                         // La liste des destinataires.
            "ALERTE : Action Manuelle Requise - Certificats sans Propriétaire Valide" // Un sujet clair et impactant.
        );
        LOGGER.info("Rapport d'alerte pour les certificats sans propriétaire valide envoyé avec succès.");
    
    } catch (Exception e) {
        // En cas d'échec de l'envoi de l'e-mail, on logue une erreur critique pour le monitoring.
        LOGGER.error("Échec critique de l'envoi de l'e-mail d'alerte pour les certificats sans propriétaire valide.", e);
    }
}
. Template pour le Rapport de Synthèse (report-incident-summary.vm)
Objectif : Fournir un résumé de l'activité de la tâche (créations/mises à jour réussies et erreurs d'action).
Utilisé par : sendFinalReport()
Generated html
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>Rapport de Synthèse - Incidents Auto-Enroll</title>
  <style>
    body { font-family: Arial, sans-serif; font-size: 14px; color: #333; line-height: 1.6; }
    .container { max-width: 1000px; margin: 20px auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px; }
    h1 { color: #2E4053; }
    h2 { color: #2E4053; border-bottom: 2px solid #AED6F1; padding-bottom: 5px; margin-top: 30px; }
    table { border-collapse: collapse; width: 100%; margin-top: 15px; font-size: 12px; }
    th, td { border: 1px solid #cccccc; text-align: left; padding: 8px; vertical-align: top; }
    th { background-color: #f2f2f2; font-weight: bold; }
    .urgent-title { color: #C0392B; border-bottom-color: #F5B7B1; }
    .error-title { color: #D8000C; border-bottom-color: #FFBABA; }
    .footer { margin-top: 30px; font-size: 12px; color: #888; text-align: center; }
    .summary-box { background-color: #EBF5FB; border: 1px solid #AED6F1; padding: 15px; margin-bottom: 20px; border-radius: 5px; }
    .summary-box strong { color: #2980B9; }
  </style>
</head>
<body>
<div class="container">
  
  <h1>Rapport de Synthèse - Incidents Auto-Enroll</h1>
  <p><strong>Date d'exécution :</strong> $date.toString()</p>
  
  <div class="summary-box">
    Ce rapport récapitule les actions de création et de mise à jour d'incidents effectuées par la tâche automatisée.
  </div>

  <!-- ================================================================== -->
  <!-- Section des Incidents URGENTS (Priorité Haute) -->
  <!-- ================================================================== -->
  #if( $urgentSuccessItems && !$urgentSuccessItems.isEmpty() )
    <h2 class="urgent-title">Incidents URGENTS Créés ou Mis à Jour ($urgentSuccessItems.size())</h2>
    <table>
      <thead>
        <tr>
          <th>Action</th>
          <th>Common Name</th>
          <th>Code AP</th>
          <th>Groupe Support</th>
          <th>Expiration</th>
          <th>N° Incident</th>
        </tr>
      </thead>
      <tbody>
        #foreach( $item in $urgentSuccessItems )
          <tr>
            <td>$!item.get("actionMessage")</td>
            <td>$!item.get("commonName")</td>
            <td>$!item.get("codeAp")</td>
            <td>$!item.get("supportGroup")</td>
            <td>$!item.get("expiryDate")</td>
            <td><strong>$!item.get("incidentNumber")</strong></td>
          </tr>
        #end
      </tbody>
    </table>
  #end

  <!-- ================================================================== -->
  <!-- Section des Incidents STANDARDS (Priorité Normale) -->
  <!-- ================================================================== -->
  #if( $standardSuccessItems && !$standardSuccessItems.isEmpty() )
    <h2>Incidents STANDARDS Créés ou Mis à Jour ($standardSuccessItems.size())</h2>
    <table>
      <thead>
        <tr>
          <th>Action</th>
          <th>Common Name</th>
          <th>Code AP</th>
          <th>Groupe Support</th>
          <th>Expiration</th>
          <th>N° Incident</th>
        </tr>
      </thead>
      <tbody>
        #foreach( $item in $standardSuccessItems )
          <tr>
            <td>$!item.get("actionMessage")</td>
            <td>$!item.get("commonName")</td>
            <td>$!item.get("codeAp")</td>
            <td>$!item.get("supportGroup")</td>
            <td>$!item.get("expiryDate")</td>
            <td><strong>$!item.get("incidentNumber")</strong></td>
          </tr>
        #end
      </tbody>
    </table>
  #end
  
  #if( $urgentSuccessItems.isEmpty() && $standardSuccessItems.isEmpty() )
      <h2>Opérations Réussies</h2>
      <p>Aucune opération de création ou de mise à jour réussie à rapporter.</p>
  #end

  <!-- ================================================================== -->
  <!-- Section des Erreurs d'Action -->
  <!-- ================================================================== -->
  #if( $errorItems && !$errorItems.isEmpty() )
    <h2 class="error-title">Opérations en Échec ($errorItems.size())</h2>
    <table>
      <thead>
        <tr>
          <th>Action Tentée</th>
          <th>Common Name</th>
          <th>Code AP</th>
          <th>Groupe Support</th>
          <th>Expiration</th>
          <th>Priorité Tentée</th>
          <th style="width: 30%;">Message d'Erreur</th>
        </tr>
      </thead>
      <tbody>
        #foreach( $item in $errorItems )
          <tr>
            <td>$!item.get("actionMessage")</td>
            <td>$!item.get("commonName")</td>
            <td>$!item.get("codeAp")</td>
            <td>$!item.get("supportGroup")</td>
            <td>$!item.get("expiryDate")</td>
            <td>$!item.get("priority")</td>
            <td>$!item.get("errorMessage")</td>
          </tr>
        #end
      </tbody>
    </table>
  #end

  <div class="footer">
    <p>Ceci est un e-mail généré automatiquement.</p>
  </div>
</div>
</body>
</html>
Use code with caution.
Html
2. Template pour l'Alerte des Certificats sans codeAp (report-no-codeap.vm)
Objectif : Alerter sur un problème de données nécessitant une action manuelle.
Utilisé par : sendAutoEnrollCertificateNoCodeApReport()
Generated html
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>ALERTE : Action Manuelle Requise</title>
  <style>
    body { font-family: Arial, sans-serif; font-size: 14px; color: #333; }
    .container { max-width: 900px; margin: 20px auto; padding: 20px; border: 2px solid #D8000C; border-radius: 5px; }
    h1 { color: #D8000C; } /* Rouge pour attirer l'attention */
    p { line-height: 1.6; }
    table { border-collapse: collapse; width: 100%; margin-top: 20px; font-size: 12px; }
    th, td { border: 1px solid #cccccc; text-align: left; padding: 10px; }
    th { background-color: #FFBABA; font-weight: bold; } /* Fond rouge clair pour l'en-tête */
    .footer { margin-top: 30px; font-size: 12px; color: #888; text-align: center; }
    .action-box { background-color: #FEF9E7; border: 1px solid #F7DC6F; padding: 15px; margin-top: 20px; border-radius: 5px; }
  </style>
</head>
<body>
<div class="container">
  
  <h1>ALERTE : Action Manuelle Requise</h1>
  <p><strong>Date de l'alerte :</strong> $!date.toString()</p>

  <div class="action-box">
    <p>
      La tâche automatisée n'a pas pu traiter les <strong>$certsWithoutCodeAp.size() certificat(s)</strong> listés ci-dessous.
    </p>
    <p>
      <strong>Raison :</strong> Leur <strong>code applicatif (codeAp) est invalide</strong>, ou <strong>aucun propriétaire/contact technique</strong> n'a pu être trouvé dans les référentiels.
    </p>
    <p>
      <strong>Action requise :</strong> Veuillez corriger les informations pour ces certificats afin que les futurs incidents puissent être créés et assignés correctement.
    </p>
  </div>

  #if( $certsWithoutCodeAp && !$certsWithoutCodeAp.isEmpty() )
    <table>
      <thead>
        <tr>
          <th>Common Name (CN)</th>
          <th>AutomationHub ID</th>
          <th>Code AP (tel que trouvé)</th>
          <th>Date d'Expiration</th>
        </tr>
      </thead>
      <tbody>
        #foreach( $cert in $certsWithoutCodeAp )
          <tr>
            <td><strong>$!cert.getCommonName()</strong></td>
            <td>$!cert.getAutomationHubId()</td>
            #set( $codeApLabel = "Non trouvé" )
            #if( $cert.getLabels() )
              #foreach( $label in $cert.getLabels() )
                #if( $label.getKey().equalsIgnoreCase("APCode") )
                  #set( $codeApLabel = $label.getValue() )
                #end
              #end
            #end
            <td>$!codeApLabel</td>
            <td>$!cert.getExpiryDate().toString()</td>
          </tr>
        #end
      </tbody>
    </table>
  #end

  <div class="footer">
    <p>Ceci est un e-mail généré automatiquement.</p>
  </div>
</div>
</body>
</html>
/////////////////////////////////////////////////////////////////
## =====================================================================
## TEMPLATE POUR LA DESCRIPTION D'UN INCIDENT AUTO-ENROLL
## Ce contenu sera visible dans le champ "Description" de l'incident ITSM.
## =====================================================================

## --- SECTION 1 : Contexte de la création de l'incident ---
## Affiche un message spécifique si cet incident est une recréation.
#if( $relatedIncNumber && !$relatedIncNumber.isEmpty() )
This incident has been automatically created because the previous incident ($relatedIncNumber) was closed, but the certificate has not been renewed yet.
#end

## --- SECTION 2 : Résumé des Informations Clés ---
## Un résumé clair et structuré des informations essentielles.
A certificate requiring your attention is about to expire. Please take the necessary action to renew it.

CERTIFICATE DETAILS:
--------------------
- Common Name (CN): $!cn
- Application Code (codeAp): $!appCode
- Expiration Date: $!expiryDate
- Assigned Support Group: $!supportGroup

TECHNICAL DETAILS:
------------------
- AutomationHub ID: $!automationHubId
- Initial Priority: $!priority

## --- SECTION 3 : Instructions Claires pour l'Action ---
## Explique pas à pas ce que l'utilisateur doit faire.

ACTION REQUIRED:

OPTION 1: To renew the certificate, please follow the steps below:
------------------------------------------------------------------
  a. Go to the website: https://certis.group.echonet
  b. Go into « My requests ».
  c. Click the button « Renew » on the certificate to start a renewal request.
  d. Verify/modify information in your renewal request.
  e. Download the new certificate through the button « Download ».
  f. Deploy the new certificate on your server.

OPTION 2: If the certificate does not have to be renewed:
-----------------------------------------------------------
  a. Go to the website: https://certis.group.echonet
  b. Go into « My requests ».
  c. Click the button « Do not renew » on the certificate.
  d. You may also request a revocation on the certificate if it's no more used.

## --- SECTION 4 : Avertissement Important ---
## Informe l'utilisateur sur le comportement futur de l'incident.

WARNING:
--------
This incident will be automatically upgraded to a higher priority (P2) if it is not resolved 3 days prior to the certificate's expiry date.


////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////
import com.bnpparibas.certis.api.enums.IncidentPriority;
import com.bnpparibas.certis.api.utils.ProcessingContext;
import com.bnpparibas.certis.automationhub.dto.AutomationHubCertificateLightDto;
import com.bnpparibas.certis.itsm.dto.AutoItsmTaskDtoImpl;
import com.bnpparibas.certis.referential.dto.OwnerAndReferenceRefiResult;
import com.bnpparibas.certis.referential.dto.ReferenceRefiDto;
import com.bnpparibas.certis.snow.dto.SnowIncidentReadResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests unitaires pour la tâche IncidentAutoEnrollTask")
class IncidentAutoEnrollTaskTest {

    // --- Mocks pour toutes les dépendances ---
    @Mock
    private ItsmTaskService itsmTaskService;
    @Mock
    private CertificateOwnerService certificateOwnerService;
    @Mock
    private AutomationHubService automationHubService;
    @Mock
    private SnowService snowService;
    @Mock
    private SendMailUtils sendMailUtils;

    // --- Instance de la classe à tester ---
    private IncidentAutoEnrollTask incidentAutoEnrollTask;

    @BeforeEach
    void setUp() {
        // Instanciation de la classe avec les mocks via le constructeur
        incidentAutoEnrollTask = new IncidentAutoEnrollTask(
            itsmTaskService,
            certificateOwnerService,
            automationHubService,
            snowService,
            sendMailUtils
        );

        // Injection des valeurs de configuration via ReflectionTestUtils
        // C'est la manière propre de simuler les @Value dans les tests unitaires.
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "urgentPriorityThresholdDays", 10);
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "processingWindowDays", 30);
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "ipkiTeam", "test-team@example.com");
    }

    // ========================================================================
    // --- Scénarios de Test ---
    // ========================================================================

    @Test
    @DisplayName("Ne doit rien traiter et envoyer des rapports vides si aucun certificat n'est trouvé")
    void processExpireCertificates_shouldDoNothing_whenNoCertificatesAreFound() {
        // --- Arrange ---
        // On simule que le service ne retourne aucun certificat
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(Collections.emptyList());

        // --- Act ---
        incidentAutoEnrollTask.processExpireCertificates();

        // --- Assert ---
        // On vérifie qu'aucune action de création/mise à jour n'a été tentée
        verify(itsmTaskService, never()).createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any());
        verify(certificateOwnerService, never()).findBestAvailableCertificateOwner(any(), anyString());

        // On vérifie que les méthodes de rapport ont bien été appelées (dans le finally)
        // mais qu'elles n'ont rien envoyé (car les contextes sont vides)
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sendMailUtils, never()).sendEmail(anyString(), dataCaptor.capture(), anyList(), anyString());
    }

    @Test
    @DisplayName("Doit enregistrer une erreur de validation si le propriétaire du certificat n'est pas trouvé")
    void processExpireCertificates_shouldRecordValidationError_whenOwnerIsNotFound() {
        // --- Arrange ---
        AutomationHubCertificateLightDto cert = createTestCertificate("cert-123");
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(cert));

        // On simule que la recherche de propriétaire échoue
        when(certificateOwnerService.findBestAvailableCertificateOwner(any(), anyString())).thenReturn(null);

        // --- Act ---
        incidentAutoEnrollTask.processExpireCertificates();

        // --- Assert ---
        // Aucune création/mise à jour d'incident ne doit avoir lieu
        verify(itsmTaskService, never()).createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any());

        // On capture les arguments passés à sendMail pour vérifier le contenu des rapports
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sendMailUtils, times(1)).sendEmail(anyString(), dataCaptor.capture(), anyList(), anyString()); // Un seul rapport, celui des erreurs de validation

        // On vérifie le contenu du rapport "sans codeAp"
        Map<String, Object> reportData = dataCaptor.getValue();
        List<AutomationHubCertificateLightDto> certsInError = (List<AutomationHubCertificateLightDto>) reportData.get("certsWithoutCodeAp");
        assertThat(certsInError).hasSize(1);
        assertThat(certsInError.get(0).getAutomationHubId()).isEqualTo("cert-123");
    }

    @Test
    @DisplayName("Doit créer un nouvel incident si aucun incident actif n'existe")
    void processExpireCertificates_shouldCreateNewIncident_whenNoneExists() {
        // --- Arrange ---
        AutomationHubCertificateLightDto cert = createTestCertificate("cert-456");
        OwnerAndReferenceRefiResult owner = createTestOwner();

        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(cert));
        when(certificateOwnerService.findBestAvailableCertificateOwner(any(), any())).thenReturn(owner);
        
        // Simuler qu'aucun incident n'est trouvé
        when(itsmTaskService.findByAutomationHubIdAndStatusAndTypeAndCreationDate(any(), any(), any(), any())).thenReturn(Collections.emptyList());
        
        // --- Act ---
        incidentAutoEnrollTask.processExpireCertificates();

        // --- Assert ---
        // On vérifie que la méthode de création a bien été appelée
        // (Ici on suppose que createNewInc appelle en interne createIncidentAutoEnroll)
        verify(itsmTaskService, times(1)).createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any());
        verify(itsmTaskService, never()).upgradeIncidentAutoEnroll(any(), any());
    }

    @Test
    @DisplayName("Doit mettre à jour un incident existant si sa priorité est trop basse")
    void processExpireCertificates_shouldUpdateIncident_whenPriorityIsTooLow() {
        // --- Arrange ---
        AutomationHubCertificateLightDto cert = createTestCertificate("cert-789", 5); // Expire dans 5 jours -> URGENT
        OwnerAndReferenceRefiResult owner = createTestOwner();
        AutoItsmTaskDtoImpl existingTask = new AutoItsmTaskDtoImpl();
        SnowIncidentReadResponseDto openLowPriorityIncident = new SnowIncidentReadResponseDto();
        openLowPriorityIncident.setState("2"); // In Progress
        openLowPriorityIncident.setPriority("3"); // STANDARD

        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(cert));
        when(certificateOwnerService.findBestAvailableCertificateOwner(any(), any())).thenReturn(owner);
        when(itsmTaskService.findByAutomationHubIdAndStatusAndTypeAndCreationDate(any(), any(), any(), any())).thenReturn(List.of(existingTask));
        when(snowService.getIncidentBySysId(any())).thenReturn(openLowPriorityIncident);

        // --- Act ---
        incidentAutoEnrollTask.processExpireCertificates();

        // --- Assert ---
        // On vérifie que la méthode de mise à jour a été appelée avec la bonne priorité
        ArgumentCaptor<IncidentPriority> priorityCaptor = ArgumentCaptor.forClass(IncidentPriority.class);
        verify(itsmTaskService, times(1)).upgradeIncidentAutoEnroll(priorityCaptor.capture(), any());
        assertThat(priorityCaptor.getValue()).isEqualTo(IncidentPriority.URGENT);
        
        verify(itsmTaskService, never()).createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any());
    }
    
    @Test
    @DisplayName("Ne doit rien faire si un incident existe déjà avec une priorité suffisante")
    void processExpireCertificates_shouldDoNothing_whenIncidentHasHighPriority() {
        // --- Arrange ---
        AutomationHubCertificateLightDto cert = createTestCertificate("cert-101", 5); // Expire dans 5 jours -> URGENT
        OwnerAndReferenceRefiResult owner = createTestOwner();
        AutoItsmTaskDtoImpl existingTask = new AutoItsmTaskDtoImpl();
        SnowIncidentReadResponseDto openHighPriorityIncident = new SnowIncidentReadResponseDto();
        openHighPriorityIncident.setState("2"); // In Progress
        openHighPriorityIncident.setPriority("2"); // URGENT

        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(List.of(cert));
        when(certificateOwnerService.findBestAvailableCertificateOwner(any(), any())).thenReturn(owner);
        when(itsmTaskService.findByAutomationHubIdAndStatusAndTypeAndCreationDate(any(), any(), any(), any())).thenReturn(List.of(existingTask));
        when(snowService.getIncidentBySysId(any())).thenReturn(openHighPriorityIncident);

        // --- Act ---
        incidentAutoEnrollTask.processExpireCertificates();

        // --- Assert ---
        // On vérifie qu'AUCUNE action n'a été tentée
        verify(itsmTaskService, never()).upgradeIncidentAutoEnroll(any(), any());
        verify(itsmTaskService, never()).createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any());
    }

    // ========================================================================
    // --- Méthodes d'aide (Helpers) pour créer des objets de test ---
    // ========================================================================

    private AutomationHubCertificateLightDto createTestCertificate(String id, int daysUntilExpiry) {
        AutomationHubCertificateLightDto cert = new AutomationHubCertificateLightDto();
        cert.setAutomationHubId(id);
        cert.setCommonName("test." + id + ".com");
        // ... (ajouter d'autres champs si nécessaire, comme les labels)
        // Pour simuler la date d'expiration
        // cert.setExpiryDate(DateUtils.addDays(new Date(), daysUntilExpiry));
        return cert;
    }
    
    private AutomationHubCertificateLightDto createTestCertificate(String id) {
        return createTestCertificate(id, 20); // Par défaut, expire dans 20 jours -> STANDARD
    }

    private OwnerAndReferenceRefiResult createTestOwner() {
        // ... Créer des instances mockées ou réelles de DTOs
        return new OwnerAndReferenceRefiResult(new CertificateOwnerDTO(), new ReferenceRefiDto());
    }
}
//////////////////////////// verouillge pour le test /////////////////////////
@Test
@DisplayName("Doit enregistrer une erreur de validation si le propriétaire du certificat n'est pas trouvé")
void processExpireCertificates_shouldRecordValidationError_whenOwnerIsNotFound() {
    // --- Arrange : On définit le comportement ATTENDU ---

    // 1. On crée notre certificat de test.
    AutomationHubCertificateLightDto cert = createTestCertificate("cert-123");
    
    // 2. On dit au mock de retourner ce certificat.
    when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(Arrays.asList(cert));

    // 3. On simule l'échec de la recherche de propriétaire. C'est le cœur du scénario.
    // On doit s'assurer que le deuxième argument est bien matché.
    // Si getLabelByKey retourne une String, on utilise anyString().
    // Si getLabelByKey peut retourner null, on utilise any() pour être sûr.
    when(certificateOwnerService.findBestAvailableCertificateOwner(any(AutomationHubCertificateLightDto.class), any()))
        .thenReturn(null);
        
    // --- NOUVEAU : On VERROUILLE les autres mocks ---
    // On s'assure qu'AUCUN appel inattendu n'est fait aux autres services.
    // Cela nous aidera à détecter immédiatement si le flux dévie.
    verify(itsmTaskService, never()).findByAutomationHubIdAndStatusAndTypeAndCreationDate(any(), any(), any(), any());
    verify(snowService, never()).getIncidentBySysId(anyString());


    // --- Act ---
    // On exécute la méthode à tester.
    incidentAutoEnrollTask.processExpireCertificates();


    // --- Assert ---
    // On vérifie que le bon rapport a été envoyé, et UN SEUL.
    ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

    // On s'attend à UN SEUL appel.
    verify(sendMailUtils, times(1)).sendEmail(
        anyString(),
        dataCaptor.capture(),
        anyList(),
        subjectCaptor.capture()
    );

    // On vérifie que c'est bien l'email d'alerte.
    assertThat(subjectCaptor.getValue()).contains("Action Manuelle Requise");
    
    // On vérifie le contenu de cet e-mail.
    Map<String, Object> reportData = dataCaptor.getValue();
    List<AutomationHubCertificateLightDto> certsInError = (List<AutomationHubCertificateLightDto>) reportData.get("certsWithoutCodeAp");
    
    assertThat(certsInError).isNotNull();
    assertThat(certsInError).hasSize(1);
    assertThat(certsInError.get(0).getAutomationHubId()).isEqualTo("cert-123");
}
///////////////////////////////////// incident standard et uregent///////////////////////////////
@BeforeEach
void setUp() {
    // Instanciation de la classe à tester...
    incidentAutoEnrollTask = new IncidentAutoEnrollTask(...);

    // --- CONFIGURATION ALIGNÉE SUR LES RÈGLES MÉTIER ---
    
    // On injecte la fenêtre de traitement principale (pour la recherche)
    // C'est le début de la période de surveillance.
    ReflectionTestUtils.setField(incidentAutoEnrollTask, "processingWindowDays", 15);

    // On injecte le seuil où la priorité devient URGENT.
    ReflectionTestUtils.setField(incidentAutoEnrollTask, "urgentPriorityThresholdDays", 3);
    
    ReflectionTestUtils.setField(incidentAutoEnrollTask, "ipkiTeam", "test-team@example.com");
}
Use code with caution.
Java
Étape 2 : Écrire le Test pour un Incident STANDARD
Maintenant, nous allons écrire un test pour un cas STANDARD typique. Un bon exemple serait un certificat expirant dans 10 jours. C'est bien dans la fenêtre de 15 jours, mais loin du seuil d'urgence de 3 jours.
Generated java
// Dans la classe de test

@Test
@DisplayName("Doit créer un incident STANDARD pour un certificat expirant dans 10 jours")
void processExpireCertificates_shouldCreateStandardIncident_whenExpiryIs10DaysAway() {
    
    // --- Arrange ---

    // 1. On crée un certificat de test expirant dans 10 jours.
    // Cela devrait résulter en une priorité STANDARD.
    AutomationHubCertificateLightDto cert = createTestCertificate("cert-standard-10days", 10);
    
    // 2. On simule la recherche et la validation du propriétaire
    OwnerAndReferenceRefiResult owner = createTestOwner();
    when(automationHubService.searchAutoEnrollExpiring(15)).thenReturn(Arrays.asList(cert)); // On s'attend à un appel avec 15 jours
    when(certificateOwnerService.findBestAvailableCertificateOwner(any(), any())).thenReturn(owner);
        
    // 3. On simule qu'aucun incident n'existe
    when(itsmTaskService.findByAutomationHubIdAndStatusAndTypeAndCreationDate(any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    
    // 4. On simule la création réussie de l'incident
    AutoItsmTaskDto createdTask = new AutoItsmTaskDtoImpl();
    createdTask.setItsmId("INC_STD_123");
    when(itsmTaskService.createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(createdTask);

    // --- Act ---
    incidentAutoEnrollTask.processExpireCertificates();

    // --- Assert ---
    // On vérifie que la priorité passée au service de création est bien STANDARD
    ArgumentCaptor<IncidentPriority> priorityCaptor = ArgumentCaptor.forClass(IncidentPriority.class);
    verify(itsmTaskService).createIncidentAutoEnroll(any(), any(), priorityCaptor.capture(), any(), any(), any(), any());
    assertThat(priorityCaptor.getValue()).isEqualTo(IncidentPriority.STANDARD);

    // ... (on peut aussi ajouter les vérifications sur l'e-mail de rapport comme précédemment)
}
Use code with caution.
Java
Étape 3 : Écrire le Test pour un Incident URGENT
Pour être complets, nous devons aussi tester l'autre côté de la règle : un certificat expirant dans 3 jours ou moins doit devenir URGENT.
Generated java
// Dans la classe de test

@Test
@DisplayName("Doit créer un incident URGENT pour un certificat expirant dans 2 jours")
void processExpireCertificates_shouldCreateUrgentIncident_whenExpiryIs2DaysAway() {
    
    // --- Arrange ---

    // 1. On crée un certificat de test expirant dans 2 jours.
    // Cela devrait résulter en une priorité URGENT.
    AutomationHubCertificateLightDto cert = createTestCertificate("cert-urgent-2days", 2);
    
    // 2. On simule la recherche et la validation du propriétaire
    OwnerAndReferenceRefiResult owner = createTestOwner();
    when(automationHubService.searchAutoEnrollExpiring(15)).thenReturn(Arrays.asList(cert));
    when(certificateOwnerService.findBestAvailableCertificateOwner(any(), any())).thenReturn(owner);
        
    // 3. On simule qu'aucun incident n'existe
    when(itsmTaskService.findByAutomationHubIdAndStatusAndTypeAndCreationDate(any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    
    // 4. On simule la création réussie de l'incident
    AutoItsmTaskDto createdTask = new AutoItsmTaskDtoImpl();
    createdTask.setItsmId("INC_URG_456");
    when(itsmTaskService.createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(createdTask);

    // --- Act ---
    incidentAutoEnrollTask.processExpireCertificates();

    // --- Assert ---
    // On vérifie que la priorité passée au service de création est bien URGENT
    ArgumentCaptor<IncidentPriority> priorityCaptor = ArgumentCaptor.forClass(IncidentPriority.class);
    verify(itsmTaskService).createIncidentAutoEnroll(any(), any(), priorityCaptor.capture(), any(), any(), any(), any());
    assertThat(priorityCaptor.getValue()).isEqualTo(IncidentPriority.URGENT);

    // ... (on peut vérifier ici que le rapport contient bien une entrée dans la section URGENT)
}
Use code with caution.
Java
La Logique de Production Validée
Ces tests valident que la logique suivante dans votre code de production est correcte :
Generated java
// Dans processExpireCertificates()
// La tâche appelle la recherche avec la fenêtre de 15 jours.
automationHubService.searchAutoEnrollExpiring(processingWindowDays); // processingWindowDays = 15

// Dans processSingleCertificate()
// Le seuil d'urgence est de 3 jours.
boolean isExpiringUrgently = certificate.getExpiryDate().compareTo(DateUtils.addDays(new Date(), urgentPriorityThresholdDays)) <= 0; // urgentPriorityThresholdDays = 3

IncidentPriority priority = isExpiringUrgently ? IncidentPriority.URGENT : IncidentPriority.STANDARD;
////////////////////////////////////////////////////////// test incident standard et uregent complets///////////////////////////////
package com.bnpparibas.certis.api.tasks;

import // ... (tous les imports nécessaires)
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests unitaires pour la tâche IncidentAutoEnrollTask")
class IncidentAutoEnrollTaskTest {

    // --- Mocks pour les dépendances ---
    @Mock private ItsmTaskService itsmTaskService;
    @Mock private CertificateOwnerService certificateOwnerService;
    @Mock private AutomationHubService automationHubService;
    @Mock private SendMailUtils sendMailUtils;
    // ... (autres mocks si besoin, comme SnowService)

    // --- Captureurs d'arguments ---
    @Captor private ArgumentCaptor<IncidentPriority> priorityCaptor;
    @Captor private ArgumentCaptor<Map<String, Object>> dataCaptor;
    @Captor private ArgumentCaptor<String> subjectCaptor;

    // --- Instance à tester ---
    private IncidentAutoEnrollTask incidentAutoEnrollTask;

    @BeforeEach
    void setUp() {
        incidentAutoEnrollTask = new IncidentAutoEnrollTask(
            itsmTaskService, certificateOwnerService, automationHubService, 
            snowService, sendMailUtils
        );
        
        // Configuration des seuils métier pour les tests
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "processingWindowDays", 15);
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "urgentPriorityThresholdDays", 3);
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "ipkiTeam", "test-team@example.com");
    }

    // ========================================================================
    // --- Scénarios de Test ---
    // ========================================================================

    @Test
    @DisplayName("Doit créer un incident STANDARD pour un certificat expirant dans 10 jours")
    void processExpireCertificates_shouldCreateStandardIncident_whenExpiryIs10DaysAway() {
        // --- Arrange ---
        String certId = "cert-std-10days";
        String incidentNumber = "INC_STD_123";
        AutomationHubCertificateLightDto cert = createTestCertificate(certId, 10);
        
        setupMocksForSuccessfulCreation(cert, incidentNumber);

        // --- Act ---
        incidentAutoEnrollTask.processExpireCertificates();

        // --- Assert ---
        assertIncidentCreationAndReport(
            IncidentPriority.STANDARD, 
            "standardSuccessItems", 
            certId, 
            incidentNumber
        );
    }

    @Test
    @DisplayName("Doit créer un incident URGENT pour un certificat expirant dans 2 jours")
    void processExpireCertificates_shouldCreateUrgentIncident_whenExpiryIs2DaysAway() {
        // --- Arrange ---
        String certId = "cert-urg-2days";
        String incidentNumber = "INC_URG_456";
        AutomationHubCertificateLightDto cert = createTestCertificate(certId, 2);
        
        setupMocksForSuccessfulCreation(cert, incidentNumber);

        // --- Act ---
        incidentAutoEnrollTask.processExpireCertificates();

        // --- Assert ---
        assertIncidentCreationAndReport(
            IncidentPriority.URGENT, 
            "urgentSuccessItems", 
            certId, 
            incidentNumber
        );
    }
    
    // ... (vos autres tests : _whenNoCertificatesAreFound, _whenOwnerIsNotFound, etc.)

    // ========================================================================
    // --- Méthodes d'aide (Helpers) ---
    // ========================================================================

    /**
     * Méthode d'aide pour configurer les mocks pour un scénario de création réussie.
     */
  private void setupMocksForSuccessfulCreation(AutomationHubCertificateLightDto cert, String incidentNumber) {
        // On utilise notre helper pour créer un propriétaire valide
        OwnerAndReferenceRefiResult owner = createTestOwner();
        
        // On mock les appels de service
        when(automationHubService.searchAutoEnrollExpiring(15)).thenReturn(Arrays.asList(cert));
        
        // On s'assure que la recherche de propriétaire réussit en retournant l'objet 'owner'
        // Le getLabelByKey du code de prod va extraire "TEST_CODE_AP" du certificat de test
        when(certificateOwnerService.findBestAvailableCertificateOwner(eq(cert), eq("TEST_CODE_AP")))
            .thenReturn(owner);
        
        when(itsmTaskService.findByAutomationHubIdAndStatusAndTypeAndCreationDate(any(), any(), any(), any()))
            .thenReturn(Collections.emptyList());
        
        AutoItsmTaskDto createdTask = new AutoItsmTaskDtoImpl();
        createdTask.setItsmId(incidentNumber);
        when(itsmTaskService.createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(createdTask);
    }

    /**
     * Méthode d'aide pour les assertions communes de création d'incident et de rapport.
     */
    private void assertIncidentCreationAndReport(IncidentPriority expectedPriority, String reportSectionKey, String expectedCertId, String expectedIncidentNumber) {
        // VÉRIFICATION 1 : L'action de création a eu lieu avec la bonne priorité
        verify(itsmTaskService).createIncidentAutoEnroll(any(), any(), priorityCaptor.capture(), any(), any(), any(), any());
        assertThat(priorityCaptor.getValue())
            .as("La priorité de l'incident créé doit être " + expectedPriority)
            .isEqualTo(expectedPriority);

        // VÉRIFICATION 2 : Le bon rapport a été envoyé (un seul)
        verify(sendMailUtils, times(1)).sendEmail(anyString(), dataCaptor.capture(), anyList(), subjectCaptor.capture());
        assertThat(subjectCaptor.getValue())
            .as("Le sujet de l'e-mail doit être celui du rapport de synthèse")
            .contains("Rapport de Synthèse");
            
        // VÉRIFICATION 3 : Le contenu du rapport est correct
        Map<String, Object> reportData = dataCaptor.getValue();
        
        // On vérifie la section attendue
        List<Map<String, Object>> successSection = (List<Map<String, Object>>) reportData.get(reportSectionKey);
        assertThat(successSection)
            .as("La section de rapport '" + reportSectionKey + "' ne doit pas être vide")
            .isNotNull()
            .hasSize(1);
        
        // On vérifie le contenu de la ligne de rapport
        Map<String, Object> successItem = successSection.get(0);
        assertThat(successItem.get("automationHubId")).isEqualTo(expectedCertId);
        assertThat(successItem.get("incidentNumber")).isEqualTo(expectedIncidentNumber);
        assertThat(successItem.get("priority")).isEqualTo(expectedPriority.name());

        // On vérifie que l'autre section de succès est vide
        String otherSectionKey = reportSectionKey.equals("standardSuccessItems") ? "urgentSuccessItems" : "standardSuccessItems";
        List<Map<String, Object>> otherSuccessSection = (List<Map<String, Object>>) reportData.get(otherSectionKey);
        assertThat(otherSuccessSection)
            .as("L'autre section de rapport '" + otherSectionKey + "' doit être vide")
            .isNotNull()
            .isEmpty();
    }

    private AutomationHubCertificateLightDto createTestCertificate(String id, int daysUntilExpiry) {
        AutomationHubCertificateLightDto cert = new AutomationHubCertificateLightDto();
        
        cert.setAutomationHubId(id);
        cert.setCommonName("test." + id + ".com");

        // Calcul dynamique de la date d'expiration
        // On a besoin d'une dépendance comme commons-lang3 pour DateUtils, 
        // ou on peut utiliser l'API java.time
        Date expiryDate = Date.from(Instant.now().plus(daysUntilExpiry, ChronoUnit.DAYS));
        cert.setExpiryDate(expiryDate);

        // On simule aussi la présence des labels nécessaires
        List<CertificateLabelDto> labels = new ArrayList<>();
        labels.add(new CertificateLabelDto("ENVIRONNEMENT", "PROD"));
        labels.add(new CertificateLabelDto("APCode", "TEST_CODE_AP"));
        cert.setLabels(labels);

        return cert;
    }

   private OwnerAndReferenceRefiResult createTestOwner() {
        // On crée un DTO pour le propriétaire avec un AUID valide
        CertificateOwnerDTO ownerDTO = new CertificateOwnerDTO();
        ownerDTO.setAuid("APP12345");
        ownerDTO.setHost("hostname.example.com");

        // On crée un DTO pour le groupe de support
        GroupSupportDto groupSupport = new GroupSupportDto();
        groupSupport.setName("TEST_SUPPORT_GROUP");

        // On crée un DTO pour les informations de référence
        ReferenceRefiDto refiDto = new ReferenceRefiDto();
        refiDto.setCodeAp("TEST_CODE_AP");
        refiDto.setGroupSupportDto(groupSupport);
        
        // On retourne l'objet de résultat complet
        return new OwnerAndReferenceRefiResult(ownerDTO, refiDto);
    }
}
Use code with caution.
Java
Explications Clés de cette Version
@Captor : Au lieu de créer les ArgumentCaptor dans chaque méthode, on peut les déclarer comme des champs de la classe de test avec l'annotation @Captor. Mockito les initialisera automatiquement. C'est plus propre.
Méthodes d'Aide setupMocksFor... et assert... : C'est le point le plus important.
setupMocksForSuccessfulCreation : Factorise toute la partie Arrange qui est commune aux deux tests de création.
assertIncidentCreationAndReport : Factorise toute la partie Assert. Elle est maintenant paramétrable pour pouvoir vérifier un succès STANDARD ou URGENT.
Bénéfices de la Factorisation :
DRY (Don't Repeat Yourself) : Le code de test n'est pas dupliqué.
Lisibilité : Les méthodes de test (@Test) deviennent très courtes et lisibles. On voit immédiatement leur intention : "préparer le scénario X, exécuter, puis vérifier le résultat Y".
Maintenabilité : Si demain vous devez changer un détail dans les assertions (par exemple, vérifier un champ de plus dans le rapport), vous n'avez qu'à le modifier dans la méthode assert..., et la correction s'appliquera à tous les tests qui l'utilisent.
Cette classe de test est maintenant complète, robuste et très facile à lire et à maintenir. Elle valide de manière fiable les règles métier que vous avez spécifiées.
////////////////////////////////////////////////////////////////////////////////////////////////////
private void setupMocksForSuccessfulCreation(AutomationHubCertificateLightDto cert, String incidentNumber) {

    // --- PRÉPARATION DES DONNÉES DE RETOUR ---
    OwnerAndReferenceRefiResult owner = createTestOwner();
    AutoItsmTaskDto createdTask = new AutoItsmTaskDtoImpl();
    createdTask.setItsmId(incidentNumber);

    // --- CONFIGURATION DES MOCKS ---
    // Chaque 'when' est une instruction claire et complète.

    // Mock 1 : Le service AutomationHub retourne notre certificat.
    when(automationHubService.searchAutoEnrollExpiring(anyInt()))
        .thenReturn(Arrays.asList(cert));

    // Mock 2 : Le service de propriétaire trouve un propriétaire valide.
    when(certificateOwnerService.findBestAvailableCertificateOwner(any(), any()))
        .thenReturn(owner);

    // Mock 3 : Le service ITSM, quand on cherche un incident existant, retourne une liste VIDE.
    // Assurons-nous que la signature est EXACTEMENT la bonne (4 arguments ici).
    when(itsmTaskService.findByAutomationHubIdAndStatusAndTypeAndCreationDate(
        anyString(),                // Argument 1: automationHubId (String)
        any(),                      // Argument 2: status (peut être null)
        any(InciTypeEnum.class),    // Argument 3: type (InciTypeEnum)
        any()                       // Argument 4: creationDate (peut être null)
    )).thenReturn(Collections.emptyList());
    
    // Mock 4 (optionnel mais recommandé) : La méthode de sélection, appelée avec une liste vide, retourne null.
    when(itsmTaskService.getIncNumberByAutoItsmTaskList(Collections.emptyList()))
        .thenReturn(null);

    // Mock 5 : Le service ITSM, quand on crée un incident, retourne l'objet 'createdTask'.
    // La signature doit correspondre à celle du 'verify' (probablement 7 arguments).
    when(itsmTaskService.createIncidentAutoEnroll(
        any(AutomationHubCertificateLightDto.class),
        any(ReferenceRefiDto.class),
        any(Integer.class),
        anyString(),
        anyString(),
        any(InciTypeEnum.class),
        any(AutoItsmTaskDtoImpl.class)
    )).thenReturn(createdTask);
}
///////////// test complet repris//////////////////////////////////////
import com.bnpparibas.certis.api.enums.IncidentPriority;
import com.bnpparibas.certis.api.enums.SnowIncStateEnum;
import com.bnpparibas.certis.api.utils.ProcessingContext;
import com.bnpparibas.certis.automationhub.dto.AutomationHubCertificateLightDto;
import com.bnpparibas.certis.automationhub.dto.CertificateLabelDto;
import com.bnpparibas.certis.itsm.dto.AutoItsmTaskDto;
import com.bnpparibas.certis.itsm.dto.AutoItsmTaskDtoImpl;
import com.bnpparibas.certis.referential.dto.GroupSupportDto;
import com.bnpparibas.certis.referential.dto.OwnerAndReferenceRefiResult;
import com.bnpparibas.certis.referential.dto.ReferenceRefiDto;
import com.bnpparibas.certis.snow.dto.SnowIncidentReadResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests unitaires pour la tâche IncidentAutoEnrollTask")
class IncidentAutoEnrollTaskTest {

    // --- Mocks ---
    @Mock private ItsmTaskService itsmTaskService;
    @Mock private CertificateOwnerService certificateOwnerService;
    @Mock private AutomationHubService automationHubService;
    @Mock private SnowService snowService;
    @Mock private SendMailUtils sendMailUtils;

    // --- Captureurs ---
    @Captor private ArgumentCaptor<Integer> priorityValueCaptor;
    @Captor private ArgumentCaptor<Map<String, Object>> dataCaptor;
    @Captor private ArgumentCaptor<String> subjectCaptor;

    // --- Instance à tester ---
    private IncidentAutoEnrollTask incidentAutoEnrollTask;

    @BeforeEach
    void setUp() {
        incidentAutoEnrollTask = new IncidentAutoEnrollTask(
            itsmTaskService, certificateOwnerService, automationHubService, 
            snowService, sendMailUtils
        );
        
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "processingWindowDays", 15);
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "urgentPriorityThresholdDays", 3);
        ReflectionTestUtils.setField(incidentAutoEnrollTask, "ipkiTeam", "test-team@example.com");
    }

    // ========================================================================
    // --- Scénarios de Test ---
    // ========================================================================

    @Test
    @DisplayName("Doit créer un incident STANDARD pour un certificat expirant dans 10 jours")
    void processExpireCertificates_shouldCreateStandardIncident_whenExpiryIs10DaysAway() {
        // Arrange
        String certId = "cert-std-10days";
        String incidentNumber = "INC_STD_123";
        AutomationHubCertificateLightDto cert = createTestCertificate(certId, 10);
        setupMocksForSuccessfulCreation(cert, incidentNumber);

        // Act
        incidentAutoEnrollTask.processExpireCertificates();

        // Assert
        assertIncidentCreationAndReport(IncidentPriority.STANDARD, "standardSuccessItems", certId, incidentNumber);
    }
    
    @Test
    @DisplayName("Doit créer un incident URGENT pour un certificat expirant dans 3 jours")
    void processExpireCertificates_shouldCreateUrgentIncident_whenExpiryIs3DaysAway() {
        // Arrange
        String certId = "cert-urg-3days";
        String incidentNumber = "INC_URG_456";
        AutomationHubCertificateLightDto cert = createTestCertificate(certId, 3);
        setupMocksForSuccessfulCreation(cert, incidentNumber);

        // Act
        incidentAutoEnrollTask.processExpireCertificates();

        // Assert
        assertIncidentCreationAndReport(IncidentPriority.URGENT, "urgentSuccessItems", certId, incidentNumber);
    }
    
    @Test
    @DisplayName("Doit mettre à jour un incident existant si sa priorité est trop basse")
    void processExpireCertificates_shouldUpdateIncident_whenPriorityIsTooLow() {
        // Arrange
        AutomationHubCertificateLightDto cert = createTestCertificate("cert-update-789", 2);
        OwnerAndReferenceRefiResult owner = createTestOwner();
        
        AutoItsmTaskDtoImpl existingTaskInDb = new AutoItsmTaskDtoImpl();
        existingTaskInDb.setItsmId("SYS_ID_123");
        
        SnowIncidentReadResponseDto openLowPriorityIncident = new SnowIncidentReadResponseDto();
        openLowPriorityIncident.setState(String.valueOf(SnowIncStateEnum.IN_PROGRESS.getValue()));
        openLowPriorityIncident.setPriority(String.valueOf(IncidentPriority.STANDARD.getValue()));

        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(Arrays.asList(cert));
        when(certificateOwnerService.findBestAvailableCertificateOwner(any(), anyString())).thenReturn(owner);
        when(itsmTaskService.findByAutomationhubIdAndTypeAndCreationDate(anyString(), any(InciTypeEnum.class), any(Date.class)))
            .thenReturn(Arrays.asList(existingTaskInDb));
        when(itsmTaskService.getIncNumberByAutoItsmTaskList(anyList())).thenReturn(existingTaskInDb);
        when(snowService.getIncidentBySysId(eq("SYS_ID_123"))).thenReturn(openLowPriorityIncident);

        // Act
        incidentAutoEnrollTask.processExpireCertificates();

        // Assert
        verify(itsmTaskService, times(1)).upgradeIncidentAutoEnroll(priorityValueCaptor.capture(), any(AutoItsmTaskDtoImpl.class));
        assertThat(priorityValueCaptor.getValue()).isEqualTo(IncidentPriority.URGENT.getValue());
        verify(itsmTaskService, never()).createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any());
    }
    
    @Test
    @DisplayName("Doit RECRÉER un incident si l'incident précédent est résolu")
    void processExpireCertificates_shouldRecreateIncident_whenPreviousIncidentIsResolved() {
        // Arrange
        String certId = "cert-recreate-123";
        String newIncidentNumber = "INC_NEW_789";
        AutomationHubCertificateLightDto cert = createTestCertificate(certId, 2);
        OwnerAndReferenceRefiResult owner = createTestOwner();
        
        AutoItsmTaskDtoImpl previousResolvedTask = new AutoItsmTaskDtoImpl();
        previousResolvedTask.setItsmId("SYS_ID_OLD_456");
        
        SnowIncidentReadResponseDto resolvedIncident = new SnowIncidentReadResponseDto();
        resolvedIncident.setState(String.valueOf(SnowIncStateEnum.RESOLVED.getValue()));
        
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(Arrays.asList(cert));
        when(certificateOwnerService.findBestAvailableCertificateOwner(any(), anyString())).thenReturn(owner);
        when(itsmTaskService.findByAutomationhubIdAndTypeAndCreationDate(anyString(), any(InciTypeEnum.class), any(Date.class)))
            .thenReturn(Arrays.asList(previousResolvedTask));
        when(itsmTaskService.getIncNumberByAutoItsmTaskList(anyList())).thenReturn(previousResolvedTask);
        when(snowService.getIncidentBySysId(eq("SYS_ID_OLD_456"))).thenReturn(resolvedIncident);
        
        AutoItsmTaskDto createdTask = new AutoItsmTaskDtoImpl();
        createdTask.setItsmId(newIncidentNumber);
        when(itsmTaskService.createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any())).thenReturn(createdTask);

        // Act
        incidentAutoEnrollTask.processExpireCertificates();

        // Assert
        verify(itsmTaskService, times(1)).createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any());
        verify(itsmTaskService, never()).upgradeIncidentAutoEnroll(any(), any());
    }

    // ========================================================================
    // --- Méthodes d'aide (Helpers) ---
    // ========================================================================

    private void setupMocksForSuccessfulCreation(AutomationHubCertificateLightDto cert, String incidentNumber) {
        OwnerAndReferenceRefiResult owner = createTestOwner();
        AutoItsmTaskDto createdTask = new AutoItsmTaskDtoImpl();
        createdTask.setItsmId(incidentNumber);

        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(Arrays.asList(cert));
        when(certificateOwnerService.findBestAvailableCertificateOwner(any(), anyString())).thenReturn(owner);
        when(itsmTaskService.findByAutomationhubIdAndTypeAndCreationDate(anyString(), any(InciTypeEnum.class), any(Date.class)))
            .thenReturn(Collections.emptyList());
        when(itsmTaskService.getIncNumberByAutoItsmTaskList(anyList())).thenReturn(null);
        when(itsmTaskService.createIncidentAutoEnroll(any(), any(), any(), any(), any(), any(), any())).thenReturn(createdTask);
    }

    private void assertIncidentCreationAndReport(IncidentPriority expectedPriority, String reportSectionKey, String expectedCertId, String expectedIncidentNumber) {
        verify(itsmTaskService).createIncidentAutoEnroll(any(), any(), priorityValueCaptor.capture(), any(), any(), any(), any());
        assertThat(priorityValueCaptor.getValue()).isEqualTo(expectedPriority.getValue());

        verify(sendMailUtils, times(1)).sendEmail(anyString(), dataCaptor.capture(), anyList(), subjectCaptor.capture());
        assertThat(subjectCaptor.getValue()).contains("Rapport de Synthèse");
            
        Map<String, Object> reportData = dataCaptor.getValue();
        List<Map<String, Object>> successSection = (List<Map<String, Object>>) reportData.get(reportSectionKey);
        assertThat(successSection).isNotNull().hasSize(1);
        
        Map<String, Object> successItem = successSection.get(0);
        assertThat(successItem.get("automationHubId")).isEqualTo(expectedCertId);
        assertThat(successItem.get("incidentNumber")).isEqualTo(expectedIncidentNumber);
        assertThat(successItem.get("priority")).isEqualTo(expectedPriority.name());

        String otherSectionKey = reportSectionKey.equals("standardSuccessItems") ? "urgentSuccessItems" : "standardSuccessItems";
        List<Map<String, Object>> otherSuccessSection = (List<Map<String, Object>>) reportData.get(otherSectionKey);
        assertThat(otherSuccessSection).isNotNull().isEmpty();
    }

    private AutomationHubCertificateLightDto createTestCertificate(String id, int daysUntilExpiry) {
        AutomationHubCertificateLightDto cert = new AutomationHubCertificateLightDto();
        cert.setAutomationHubId(id);
        cert.setCommonName("test." + id + ".com");
        cert.setExpiryDate(Date.from(Instant.now().plus(daysUntilExpiry, ChronoUnit.DAYS)));
        
        List<CertificateLabelDto> labels = new ArrayList<>();
        CertificateLabelDto envLabel = new CertificateLabelDto();
        envLabel.setKey("ENVIRONNEMENT");
        envLabel.setValue("PROD");
        labels.add(envLabel);
        
        CertificateLabelDto codeApLabel = new CertificateLabelDto();
        codeApLabel.setKey("APCode");
        codeApLabel.setValue("TEST_CODE_AP");
        labels.add(codeApLabel);
        
        cert.setLabels(labels);
        return cert;
    }

    private OwnerAndReferenceRefiResult createTestOwner() {
        CertificateOwnerDTO ownerDTO = new CertificateOwnerDTO();
        ownerDTO.setAuid("APP12345");
        ReferenceRefiDto refiDto = new ReferenceRefiDto();
        refiDto.setGroupSupportDto(new GroupSupportDto());
        return new OwnerAndReferenceRefiResult(ownerDTO, refiDto);
    }
	@Test
    @DisplayName("Doit envoyer les DEUX rapports quand il y a des succès, des erreurs d'action et des erreurs de validation")
    void processExpireCertificates_shouldSendBothReports_whenAllCasesOccur() {
        
        // --- Arrange ---

        // 1. On prépare 3 certificats pour 3 scénarios différents
        AutomationHubCertificateLightDto certSuccess = createTestCertificate("cert-success", 10); // Va réussir
        AutomationHubCertificateLightDto certActionError = createTestCertificate("cert-action-error", 8); // Va échouer à la création
        AutomationHubCertificateLightDto certValidationError = createTestCertificate("cert-validation-error", 5); // N'a pas de propriétaire

        // 2. On configure les mocks pour chaque cas
        OwnerAndReferenceRefiResult validOwner = createTestOwner();
        
        // La recherche initiale retourne nos 3 certificats
        when(automationHubService.searchAutoEnrollExpiring(anyInt())).thenReturn(Arrays.asList(
            certSuccess, certActionError, certValidationError
        ));
        
        // Comportement de la recherche de propriétaire
        when(certificateOwnerService.findBestAvailableCertificateOwner(eq(certSuccess), anyString())).thenReturn(validOwner);
        when(certificateOwnerService.findBestAvailableCertificateOwner(eq(certActionError), anyString())).thenReturn(validOwner);
        when(certificateOwnerService.findBestAvailableCertificateOwner(eq(certValidationError), anyString())).thenReturn(null); // Échec pour celui-ci
        
        // Comportement de la recherche d'incident existant (aucun n'existe)
        when(itsmTaskService.findByAutomationhubIdAndTypeAndCreationDate(anyString(), any(), any())).thenReturn(Collections.emptyList());
        
        // Comportement de la création d'incident
        // a) Réussite pour le certificat 'certSuccess'
        AutoItsmTaskDto createdTask = new AutoItsmTaskDtoImpl();
        createdTask.setItsmId("INC_SUCCESS_123");
        when(itsmTaskService.createIncidentAutoEnroll(eq(certSuccess), any(), any(), any(), any(), any(), any())).thenReturn(createdTask);
        // b) Échec pour le certificat 'certActionError'
        when(itsmTaskService.createIncidentAutoEnroll(eq(certActionError), any(), any(), any(), any(), any(), any()))
            .thenThrow(new CreateIncidentException("ITSM API is down"));

        // --- Act ---
        
        incidentAutoEnrollTask.processExpireCertificates();

        // --- Assert ---

        // VÉRIFICATION 1 : On s'attend à DEUX appels d'envoi d'e-mail
        verify(sendMailUtils, times(2)).sendEmail(anyString(), dataCaptor.capture(), anyList(), subjectCaptor.capture());

        // VÉRIFICATION 2 : On analyse les deux e-mails envoyés
        
        // On récupère toutes les données et sujets capturés
        List<Map<String, Object>> allReportData = dataCaptor.getAllValues();
        List<String> allSubjects = subjectCaptor.getAllValues();

        // On identifie le rapport de synthèse et le rapport d'alerte
        Map<String, Object> summaryReportData = null;
        Map<String, Object> alertReportData = null;
        
        for (int i = 0; i < allSubjects.size(); i++) {
            if (allSubjects.get(i).contains("Rapport de Synthèse")) {
                summaryReportData = allReportData.get(i);
            } else if (allSubjects.get(i).contains("Action Manuelle Requise")) {
                alertReportData = allReportData.get(i);
            }
        }

        // VÉRIFICATION 3 : On valide le contenu du RAPPORT DE SYNTHÈSE
        assertThat(summaryReportData).isNotNull();
        // a) Section Succès
        List<Map<String, Object>> successItems = (List<Map<String, Object>>) summaryReportData.get("standardSuccessItems");
        assertThat(successItems).hasSize(1);
        assertThat(successItems.get(0).get("automationHubId")).isEqualTo("cert-success");
        // b) Section Erreurs
        List<Map<String, Object>> errorItems = (List<Map<String, Object>>) summaryReportData.get("errorItems");
        assertThat(errorItems).hasSize(1);
        assertThat(errorItems.get(0).get("automationHubId")).isEqualTo("cert-action-error");
        
        // VÉRIFICATION 4 : On valide le contenu du RAPPORT D'ALERTE
        assertThat(alertReportData).isNotNull();
        List<AutomationHubCertificateLightDto> validationErrors = (List<AutomationHubCertificateLightDto>) alertReportData.get("certsWithoutCodeAp");
        assertThat(validationErrors).hasSize(1);
        assertThat(validationErrors.get(0).getAutomationHubId()).isEqualTo("cert-validation-error");
    }


    // ========================================================================
    // --- Méthodes d'aide (Helpers) ---
    // ========================================================================
    
    // ... (les méthodes d'aide restent les mêmes)
}
}