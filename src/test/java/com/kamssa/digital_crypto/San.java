package com.bnpparibas.certis.certificate.request.enums;

public enum SanType {
    DNSNAME,
    RFC822NAME,
    IPADDRESS,
    OTHERNAME_GUID,
    OTHERNAME_UPN,
    URI
}
Étape 2 : Mettre à jour la classe d'entité San.java
Maintenant, modifions le fichier San.java pour intégrer ces changements.
Voici le code San.java mis à jour :
code
Java
package com.bnpparibas.certis.certificate.request.model;

import com.bnpparibas.certis.certificate.request.enums.SanType; // Importer la nouvelle énumération
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "SAN") // Il est bon de spécifier le nom de la table
public class San implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_San_Id")
    @SequenceGenerator(name = "seq_San_Id", sequenceName = "SEQ_SAN_ID", allocationSize = 1)
    private Long id;

    // --- MODIFICATION 1 : Renommage de 'url' en 'sanValue' ---
    // L'ancien champ 'private String url;' est remplacé par :
    @Column(name = "san_value") // Le nom de la colonne en base de données sera san_value
    private String sanValue;

    // --- MODIFICATION 2 : Ajout du champ 'type' ---
    @NotNull // Pour s'assurer que la valeur n'est jamais nulle, comme demandé dans le ticket
    @Enumerated(EnumType.STRING) // Stocke le nom de l'enum ("DNSNAME") en base, ce qui est une bonne pratique
    @Column(name = "type", nullable = false)
    private SanType type;

    // --- Association existante ---
    // bi-directional many-to-one association to Certificate
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "CERTIFICATE_ID", nullable = false)
    private Certificate certificate;

    /**
     * @author Massoudou DIALLO
     */
    public San() {
    }

    // Pensez à mettre à jour les getters et setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSanValue() {
        return sanValue;
    }

    public void setSanValue(String sanValue) {
        this.sanValue = sanValue;
    }

    public SanType getType() {
        return type;
    }

    public void setType(SanType type) {
        this.type = type;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
    }
}
///////////////
public void setSanValue(String sanValue) {
    this.sanValue = sanValue;
    this.url = sanValue; // Synchronisation !
}

// L'ancien setter : il met aussi à jour les deux champs
public void setUrl(String url) {
    this.url = url;
    this.sanValue = url; // Synchronisation !
}
public void integrateCsrSans(RequestDto requestDto) {
    try {
        // ... (le début de votre méthode reste le même)

        List<San> sansInCsr = csrDecoder.getSansList(decodedCsr).stream()
                .map(sanString -> {
                    San newSan = new San();
                    // On utilise le NOUVEAU setter. Il mettra à jour url et sanValue en même temps.
                    newSan.setSanValue(sanString); 
                    
                    // ... (la logique pour déterminer le type reste la même)
                    if (sanString.matches("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")) {
                        newSan.setType(SanType.IPADDRESS);
                    } else {
                        newSan.setType(SanType.DNSNAME);
                    }
                    return newSan;
                }).collect(Collectors.toList());

        List<San> allSans = Stream.of(sansInCsr, requestDto.getCertificate().getSans())
                .flatMap(list -> list == null ? Stream.empty() : list.stream())
                .collect(Collectors.collectingAndThen(
                        // On utilise la NOUVELLE méthode pour la clé de la map
                        Collectors.toMap(San::getSanValue, san -> san, (existing, replacement) -> existing),
                        map -> new ArrayList<>(map.values())
                ));

        requestDto.getCertificate().setSans(allSans);
        LOGGER.info("ALL SANS = " + allSans.toString());

    } catch (Exception e) {
        LOGGER.error("Error when associate csr Sans :", e);
    }
}
/////////////////////////////////////////////////////////////
public void integrateCsrSans(RequestDto requestDto) {
    try {
        // ... (le début de votre méthode reste le même)

        List<San> sansInCsr = csrDecoder.getSansList(decodedCsr).stream()
                .map(sanString -> {
                    San newSan = new San();
                    // On utilise le NOUVEAU setter. Il mettra à jour url et sanValue en même temps.
                    newSan.setSanValue(sanString); 
                    
                    // ... (la logique pour déterminer le type reste la même)
                    if (sanString.matches("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")) {
                        newSan.setType(SanType.IPADDRESS);
                    } else {
                        newSan.setType(SanType.DNSNAME);
                    }
                    return newSan;
                }).collect(Collectors.toList());

        List<San> allSans = Stream.of(sansInCsr, requestDto.getCertificate().getSans())
                .flatMap(list -> list == null ? Stream.empty() : list.stream())
                .collect(Collectors.collectingAndThen(
                        // On utilise la NOUVELLE méthode pour la clé de la map
                        Collectors.toMap(San::getSanValue, san -> san, (existing, replacement) -> existing),
                        map -> new ArrayList<>(map.values())
                ));

        requestDto.getCertificate().setSans(allSans);
        LOGGER.info("ALL SANS = " + allSans.toString());

    } catch (Exception e) {
        LOGGER.error("Error when associate csr Sans :", e);
    }
}
//////////////////////////////////////////////////
public void integrateCsrSans(RequestDto requestDto) {
        try {
            String decodedCsr = new String(Base64.getDecoder().decode(requestDto.getCsr()), StandardCharsets.UTF_8);
            CertificateCsrDecoder csrDecoder = new CertificateCsrDecoder();

            List<San> sansInCsr = csrDecoder.getSansList(decodedCsr).stream()
                    .map(sanString -> {
                        San newSan = new San();
                        newSan.setSanValue(sanString);

                        // --- LOGIQUE DE DÉTECTION COMPLÈTE POUR LES 6 TYPES ---
                        // L'ordre est essentiel : du plus spécifique au plus général.

                        // 1. GUID (32 caractères hexadécimaux)
                        if (sanString.matches("^[a-fA-F0-9]{32}$")) {
                            newSan.setType(SanType.OTHERNAME_GUID);

                        // 2. Adresse IPv4
                        } else if (sanString.matches("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")) {
                            newSan.setType(SanType.IPADDRESS);

                        // 3. URI (commence par un schéma connu)
                        } else if (sanString.matches("^(https?|ldaps?|ftp|file|tag|urn|data|tel):.*")) {
                            newSan.setType(SanType.URI);
                            
                        // 4. UPN (contient un '@' ET le domaine ne ressemble pas à un domaine public)
                        } else if (sanString.contains("@") && (sanString.endsWith(".local") || !sanString.matches(".*\\.(com|org|net|fr|io|dev)$"))) {
                            newSan.setType(SanType.OTHERNAME_UPN);

                        // 5. Adresse E-mail (contient un '@')
                        } else if (sanString.contains("@")) {
                            newSan.setType(SanType.RFC822NAME);

                        // 6. Par défaut, si rien d'autre ne correspond, c'est un DNSNAME
                        } else {
                            newSan.setType(SanType.DNSNAME);
                        }

                        return newSan;
                    }).collect(Collectors.toList());

            // Fusionner et dédupliquer les listes
            List<San> allSans = Stream.of(sansInCsr, requestDto.getCertificate().getSans())
                    .flatMap(list -> list == null ? Stream.empty() : list.stream())
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(San::getSanValue, san -> san, (e, r) -> e),
                            map -> new ArrayList<>(map.values())
                    ));

            requestDto.getCertificate().setSans(allSans);

            LOGGER.info("Intégration des SANs du CSR terminée. Nombre total de SANs : {}", allSans.size());

        } catch (Exception e) {
            LOGGER.error("Erreur lors de l'intégration des SANs du CSR :", e);
        }
    }
}
/////////////////////////////
code
Java
/**
 * Intègre les SANs (Subject Alternative Names) d'un CSR dans un certificat.
 * Cette méthode lit les SANs du CSR, déduit leur type (DNSNAME, IPADDRESS, etc.),
 * puis les fusionne avec les SANs déjà présents dans le certificat, en supprimant les doublons.
 *
 * @param requestDto Le DTO contenant le CSR et le certificat à mettre à jour.
 */
public void integrateCsrSans(RequestDto requestDto) {
    try {
        // 1. Décoder le CSR pour extraire la liste des SANs sous forme de chaînes
        String decodedCsr = new String(Base64.getDecoder().decode(requestDto.getCsr()), StandardCharsets.UTF_8);
        CertificateCsrDecoder csrDecoder = new CertificateCsrDecoder();
        List<String> sanStringsFromCsr = csrDecoder.getSansList(decodedCsr);

        // 2. Transformer chaque chaîne en objet San en déduisant son type
        List<San> sansInCsr = sanStringsFromCsr.stream()
                .map(sanString -> {
                    San newSan = new San();
                    newSan.setSanValue(sanString); // Met à jour 'sanValue' et 'url' si synchronisés

                    // --- LOGIQUE DE DÉTECTION COMPLÈTE POUR LES 6 TYPES ---
                    // L'ordre des vérifications est essentiel, du plus spécifique au plus général.

                    // 1. GUID (32 caractères hexadécimaux)
                    if (sanString.matches("^[a-fA-F0-9]{32}$")) {
                        newSan.setType(SanType.OTHERNAME_GUID);

                    // 2. Adresse IPv4
                    } else if (sanString.matches("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")) {
                        newSan.setType(SanType.IPADDRESS);

                    // 3. URI (commence par un schéma comme http:, ftp:, etc.)
                    } else if (sanString.matches("^(https?|ldaps?|ftp|file|tag|urn|data|tel):.*")) {
                        newSan.setType(SanType.URI);
                        
                    // 4. UPN (contient '@' ET le domaine ne ressemble pas à un domaine public commun)
                    //    Cette règle est une heuristique et peut être ajustée.
                    } else if (sanString.contains("@") && (sanString.endsWith(".local") || !sanString.matches(".*\\.(com|org|net|fr|io|dev|biz|info)$"))) {
                        newSan.setType(SanType.OTHERNAME_UPN);

                    // 5. Adresse E-mail (tous les autres cas contenant '@')
                    } else if (sanString.contains("@")) {
                        newSan.setType(SanType.RFC822NAME);

                    // 6. Par défaut, si rien d'autre ne correspond, c'est un DNSNAME
                    } else {
                        newSan.setType(SanType.DNSNAME);
                    }

                    return newSan;
                }).collect(Collectors.toList());

        // 3. Fusionner la liste des nouveaux SANs avec ceux déjà existants
        List<San> allSans = Stream.of(sansInCsr, requestDto.getCertificate().getSans())
                .flatMap(list -> list == null ? Stream.empty() : list.stream())
                .collect(Collectors.collectingAndThen(
                        // Dédupliquer en se basant sur la valeur du SAN
                        Collectors.toMap(
                                San::getSanValue,               // Clé de la map
                                san -> san,                     // Valeur de la map
                                (existingValue, newValue) -> existingValue // En cas de doublon, on garde l'existant
                        ),
                        map -> new ArrayList<>(map.values()) // On transforme la map finale en une liste
                ));

        // 4. Mettre à jour le certificat avec la liste finale et dédupliquée
        requestDto.getCertificate().setSans(allSans);

        LOGGER.info("Intégration des SANs du CSR terminée. Nombre total de SANs : {}", allSans.size());

    } catch (Exception e) {
        LOGGER.error("Erreur critique lors de l'intégration des SANs à partir du CSR :", e);
        // Vous pourriez vouloir lancer une exception personnalisée ici pour une meilleure gestion d'erreurs
        // throw new SanProcessingException("Failed to process SANs from CSR", e);
    }
}

///////// regex ///////////////////////////////////
package com.bnpparibas.certis.certificate.request.enums;

import java.util.regex.Pattern;

public enum SanType {

    DNSNAME("^[a-zA-Z0-9\\.\\-\\*_]*$"),
    RFC822NAME("^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$"),
    IPADDRESS("^((25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(?!$)|$)){4}$"), // Regex simplifiée pour IPv4, plus lisible. Adaptez si vous avez besoin d'IPv6.
    OTHERNAME_GUID("^[a-fA-F0-9]{32}$"), // GUID/UUID sans tirets
    OTHERNAME_UPN("^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$"), // Même format que RFC822NAME
    URI("^(https?|ldaps?|ftp|file|tag|urn|data|tel):[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");

    private final Pattern pattern;

    SanType(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    /**
     * Valide si la valeur donnée correspond au format attendu pour ce type de SAN.
     * @param value La valeur du SAN à valider.
     * @return true si la valeur est valide, false sinon.
     */
    public boolean isValid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false; // Les valeurs vides ne sont pas valides
        }
        return this.pattern.matcher(value).matches();
    }
}
Note importante : J'ai légèrement simplifié et corrigé certaines regex du ticket pour qu'elles soient plus robustes et standards (notamment pour l'adresse IP). Vous pouvez reprendre celles du ticket à la lettre si nécessaire, en faisant attention à bien échapper les caractères spéciaux en Java (par exemple, \ devient \\).
Étape 2 : Créer un Service de Validation
Ce service aura pour seule responsabilité de valider une liste de SANs.
Créez un nouveau fichier SanValidatorService.java dans votre package service ou validator :
code
Java
package com.bnpparibas.certis.service; // Ou le package approprié

import com.bnpparibas.certis.certificate.request.model.San;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SanValidatorService {

    /**
     * Valide une liste d'objets San, en vérifiant que chaque valeur correspond au format de son type.
     * @param sans La liste des SANs à valider.
     * @throws IllegalArgumentException si un SAN est invalide.
     */
    public void validateSans(List<San> sans) {
        if (sans == null || sans.isEmpty()) {
            return; // Rien à valider
        }

        for (San san : sans) {
            if (san.getType() == null) {
                throw new IllegalArgumentException("Le type du SAN ne peut pas être nul pour la valeur : '" + san.getSanValue() + "'");
            }

            if (!san.getType().isValid(san.getSanValue())) {
                // Lève une exception explicite qui sera interceptée et renverra une erreur 400 (Bad Request)
                throw new IllegalArgumentException(
                    "Format invalide pour le SAN de type " + san.getType().name() + ". La valeur fournie est : '" + san.getSanValue() + "'"
                );
            }
        }
    }
}
Étape 3 : Intégrer la validation dans votre logique métier
Maintenant, il suffit d'appeler ce nouveau service au bon endroit, c'est-à-dire avant de sauvegarder les données en base. Par exemple, dans votre RequestServiceImpl :
code
Java
// Dans votre classe RequestServiceImpl.java (ou autre service principal)

@Service
public class RequestServiceImpl implements RequestService {

    // ... autres injections (repository, etc.)
    
    private final SanValidatorService sanValidatorService;

    // Injection via le constructeur (meilleure pratique)
    @Autowired
    public RequestServiceImpl(SanValidatorService sanValidatorService /*, ... autres services */) {
        this.sanValidatorService = sanValidatorService;
        // ...
    }
    
    public void createOrUpdateRequest(RequestDto requestDto) {
        // ... autre logique de préparation du DTO
        
        // --- APPEL DE LA VALIDATION ---
        // On récupère la liste complète des SANs (après fusion avec le CSR par exemple)
        List<San> finalSanList = requestDto.getCertificate().getSans();
        
        // On valide la liste AVANT toute opération de sauvegarde
        sanValidatorService.validateSans(finalSanList);

        // Si la validation passe, le code continue.
        // Sinon, une exception est levée et le traitement s'arrête.
        
        // ... suite de la méthode (sauvegarde en base, etc.)
        certificateRepository.save(requestDto.getCertificate());
    }
}
////////////////////////// detection de type ///////////////////
public SanType deduceSanTypeFromString(String sanValue) {
    if (sanValue == null || sanValue.trim().isEmpty()) {
        throw new IllegalArgumentException("La valeur du SAN ne peut pas être nulle ou vide.");
    }

    // 1. GUID
    if (sanValue.matches("^[a-fA-F0-9]{32}$")) {
        return SanType.OTHERNAME_GUID;
    }
    // 2. IP Address
    if (sanValue.matches("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")) {
        return SanType.IPADDRESS;
    }
    // 3. URI
    if (sanValue.matches("^(https?|ldaps?|ftp|file|tag|urn|data|tel):.*")) {
        return SanType.URI;
    }
    // 4. UPN (heuristique)
    if (sanValue.contains("@") && (sanValue.endsWith(".local") || !sanValue.matches(".*\\.(com|org|net|fr|io|dev|biz|info)$"))) {
        return SanType.OTHERNAME_UPN;
    }
    // 5. Email
    if (sanValue.contains("@")) {
        return SanType.RFC822NAME;
    }
    // 6. Par défaut : DNSNAME
    return SanType.DNSNAME;
}
Étape 2 : Mettre à jour votre méthode getAutoEnrollSans
Maintenant, modifiez la méthode getAutoEnrollSans pour qu'elle utilise ce nouvel utilitaire. Vous devrez probablement injecter UtilityService dans votre classe AutoEnrollService.
Voici le code complet et corrigé de votre méthode getAutoEnrollSans :
code
Java
// Dans votre classe AutoEnrollService.java

// ... Assurez-vous d'injecter UtilityService
private final UtilityService utilityService;

@Autowired
public AutoEnrollService(UtilityService utilityService) {
    this.utilityService = utilityService;
}


/**
 * Convertit une liste de SanDto en une liste d'entités San, en déduisant le type de chaque SAN.
 */
private List<San> getAutoEnrollSans(List<SanDto> subjectAlternateNames) {
    List<San> sanList = new ArrayList<>();

    if (subjectAlternateNames != null && !subjectAlternateNames.isEmpty()) {
        for (SanDto sanAutoEnroll : subjectAlternateNames) {
            String sanValue = sanAutoEnroll.getSanValue(); // Récupère la valeur

            if (sanValue != null && !sanValue.trim().isEmpty()) {
                San san = new San();
                
                // On utilise la nouvelle méthode pour mettre à jour la valeur
                san.setSanValue(sanValue);

                // On appelle la méthode utilitaire pour déduire et assigner le type
                SanType detectedType = utilityService.deduceSanTypeFromString(sanValue);
                san.setType(detectedType);

                sanList.add(san);
            }
        }
    }
    
    return sanList;
}
//////// nouveau dans utility/////////////
public SanType deduceSanTypeFromString(String sanValue) {
    if (sanValue == null || sanValue.trim().isEmpty()) {
        throw new IllegalArgumentException("La valeur du SAN ne peut pas être nulle ou vide.");
    }

    // L'ordre des vérifications est crucial : du plus spécifique au plus général.
    
    // 1. GUID (32 caractères hexadécimaux)
    if (sanValue.matches("^[a-fA-F0-9]{32}$")) {
        return SanType.OTHERNAME_GUID;
    }
    // 2. Adresse IPv4
    if (sanValue.matches("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")) {
        return SanType.IPADDRESS;
    }
    // 3. URI (commence par un schéma comme http:, ftp:, etc.)
    if (sanValue.matches("^(https?|ldaps?|ftp|file|tag|urn|data|tel):.*")) {
        return SanType.URI;
    }
    // 4. UPN (heuristique : contient '@' et un domaine non-public)
    if (sanValue.contains("@") && (sanValue.endsWith(".local") || !sanValue.matches(".*\\.(com|org|net|fr|io|dev|biz|info)$"))) {
        return SanType.OTHERNAME_UPN;
    }
    // 5. Adresse E-mail (tous les autres cas contenant '@')
    if (sanValue.contains("@")) {
        return SanType.RFC822NAME;
    }
    // 6. Par défaut : DNSNAME
    return SanType.DNSNAME;
}


// 2. La méthode qui utilise la logique centralisée
// ====================================================

/**
 * Intègre les SANs (Subject Alternative Names) d'un CSR dans un certificat.
 * Cette méthode est refactorisée pour utiliser la logique centralisée de 
 * {@link #deduceSanTypeFromString(String)} afin d'éviter la duplication de code.
 *
 * @param requestDto Le DTO contenant le CSR et le certificat à mettre à jour.
 */
public void integrateCsrSans(RequestDto requestDto) {
    try {
        // Décoder le CSR et extraire les SANs
        String decodedCsr = new String(Base64.getDecoder().decode(requestDto.getCsr()), StandardCharsets.UTF_8);
        CertificateCsrDecoder csrDecoder = new CertificateCsrDecoder();
        List<String> sanStringsFromCsr = csrDecoder.getSansList(decodedCsr);

        // Transformer chaque chaîne en objet San en utilisant la méthode de déduction
        List<San> sansInCsr = sanStringsFromCsr.stream()
                .map(sanString -> {
                    San newSan = new San();
                    newSan.setSanValue(sanString);
                    
                    // Appel à la méthode centralisée pour déduire le type
                    newSan.setType(this.deduceSanTypeFromString(sanString));
                    
                    return newSan;
                }).collect(Collectors.toList());

        // Fusionner et dédupliquer les listes de SANs
        List<San> allSans = Stream.of(sansInCsr, requestDto.getCertificate().getSans())
                .flatMap(list -> list == null ? Stream.empty() : list.stream())
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(San::getSanValue, san -> san, (existing, replacement) -> existing),
                        map -> new ArrayList<>(map.values())
                ));

        // Mettre à jour le certificat avec la liste finale
        requestDto.getCertificate().setSans(allSans);

        LOGGER.info("Intégration des SANs du CSR terminée. Nombre total de SANs : {}", allSans.size());

    } catch (Exception e) {
        LOGGER.error("Erreur critique lors de l'intégration des SANs à partir du CSR :", e);
    }
}
///////////////////////// 2.2////////////////////
/**
 * Convertit une liste de SanDto en une liste d'entités San.
 * Cette méthode est adaptée pour utiliser la logique de déduction de type centralisée
 * afin d'assigner le SanType correct à chaque nouvelle entité San.
 *
 * @param subjectAlternateNames La liste de DTOs contenant les valeurs des SANs.
 * @return Une liste d'entités {@link San} prêtes à être persistées.
 */
private List<San> getAutoEnrollSans(List<SanDto> subjectAlternateNames) {
    // Si la liste d'entrée est nulle ou vide, on retourne une liste vide tout de suite.
    if (subjectAlternateNames == null || subjectAlternateNames.isEmpty()) {
        return new ArrayList<>();
    }

    // On utilise les Streams pour un code plus concis et moderne.
    return subjectAlternateNames.stream()
            // On s'assure de ne pas traiter des DTOs nuls ou avec des valeurs vides.
            .filter(dto -> dto != null && dto.getSanValue() != null && !dto.getSanValue().trim().isEmpty())
            // On transforme chaque DTO valide en une entité San.
            .map(sanDto -> {
                San san = new San();
                String sanValue = sanDto.getSanValue();

                // ÉTAPE 1: Utiliser le nouveau setter 'setSanValue'.
                // Si vous avez la synchronisation, 'url' sera aussi mis à jour.
                san.setSanValue(sanValue);

                // ÉTAPE 2: Appeler la méthode utilitaire pour déduire et assigner le type.
                // C'est l'étape la plus importante de la correction.
                san.setType(utilityService.deduceSanTypeFromString(sanValue));

                return san;
            })
            // On collecte les résultats dans une nouvelle liste.
            .collect(Collectors.toList());
}
/////////////////////////////
private List<San> getAutoEnrollSans(List<SanDto> subjectAlternateNames) {
    if (subjectAlternateNames == null || subjectAlternateNames.isEmpty()) {
        return new ArrayList<>();
    }

    return subjectAlternateNames.stream()
            .filter(dto -> dto != null && dto.getSanValue() != null && !dto.getSanValue().trim().isEmpty())
            .map(sanDto -> {
                San san = new San();
                String sanValue = sanDto.getSanValue();
                
                san.setSanValue(sanValue);
                san.setType(utilityService.deduceSanTypeFromString(sanValue));
                
                return san;
            })
            .collect(Collectors.toList());
}
////////////////
private void addSansCertis(RequestDto request, RequestDto requestCertis) {
    // Vérifie si le certificat source et sa liste de SANs existent et ne sont pas vides.
    if (requestCertis.getCertificate() != null &&
        requestCertis.getCertificate().getSans() != null &&
        !requestCertis.getCertificate().getSans().isEmpty()) {

        List<San> sans = new ArrayList<>();
        
        for (San sanCertis : requestCertis.getCertificate().getSans()) {
            // Créer une nouvelle instance de San pour la destination.
            San san = new San();

            // CORRECTION ÉTAPE 1 : On copie la VALEUR en utilisant les nouvelles méthodes.
            san.setSanValue(sanCertis.getSanValue());

            // CORRECTION ÉTAPE 2 : ET SURTOUT, on copie le TYPE. C'est la correction la plus importante.
            san.setType(sanCertis.getType());

            sans.add(san);
        }

        // Assigner la nouvelle liste de SANs copiés au certificat de destination.
        request.getCertificate().setSans(sans);
    }
}
/////////////////////////////////////////
public RequestDto buildSANs(RequestDto requestDto) {
    try {
        String typeCert = requestDto.getCertificate().getCertificateType().getName();

        // La condition pour exécuter la logique reste la même
        if (RequestTypeList.SSL_CLIENT_SERVER_EXTERNE.toString().equals(typeCert)
                || RequestTypeList.SSL_CLIENT_SERVER.toString().equals(typeCert)
                || RequestTypeList.SSL_SERVER.toString().equals(typeCert)) {

            // 1. Récupérer la liste existante et gérer le cas où elle est nulle
            List<San> sanList = requestDto.getCertificate().getSans();
            if (sanList == null) {
                sanList = new ArrayList<>();
            }

            // 2. Créer un Set avec les valeurs des SANs existants pour des recherches rapides et efficaces
            Set<String> existingSanValues = sanList.stream()
                    .map(San::getSanValue)
                    .collect(Collectors.toSet());

            // 3. Préparer les nouveaux SANs potentiels à partir du Common Name
            String cn = requestDto.getCertificate().getCommonName();
            if (cn != null && !cn.isEmpty()) {
                String domain = cn.toLowerCase();
                String domainWWW = domain.startsWith("www.") ? domain : "www." + domain;
                if(domain.startsWith("www.")){
                    domain = domain.replaceFirst("www.","");
                }


                // 4. Ajouter le domaine principal s'il n'est pas déjà dans la liste
                if (!existingSanValues.contains(domain)) {
                    San sanDomain = new San();
                    sanDomain.setSanValue(domain);
                    sanDomain.setType(SanType.DNSNAME); // <-- CORRECTION CRUCIALE
                    sanList.add(sanDomain);
                }

                // 5. Ajouter le domaine avec "www" s'il n'est pas déjà dans la liste
                if (!existingSanValues.contains(domainWWW)) {
                    San sanDomainWWW = new San();
                    sanDomainWWW.setSanValue(domainWWW);
                    sanDomainWWW.setType(SanType.DNSNAME); // <-- CORRECTION CRUCIALE
                    sanList.add(sanDomainWWW);
                }
            }

            // 6. Mettre à jour le certificat avec la liste potentiellement étendue
            requestDto.getCertificate().setSans(sanList);
        }
    } catch (Exception e) {
        LOGGER.error("Exception in buildSANs {}", e.getMessage());
    }
    return requestDto;
}
////////////////////////////
 * Vérifie que les SANs de type DNSNAME ne contiennent pas de caractère underscore ('_').
 * Les autres types de SANs (comme URI) sont ignorés par cette vérification car ils peuvent
 * légitimement contenir des underscores.
 *
 * @param sans La liste des SANs à valider.
 * @return true si aucun DNSNAME ne contient d'underscore, false sinon.
 */
protected boolean doNotContainsUnderscore(List<San> sans) {
    if (CollectionUtils.isEmpty(sans)) {
        return true; // Une liste vide est valide.
    }

    for (San san : sans) {
        // On applique la règle UNIQUEMENT pour les types DNSNAME.
        if (SanType.DNSNAME.equals(san.getType())) {
            // On utilise la nouvelle méthode getSanValue().
            if (san.getSanValue() != null && san.getSanValue().contains("_")) {
                // Dès qu'on trouve un DNSNAME invalide, on arrête et on retourne false.
                return false;
            }
        }
    }

    // Si on a parcouru toute la liste sans trouver de DNSNAME invalide, c'est bon.
    return true;
}
//////////////////////////////
@Mapper(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL)
public interface FilterRequestMapper {

    @Mapping(source = "requestStatus", target = "status")
    FilterRequestDto requestToFilterRequestDto(Request request);

    FilterRequestStatusDto requestStatusToFilterRequestStatusDto(RequestStatus requestStatus);

    @Mapping(source = "commonName", target = "cn")
    @Mapping(source = "certisEntity", target = "entity")
    @Mapping(source = "certificateStatus", target = "status")
    @Mapping(source = "gnsCountry", target = "country")
    @Mapping(source = "sans", target = "san")
    FilterCertificateDto certificateToCertificateDto(Certificate certificate);

    FilterCertificateStatusDto certificateStatusToFilterCertificateStatusDto(CertificateStatus certificateStatus);

    FilterCertificateTypeDto certificateTypeToFilterCertificateTypeDto(CertificateType certificateType);

    FilterPlatformDto platformToFilterPlatformDto(Platform platform);
    
    // ... (les autres méthodes que vous aviez déjà)

    // =====================================================================
    // === MÉTHODE À AJOUTER POUR ADAPTER VOTRE NOUVELLE ENTITÉ SAN ===
    // =====================================================================
    /**
     * Mappe une entité San vers son DTO FilterSanDto.
     * Cette méthode assure la rétrocompatibilité en mappant le nouveau champ 'sanValue'
     * vers l'ancien champ 'url' du DTO, comme demandé dans le ticket Jira.
     * Elle mappe également le nouveau champ 'type'.
     */
    @Mapping(source = "sanValue", target = "url")
    @Mapping(source = "type", target = "type")
    FilterSanDto sanToFilterSanDto(San san);

}
package com.bnpparibas.certis.certificate.request.mapper;

import com.bnpparibas.certis.automationhub.dto.business.SanDto; // Importez votre SanDto
import com.bnpparibas.certis.automationhub.dto.business.enums.SanTypeEnum; // Importez votre SanTypeEnum
import com.bnpparibas.certis.certificate.request.dto.filter.*;
import com.bnpparibas.certis.certificate.request.model.*;
import com.bnpparibas.certis.certificate.request.model.enums.SanType; // Importez votre SanType d'entité
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL)
public interface FilterRequestMapper {

    @Mapping(source = "requestStatus", target = "status")
    FilterRequestDto requestToFilterRequestDto(Request request);

    FilterRequestStatusDto requestStatusToFilterRequestStatusDto(RequestStatus requestStatus);

    @Mapping(source = "commonName", target = "cn")
    @Mapping(source = "certisEntity", target = "entity")
    @Mapping(source = "certificateStatus", target = "status")
    @Mapping(source = "gnsCountry", target = "country")
    @Mapping(source = "sans", target = "san")
    FilterCertificateDto certificateToCertificateDto(Certificate certificate);

    FilterCertificateStatusDto certificateStatusToFilterCertificateStatusDto(CertificateStatus certificateStatus);

    FilterCertificateTypeDto certificateTypeToFilterCertificateTypeDto(CertificateType certificateType);

    FilterPlatformDto platformToFilterPlatformDto(Platform platform);
    
    // ... (les autres méthodes de votre interface que je n'ai pas reprises)

    // =====================================================================
    // ===       SECTION AJOUTÉE POUR LA GESTION DE San ET SanDto        ===
    // =====================================================================

    /**
     * Mappe une entité San vers son DTO FilterSanDto pour la rétrocompatibilité.
     */
    @Mapping(source = "sanValue", target = "url")
    @Mapping(source = "type", target = "sanType")
    FilterSanDto sanToFilterSanDto(San san);

    /**
     * Mappe une entité San vers son DTO SanDto.
     * Le champ 'sanValue' est mappé automatiquement.
     * Le champ 'type' est mappé vers 'sanType' en utilisant la méthode de conversion ci-dessous.
     */
    @Mapping(source = "type", target = "sanType")
    SanDto toSanDto(San san);
    
    // --- Méthodes de conversion personnalisées pour les énumérations ---

    /**
     * Convertit l'énumération de l'entité (SanType) vers l'énumération du DTO (SanTypeEnum).
     * MapStruct trouvera et utilisera cette méthode automatiquement quand il en aura besoin.
     */
    default SanTypeEnum toSanTypeEnum(SanType sanType) {
        if (sanType == null) {
            return null;
        }
        return SanTypeEnum.valueOf(sanType.name());
    }
    
    /**
     * Convertit l'énumération du DTO (SanTypeEnum) vers l'énumération de l'entité (SanType).
     * Utile si vous avez besoin de faire la conversion dans l'autre sens.
     */
    default SanType toSanType(SanTypeEnum sanTypeEnum) {
        if (sanTypeEnum == null) {
            return null;
        }
        return SanType.valueOf(sanTypeEnum.name());
    }
}