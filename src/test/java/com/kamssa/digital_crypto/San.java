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
/////////////////////////

@Mapper(componentModel = "spring")
public abstract class RequestDtoToAutomationHubRequestDtoMapper {

    @Autowired
    private FileManagerService fileManagerService;
    @Autowired
    private EncryptorService encryptorService;

    // La méthode de mapping principale reste inchangée
    @Mappings({
        @Mapping(source = "id", target = "certisId"),
        @Mapping(source = "certificate.automationHubCertificateId", target = "automationHubId"),
        @Mapping(source = "certificate.commonName", target = "commonName"),
        // ... (tous vos autres @Mapping)
        @Mapping(source = "certificate.sans", target = "sanList") // Cette ligne utilise les méthodes ci-dessous
    })
    public abstract AutomationHubRequestDto toAutomationHubRequestDto(RequestDto requestDto);


    // MapStruct génère une boucle qui appellera 'toSanDto' pour chaque élément.
    protected abstract List<SanDto> toSanDtoList(List<San> sourceSans);


    // =====================================================================
    // ===          C'EST CETTE MÉTHODE QUI EST CORRIGÉE                 ===
    // =====================================================================
    /**
     * Mappe une entité San vers son DTO SanDto.
     * Cette version corrigée copie dynamiquement le type et utilise le nouveau champ 'sanValue'.
     */
    protected SanDto toSanDto(San sourceSan) {
        if (sourceSan == null) {
            return null;
        }

        SanDto res = new SanDto();

        // CORRECTION 1 : On copie la VALEUR en utilisant la nouvelle méthode.
        res.setSanValue(sourceSan.getSanValue());

        // CORRECTION 2 : On copie le TYPE en le convertissant correctement.
        res.setSanType(toSanTypeEnum(sourceSan.getType()));

        return res;
    }


    // =====================================================================
    // ===    MÉTHODE DE CONVERSION AJOUTÉE POUR GÉRER LES ENUMS       ===
    // =====================================================================
    /**
     * Convertit l'énumération de l'entité (SanType) vers l'énumération du DTO (SanTypeEnum).
     */
    private SanTypeEnum toSanTypeEnum(SanType sanType) {
        if (sanType == null) {
            return null;
        }
        try {
            return SanTypeEnum.valueOf(sanType.name());
        } catch (IllegalArgumentException e) {
            // Gérer le cas où les enums ne sont pas synchronisés
            LOGGER.warn("Impossible de mapper SanType '{}' vers SanTypeEnum.", sanType.name());
            return null;
        }
    }


    // Les autres méthodes @Named restent inchangées
    @Named("mapCsrPathToCsrContent")
    String mapCsrPathToCsrContent(RequestDto requestDto) {
        // ...
    }

    @Named("decryptPassword")
    String decryptPassword(String cryptedPassword) {
        // ...
    }
}
///////////////////////
private RequestDto evaluateSan3W(RequestDto requestDto) {
    List<San> sanList = requestDto.getCertificate().getSans();

    // On ne fait rien si la liste est nulle (ou vide, bien que le code original ne le vérifiait pas).
    if (sanList == null) {
        return requestDto;
    }

    String cn = requestDto.getCertificate().getCommonName();
    if (cn != null && !cn.trim().isEmpty()) {
        String lowerCaseCn = cn.toLowerCase().trim();
        
        // Logique clarifiée pour toujours obtenir la version "www."
        String domain = lowerCaseCn.startsWith("www.") ? lowerCaseCn.substring(4) : lowerCaseCn;
        String domainWWW = "www." + domain;

        // OPTIMISATION : Utiliser un Set pour une recherche d'existence rapide.
        Set<String> existingSanValues = sanList.stream()
                .map(San::getSanValue) // MIGRATION : Utilisation de getSanValue()
                .collect(Collectors.toSet());

        // Si la version "www" n'existe pas, on l'ajoute.
        if (!existingSanValues.contains(domainWWW)) {
            San sanDomainWWW = new San();
            
            // CORRECTION 1 (MIGRATION) : Utilisation de setSanValue()
            sanDomainWWW.setSanValue(domainWWW);

            // CORRECTION 2 (CRITIQUE) : Assignation du type obligatoire
            sanDomainWWW.setType(SanType.DNSNAME);

            // Le code original ajoutait l'élément à l'index 0. On respecte ce comportement.
            sanList.add(0, sanDomainWWW);

            // On met à jour la liste dans le certificat (même si c'est la même instance de liste, c'est une bonne pratique).
            requestDto.getCertificate().setSans(sanList);
        }
    }

    return requestDto;
}
////////////////////////
public List<String> validateSansOnRefweb(RequestDto requestDto) throws Exception {
    List<String> sansInvalid = new ArrayList<>();
    
    if (hasNoSans(requestDto) || isWhitelistedPlatform(requestDto)) {
        return Collections.emptyList();
    }
    
    // CORRECTION ICI : Remplacer getUrl() par getSanValue()
    List<String> urls = requestDto.getCertificate().getSans().stream()
            .map(San::getSanValue) // <--- LA CORRECTION
            .collect(Collectors.toList());

    for (String sanUrl : urls) {
        if (!this.checkSanUrlOnRefweb(requestDto, sanUrl)) {
            sansInvalid.add(sanUrl);
        }
    }
    
    return sansInvalid;
}
////////1431////////////////////////////

@Autowired
private CertificateCsrDecoder certificateCsrDecoder;
Créer une nouvelle méthode PUBLIQUE : Ajoutez une nouvelle méthode qui prendra un CertificateDto et un csr en entrée, et qui retournera le CertificateDto modifié. C'est ici que vous placerez votre logique.
code
Java
// N'importe où dans le corps de la classe CertificateServiceImpl.java

/**
 * Intègre les SANs d'un CSR dans un CertificateDto existant,
 * en fusionnant avec les SANs déjà présents et en supprimant les doublons.
 * @param certificateDto Le DTO du certificat à modifier.
 * @param csrBase64 Le CSR encodé en Base64.
 * @return Le CertificateDto mis à jour avec la liste complète et propre des SANs.
 */
public CertificateDto integrateSansFromCsr(CertificateDto certificateDto, String csrBase64) {
    if (csrBase64 == null || csrBase64.isEmpty()) {
        return certificateDto; // Ne rien faire si pas de CSR
    }

    try {
        String decodedCsr = new String(Base64.getDecoder().decode(csrBase64), StandardCharsets.UTF_8);
        
        // 1. Extraire les SANs du CSR
        List<String> sanStringsFromCsr = this.certificateCsrDecoder.getSansList(decodedCsr);
        List<San> sansInCsr = sanStringsFromCsr.stream()
            .map(sanString -> {
                San newSan = new San();
                newSan.setValue(sanString);
                // newSan.setType(this.deduceSanTypeFromString(sanString)); // Adaptez si cette méthode est ailleurs
                return newSan;
            })
            .collect(Collectors.toList());

        // 2. Récupérer les SANs déjà dans le DTO (ceux du formulaire)
        List<San> sansFromDto = certificateDto.getSans() != null ? certificateDto.getSans() : new ArrayList<>();

        // 3. Fusionner les deux listes et supprimer les doublons
        List<San> allSans = Stream.concat(sansInCsr.stream(), sansFromDto.stream())
            .collect(Collectors.collectingAndThen(
                Collectors.toMap(San::getValue, san -> san, (existing, replacement) -> existing),
                map -> new ArrayList<>(map.values())
            ));

        // 4. Mettre à jour le DTO
        certificateDto.setSans(allSans);
        
        // LOGGER.info(...) si vous avez un logger disponible

    } catch (Exception e) {
        // LOGGER.error(...)
        // Gérer l'exception comme il se doit dans votre application
    }

    return certificateDto;
}
À ce stade, vous n'avez rien cassé. Vous avez juste ajouté une nouvelle méthode qui n'est appelée par personne.
Étape 2 : Appeler cette nouvelle méthode depuis RequestServiceImpl.java
Maintenant, nous retournons dans le "gros" fichier RequestServiceImpl.java pour faire une modification minimale.
Injecter CertificateService : Assurez-vous que RequestServiceImpl a accès à CertificateService.
code
Java
// En haut de RequestServiceImpl.java

@Autowired
private CertificateService certificateService;
Faire l'appel dans createRequest : Trouvez la méthode createRequest et, juste avant la logique de traitement ou la conversion en entité, appelez votre nouvelle méthode.
code
Java
// Dans RequestServiceImpl.java

@Override
public RequestDto createRequest(RequestDto requestDto) {
    // ... autre logique de validation au début de la méthode ...

    // === MODIFICATION MINIMALE ET SÉCURISÉE ===
    // On délègue la logique complexe de préparation du certificat au service spécialisé.
    this.certificateService.integrateSansFromCsr(
        requestDto.getCertificate(), 
        requestDto.getCsr()
    );
    // ===========================================

    Request request = dtoToEntity(requestDto); // Continue le processus normal
    
    // ... le reste de la méthode existante ...
    
    requestDao.save(request);
    return entityToDto(request);
}
/////////////////////////
if (requestDto.getCsr() != null && !requestDto.getCsr().isEmpty()) {
        try {
            this.certificateService.integrateSansFromCsr(
                requestDto.getCertificate(), 
                requestDto.getCsr()
            );
            LOGGER.info("Intégration des SANs du CSR terminée avec succès.");
        } catch (Exception e) {
            LOGGER.error("Impossible d'intégrer les SANs à partir du CSR fourni.", e);
            // Selon votre gestion d'erreur, vous pourriez vouloir lancer une exception ici
            // throw new CertisRequestException("error.csr.san.integration.failed", e);
        }
    }
	///////////////////
	public Certificate integrateSansFromCsr(Certificate certificate, String csrBase64) {
    if (csrBase64 == null || csrBase64.isEmpty() || certificate == null) {
        return certificate; // Ne rien faire si les entrées sont invalides
    }

    try {
        String decodedCsr = new String(Base64.getDecoder().decode(csrBase64), StandardCharsets.UTF_8);

        // 1. Extraire les SANs du CSR en objets San
        List<String> sanStringsFromCsr = this.certificateCsrDecoder.getSansList(decodedCsr);
        List<San> sansInCsr = sanStringsFromCsr.stream()
            .map(sanString -> {
                San newSan = new San();
                newSan.setValue(sanString);
                // newSan.setType(...); // Votre logique pour déduire le type
                return newSan;
            })
            .collect(Collectors.toList());

        // 2. Récupérer les SANs déjà dans l'ENTITÉ
        List<San> sansFromEntity = certificate.getSans() != null ? certificate.getSans() : new ArrayList<>();

        // 3. Fusionner les deux listes et supprimer les doublons
        List<San> allSans = Stream.concat(sansInCsr.stream(), sansFromEntity.stream())
            .collect(Collectors.collectingAndThen(
                Collectors.toMap(San::getValue, san -> san, (existing, replacement) -> existing),
                map -> new ArrayList<>(map.values())
            ));

        // 4. Mettre à jour l'ENTITÉ directement
        certificate.setSans(allSans);

    } catch (Exception e) {
        LOGGER.error("Erreur lors de l'intégration des SANs à partir du CSR.", e);
        // Vous pourriez vouloir relancer une exception personnalisée ici
    }

    return certificate;
}
//////////////////////////////
protected List<EnrollPayloadTemplateSanDto> buildSan() {
    // Si la liste de SANs est vide ou nulle, retourner une liste vide.
    if (automationHubRequestDto.getSanList() == null || automationHubRequestDto.getSanList().isEmpty()) {
        return new ArrayList<>();
    }

    // Transformer directement la liste de SanDto en une liste de EnrollPayloadTemplateSanDto.
    // C'est une simple "copie" sans recalcul.
    return automationHubRequestDto.getSanList().stream()
        .map(sanDto -> {
            EnrollPayloadTemplateSanDto payloadSan = new EnrollPayloadTemplateSanDto();
            payloadSan.setType(sanDto.getSanType().toString());
            // Attention : Le payload semble attendre une List<String> pour la valeur.
            // Il faut donc encapsuler la valeur dans une liste.
            payloadSan.setValue(Collections.singletonList(sanDto.getSanValue())); 
            return payloadSan;
        })
        .collect(Collectors.toList());
}
////////////////////////////
// Pseudocode dans une classe comme CertificateServiceImpl

public void updateCertificate(Certificate certToUpdate, String nouveauCommentaireCertificat, User author) {
    
    // ÉTAPE 1: Mettre à jour le commentaire du certificat.
    // C'est un simple remplacement de valeur.
    certToUpdate.setComment(nouveauCommentaireCertificat);

    // ... autre logique de mise à jour pour le certificat ...
    
    // SAUVEGARDER le certificat avec son nouveau commentaire
    certificateRepository.save(certToUpdate);

    // ÉTAPE 2: Appeler le CommentServiceImpl pour tracer l'action
    // dans l'historique de la Request associée.
    String messagePourHistorique = "Le commentaire du certificat a été modifié.";
    
    // 'commentService' est votre CommentServiceImpl injecté ici
    commentService.addCommentToRequest(
        certToUpdate.getRequest(), // Récupérer la request liée au certificat
        messagePourHistorique, 
        author.getName()
    );
}
///////////////////////////////
// ==================== CODE À AJOUTER ====================

// Vérifie si le commentaire du certificat a été modifié
if (updateRequest.getCertificate() != null && !StringUtils.equalsIgnoreCase(updateRequest.getCertificate().getComment(), previousCertificate.getComment())) {
    
    // ACTION N°1 : Mettre à jour le commentaire sur le certificat (remplacement de la valeur)
    previousCertificate.setComment(updateRequest.getCertificate().getComment());

    // ACTION N°2 : Ajouter la trace de cette modification à l'historique
    traceModification += " Certificate comment has been updated; "; 
    
    /* Optionnel : si vous voulez que le nouveau commentaire apparaisse dans l'historique, utilisez cette ligne à la place :
    traceModification += " Certificate comment set to: '" + updateRequest.getCertificate().getComment() + "'; ";
    */
}
//////////////////////
// ==================== CODE À AJOUTER ICI ====================

// Vérifie si le commentaire du certificat a été modifié
if (updateRequest.getCertificate() != null && !StringUtils.equalsIgnoreCase(updateRequest.getCertificate().getComment(), previousCertificate.getComment())) {
    
    // ACTION N°1 : Mettre à jour le commentaire sur le certificat (remplacement)
    previousCertificate.setComment(updateRequest.getCertificate().getComment());

    // ACTION N°2 : Ajouter la trace de cette modification à l'historique
    traceModification += " Certificate comment has been updated; "; 
}

// =============================================================

// La ligne suivante existe déjà, ne la touchez pas
this.commentService.processComment(previousRequest, oldRequestDto, null, username, traceModification);

///////////////////////
if (!StringUtils.equalsIgnoreCase(updateRequest.getComment(), previousCertificate.getComment())) {
    
    // ACTION N°1 : Mettre à jour le commentaire sur le certificat (remplacement de la valeur)
    previousCertificate.setComment(updateRequest.getComment());

    // ACTION N°2 : Ajouter la trace de cette modification à l'historique
    traceModification += " Certificate comment has been updated; ";
}
///////////////////////////// resoudre lire San //////////////
// N'oubliez pas ces imports en haut de votre fichier
import com.bnpparibas.certis.certificate.request.model.dto.SanDto;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERTaggedObject;
// ...

// Dans la classe CertificateCsrDecoder
public List<SanDto> extractSansWithTypesFromCsr(String csrPem) throws Exception {
    if (StringUtils.isEmpty(csrPem)) {
        return new ArrayList<>();
    }

    PKCS10CertificationRequest csr = this.csrPemToPKCS10(csrPem);
    if (csr == null) {
        return new ArrayList<>();
    }

    List<SanDto> sanDtoList = new ArrayList<>();

    Attribute[] attributes = csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);

    if (attributes != null && attributes.length > 0) {
        Extensions extensions = Extensions.getInstance(attributes[0].getAttrValues().getObjectAt(0));
        Extension sanExtension = extensions.getExtension(Extension.subjectAlternativeName);

        if (sanExtension != null) {
            GeneralNames generalNames = GeneralNames.getInstance(sanExtension.getParsedValue());
            for (GeneralName name : generalNames.getNames()) {
                SanDto sanDto = new SanDto();
                
                switch (name.getTagNo()) {
                    case GeneralName.dNSName:
                        sanDto.setSanType(SanTypeEnum.DNSNAME);
                        sanDto.setSanValue(name.getName().toString());
                        sanDtoList.add(sanDto);
                        break;

                    case GeneralName.iPAddress:
                        sanDto.setSanType(SanTypeEnum.IPADDRESS);
                        sanDto.setSanValue(name.getName().toString());
                        sanDtoList.add(sanDto);
                        break;
                    
                    case GeneralName.rfc822Name:
                        sanDto.setSanType(SanTypeEnum.RFC822NAME);
                        sanDto.setSanValue(name.getName().toString());
                        sanDtoList.add(sanDto);
                        break;
                        
                    case GeneralName.uniformResourceIdentifier:
                        sanDto.setSanType(SanTypeEnum.URI);
                        sanDto.setSanValue(name.getName().toString());
                        sanDtoList.add(sanDto);
                        break;

                    case GeneralName.otherName:
                        ASN1Sequence otherNameSequence = ASN1Sequence.getInstance(name.getName());
                        ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) otherNameSequence.getObjectAt(0);
                        
                        if (otherNameSequence.size() > 1) {
                            ASN1Encodable valueEncodable = ((DERTaggedObject) otherNameSequence.getObjectAt(1)).getObject();
                            String otherNameValue = valueEncodable.toString();

                            final String upnOid = "1.3.6.1.4.1.311.20.2.3";
                            final String guidOid = "1.3.6.1.4.1.311.25.1"; // OID d'exemple, à vérifier

                            if (upnOid.equals(oid.getId())) {
                                sanDto.setSanType(SanTypeEnum.OTHERNAME_UPN);
                                sanDto.setSanValue(otherNameValue);
                                sanDtoList.add(sanDto);
                            } else if (guidOid.equals(oid.getId())) {
                                sanDto.setSanType(SanTypeEnum.OTHERNAME_GUID);
                                sanDto.setSanValue(otherNameValue);
                                sanDtoList.add(sanDto);
                            }
                        }
                        break;
                }
            }
        }
    }
    return sanDtoList;
}
Fichier 3 : RequestServiceImpl.java (L'Orchestrateur)
Chemin : .../certificate/request/service/impl/RequestServiceImpl.java
Pourquoi cette modification ? C'est le cœur de l'intégration. On modifie la méthode createRequest pour qu'elle prépare la liste de SANs finale (fusion + déduplication) avant d'appeler toute la logique de validation et de sauvegarde existante.
Code Complet de la méthode createRequest : (Remplacez votre méthode existante par celle-ci)
code
Java
// Imports à ajouter en haut du fichier
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import com.bnpparibas.certis.exception.CertisRequestException;
import org.springframework.http.HttpStatus;

// ...

@Override
public RequestDto createRequest(RequestDto requestDto) {

    // --- BLOC DE FUSION ET DÉDUPLICATION (VERSION ENTITÉS) ---

    // 1. On récupère la liste d'entités SAN venant d'Angular.
    List<San> sansFromAngular = requestDto.getCertificate().getSans();
    if (sansFromAngular == null) {
        sansFromAngular = new ArrayList<>();
    }

    // 2. On récupère les SANs du CSR sous forme de DTOs propres.
    List<SanDto> sansDtoFromCsr = new ArrayList<>();
    final String csr = this.fileManagerService.extractCsr(requestDto, Boolean.TRUE);
    if (!StringUtils.isEmpty(csr)) {
        try {
            sansDtoFromCsr = this.certificateCsrDecoder.extractSansWithTypesFromCsr(csr);
        } catch (Exception e) {
            throw new CertisRequestException("error.request.csr.invalid_format", HttpStatus.BAD_REQUEST);
        }
    }

    // 3. On CONVERTIT les DTOs du CSR en nouvelles entités San.
    List<San> sansFromCsrEntities = new ArrayList<>();
    for (SanDto dto : sansDtoFromCsr) {
        San sanEntity = new San();
        sanEntity.setType(dto.getSanType());
        sanEntity.setSanValue(dto.getSanValue()); // ou setUrl si vous n'avez pas encore refactorisé
        sansFromCsrEntities.add(sanEntity);
    }

    // 4. On fusionne les DEUX listes d'ENTITÉS et on déduplique.
    Set<San> finalUniqueSans = new LinkedHashSet<>();
    finalUniqueSans.addAll(sansFromAngular);
    finalUniqueSans.addAll(sansFromCsrEntities);

    // 5. On met à jour le DTO avec la liste finale d'entités.
    requestDto.getCertificate().setSans(new ArrayList<>(finalUniqueSans));
    
    // --- FIN DU BLOC ---

    // Le reste de votre code original
    if (requestDto.getComment() != null && requestDto.getComment().length() > 3999) {
        requestDto.setComment(requestDto.getComment().substring(0, 3998));
    }

    Request request = dtoToEntity(requestDto);

    if (!CollectionUtils.isEmpty(requestDto.getCertificate().getSans())) {
        for (San san : requestDto.getCertificate().getSans()) {
            san.setCertificate(requestDto.getCertificate());
        }
    }

    if (!CollectionUtils.isEmpty(requestDto.getContacts())) {
        for (Contact cont : requestDto.getContacts()) {
            cont.setRequests(request);
        }
    }

    RequestDto requestDtoResult = entityToDto(requestDao.save(request));
    
    return requestDtoResult;
}
//////////////////// san //////////////////
@Entity
public class San implements Serializable {

    // ... vos champs existants (id, sanValue, type, certificate) ...
    
    // Vos getters et setters existants...
    public SanTypeEnum getType() {
        return type;
    }

    public String getSanValue() {
        return sanValue;
    }

    // ===================================================================
    // ===        BLOC DE CODE À AJOUTER DANS L'ENTITÉ San.java        ===
    // ===================================================================
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        San san = (San) o;

        // Deux SANs sont égaux s'ils ont le même type ET la même valeur (insensible à la casse)
        if (getType() != san.getType()) return false;
        if (getSanValue() == null) {
            return san.getSanValue() == null;
        }
        return getSanValue().equalsIgnoreCase(san.getSanValue());
    }

    @Override
    public int hashCode() {
        String lowerCaseSanValue = (getSanValue() == null) ? null : getSanValue().toLowerCase();
        return Objects.hash(getType(), lowerCaseSanValue);
    }
    // ===================================================================
    // ===                         FIN DU BLOC                         ===
    // ===================================================================
}
//////////////////////////createRequest//////////////
@Override
public RequestDto createRequest(RequestDto requestDto) {

    // --- DÉBUT DU BLOC AJOUTÉ : FUSION ET DÉDUPLICATION DES SANs ---
    
    List<San> sansFromAngular = new ArrayList<>();
    if (requestDto.getCertificate() != null && requestDto.getCertificate().getSans() != null) {
        sansFromAngular.addAll(requestDto.getCertificate().getSans());
    }

    List<SanDto> sansDtoFromCsr = new ArrayList<>();
    final String csr = this.fileManagerService.extractCsr(requestDto, Boolean.TRUE);
    if (!StringUtils.isEmpty(csr)) {
        try {
            sansDtoFromCsr = this.certificateCsrDecoder.extractSansWithTypesFromCsr(csr);
        } catch (Exception e) {
            throw new CertisRequestException("error.request.csr.invalid_format", HttpStatus.BAD_REQUEST);
        }
    }

    List<San> sansFromCsrEntities = new ArrayList<>();
    for (SanDto dto : sansDtoFromCsr) {
        San sanEntity = new San();
        sanEntity.setType(dto.getSanType());
        sanEntity.setSanValue(dto.getSanValue());
        sansFromCsrEntities.add(sanEntity);
    }

    Set<San> finalUniqueSans = new LinkedHashSet<>();
    finalUniqueSans.addAll(sansFromAngular);
    finalUniqueSans.addAll(sansFromCsrEntities);

    if (requestDto.getCertificate() != null) {
        requestDto.getCertificate().setSans(new ArrayList<>(finalUniqueSans));
    }
    
    // --- FIN DU BLOC AJOUTÉ ---


    // --- VOTRE CODE ORIGINAL (INCHANGÉ) ---
    
    if (requestDto.getComment() != null && requestDto.getComment().length() > 3999) {
        requestDto.setComment(requestDto.getComment().substring(0, 3998));
    }
    
    Request request = dtoToEntity(requestDto);
    
    if (!CollectionUtils.isEmpty(requestDto.getCertificate().getSans())) {
        for (San san : requestDto.getCertificate().getSans()) {
            san.setCertificate(requestDto.getCertificate());
        }
    }
    
    if (!CollectionUtils.isEmpty(requestDto.getContacts())) {
        for (Contact cont : requestDto.getContacts()) {
            cont.setRequests(request);
        }
    }
    
    RequestDto requestDtoResult = entityToDto(requestDao.save(request));
    
    return requestDtoResult;
}
///////////////////////////////////////
@Override
    @Transactional
    public RequestDto updateRequestInfo(UpdateRequestInfoDto updateRequest, Long requestId, String connectedUser, ActionRequestType action) {
        
        // ÉTAPE 1 : CHARGER L'ENTITÉ managée depuis la base de données
        Request requestToUpdate = requestDao.findOne(requestId);
        if (requestToUpdate == null) {
            throw new EntityNotFoundException("Request not found with id: " + requestId);
        }
        
        Certificate certificateToUpdate = requestToUpdate.getCertificate();
        if (certificateToUpdate == null) {
            throw new IllegalStateException("Certificate is null for request id: " + requestId);
        }

        checkAccessibilityForRequest(requestToUpdate, connectedUser, action);

        String traceModification = "Request information has been modified:";

        // ÉTAPE 2 : MODIFIER CETTE ENTITÉ en se basant sur la logique de vos captures d'écran
        
        if (!org.apache.commons.lang3.StringUtils.equalsIgnoreCase(updateRequest.getApplicationCode(), certificateToUpdate.getApplicationCode())) {
            traceModification += " ApplicationCode set to " + updateRequest.getApplicationCode() + ";";
            certificateService.setApplicationCodeAndSupportGroup(certificateToUpdate, updateRequest.getApplicationCode());
        }
    
        if (!Objects.equals(updateRequest.getApplicationName(), certificateToUpdate.getApplicationName())) {
            traceModification += " ApplicationName set to " + updateRequest.getApplicationName() + ";";
            certificateToUpdate.setApplicationName(updateRequest.getApplicationName());
        }

        if (!Objects.equals(updateRequest.getHostname(), certificateToUpdate.getHostname())) {
            traceModification += " Hostname set to " + updateRequest.getHostname() + ";";
            certificateToUpdate.setHostname(updateRequest.getHostname());
        }
    
        if (updateRequest.getEnvironment() != null && !Objects.equals(updateRequest.getEnvironment(), requestToUpdate.getEnvironment())) {
            traceModification += " Environment set to " + updateRequest.getEnvironment().name() + ";";
            requestToUpdate.setEnvironment(updateRequest.getEnvironment());
        }
    
        if (updateRequest.getUnknownCodeAP() != null && !Objects.equals(updateRequest.getUnknownCodeAP(), certificateToUpdate.getUnknownCodeAP())) {
            traceModification += " Unknown Code AP set to " + updateRequest.getUnknownCodeAP().toString() + ";";
            certificateToUpdate.setUnknownCodeAP(updateRequest.getUnknownCodeAP());
        }

        if (traceModification.contains("ApplicationCode") || traceModification.contains("ApplicationName")) {
            traceModification += " Application after Certis verification:" 
                + " " + Optional.ofNullable(certificateToUpdate.getApplicationCode()).orElse("") 
                + " " + Optional.ofNullable(certificateToUpdate.getApplicationName()).orElse("") + ";";
        }

        // ... (complétez avec la logique pour CertisEntity, GroupSupport, GnsCountry si nécessaire)

        // --- LOGIQUE POUR LE COMMENTAIRE DU CERTIFICAT ---
        if (!Objects.equals(updateRequest.getCertificateComment(), certificateToUpdate.getComment())) {
            certificateToUpdate.setComment(updateRequest.getCertificateComment());
            traceModification += " Le commentaire du certificat a été mis à jour;";
        }
        
        // --- ÉTAPE 3 : METTRE À JOUR L'HISTORIQUE ---
        RequestDto dtoForCommentService = entityToDto(requestToUpdate);
        commentService.processComment(dtoForCommentService, null, connectedUser, traceModification);
        requestToUpdate.setComment(dtoForCommentService.getComment());
        
        // --- ÉTAPE 4 : SAUVEGARDER L'ENTITÉ MODIFIÉE ---
        Request savedEntity = requestDao.save(requestToUpdate);

        return entityToDto(savedEntity);
    }
    
    // ... (gardez toutes vos autres méthodes existantes)
}

////////////////////////////////////
@Override
@Transactional
public RequestDto updateRequestInfo(UpdateRequestInfoDto updateRequest, Long requestId, String connectedUser, ActionRequestType action) {
    
    // --- Phase 1 : Comparaison et construction de la trace en utilisant les DTOs ---
    RequestDto previousRequest = this.findRequestByIdAndAccessibility(requestId, connectedUser, action);
    CertificateDto previousCertificate = previousRequest.getCertificate();
    String traceModification = "Request information has been modified:";

    if (!org.apache.commons.lang3.StringUtils.equalsIgnoreCase(updateRequest.getApplicationCode(), previousCertificate.getApplicationCode())) {
        traceModification += " ApplicationCode set to " + updateRequest.getApplicationCode() + ";";
    }

    if (!StringUtils.isEmpty(updateRequest.getApplicationName())) {
        if (StringUtils.isEmpty(previousCertificate.getApplicationName()) || !updateRequest.getApplicationName().equalsIgnoreCase(previousCertificate.getApplicationName())) {
            traceModification += " ApplicationName set to " + updateRequest.getApplicationName() + ";";
        }
    }

    if (!StringUtils.isEmpty(updateRequest.getHostname())) {
        if (previousRequest.getHostname() == null || !updateRequest.getHostname().equalsIgnoreCase(previousRequest.getHostname())) {
            traceModification += " Hostname set to " + updateRequest.getHostname() + ";";
        }
    }

    if (updateRequest.getEnvironment() != null) {
        if (previousRequest.getEnvironment() == null || !updateRequest.getEnvironment().equals(previousRequest.getEnvironment())) {
            traceModification += " Environment set to " + updateRequest.getEnvironment().name() + ";";
        }
    }
    
    if (updateRequest.getUnknownCodeAP() != null) {
        if (previousRequest.getCertificate().getUnknownCodeAP() == null || !updateRequest.getUnknownCodeAP().equals(previousRequest.getCertificate().getUnknownCodeAP())) {
            traceModification += " Unknown Code AP set to " + updateRequest.getUnknownCodeAP().toString() + ";";
        }
    }

    if (traceModification.contains("ApplicationCode") || traceModification.contains("ApplicationName")) {
        traceModification += " Application after Certis verification:" 
            + " " + Optional.ofNullable(previousCertificate.getApplicationCode()).orElse("") 
            + " " + Optional.ofNullable(previousCertificate.getApplicationName()).orElse("") + ";";
    }

    if (updateRequest.getCertisEntity() != null && !StringUtils.isEmpty(updateRequest.getCertisEntity().getName())) {
        // ... (Logique de comparaison pour CertisEntity)
    }

    if (updateRequest.getGroupSupport() != null && !StringUtils.isEmpty(updateRequest.getGroupSupport().getName())) {
        // ... (Logique de comparaison pour GroupSupport)
    }

    if (updateRequest.getCountry() != null && !StringUtils.isEmpty(updateRequest.getCountry().getIsoCode())) {
        // ... (Logique de comparaison pour Country)
    }

    if (!org.apache.commons.lang3.StringUtils.equalsIgnoreCase(updateRequest.getCertificateComment(), previousCertificate.getComment())) {
        traceModification += " Certificate comment has been updated;";
    }

    // --- Phase 2 : On met à jour l'historique sur le DTO ---
    this.commentService.processComment(previousRequest, null, connectedUser, traceModification);
    
    // ===================================================================================
    //     ▼▼▼   Phase 3 : Application des changements sur l'entité et sauvegarde   ▼▼▼
    // ===================================================================================

    // 1. On charge l'entité JPA managée depuis la base.
    Request requestToUpdate = requestDao.findOne(requestId);
    if (requestToUpdate == null) {
        throw new EntityNotFoundException("Request not found with id: " + requestId);
    }
    Certificate certificateToUpdate = requestToUpdate.getCertificate();

    // 2. On applique toutes les modifications depuis le DTO 'updateRequest' sur l'entité chargée.
    certificateService.setApplicationCodeAndSupportGroup(certificateToUpdate, updateRequest.getApplicationCode());
    certificateToUpdate.setApplicationName(updateRequest.getApplicationName());
    certificateToUpdate.setHostname(updateRequest.getHostname());
    requestToUpdate.setEnvironment(updateRequest.getEnvironment());
    certificateToUpdate.setUnknownCodeAP(updateRequest.getUnknownCodeAP());
    
    // (Complétez ici avec les setters pour country, entity, groupSupport si nécessaire)
    
    // On applique la modification du commentaire du certificat
    certificateToUpdate.setComment(updateRequest.getCertificateComment());
    
    // On applique l'historique mis à jour par le commentService (qui est sur le DTO previousRequest)
    requestToUpdate.setComment(previousRequest.getComment());
    
    // 3. On sauvegarde directement l'entité modifiée.
    Request savedEntity = requestDao.save(requestToUpdate);
    
    // On retourne le DTO de l'entité qui vient d'être sauvegardée.
    return entityToDto(savedEntity);
}
Request requestToUpdate = requestDao.findById(requestId)
            .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + requestId));
			
			//////////////////////////
			 @Override
    public void validateSansPerRequest(RequestDto requestDto) {
        if (this.skipValidationIfDataMissing(requestDto)) {
            return;
        }
        if (requestDto.getUsage().equalsIgnoreCase(INT_USAGE)) {
            this.verifySansLimitForInternalCertificates(requestDto);
        }
        if (requestDto.getUsage().equalsIgnoreCase(EXT_USAGE)) {
            this.verifySansLimitForExternalCertificates(requestDto);
        }

        this.verifySanFormats(requestDto); // <-- LIGNE À AJOUTER
    }
    
    // AJOUTER cette nouvelle méthode privée dans la classe
    private void verifySanFormats(RequestDto requestDto) {
        if (requestDto.getCertificate() == null || CollectionUtils.isEmpty(requestDto.getCertificate().getSans())) {
            return;
        }

        for (San san : requestDto.getCertificate().getSans()) {
            if (san.getType() == null || !StringUtils.hasText(san.getSanValue())) {
                throw new CertisRequestException("request.error.san.incomplete", HttpStatus.BAD_REQUEST);
            }

            boolean isValid = false;
            switch (san.getType()) {
                case DNSNAME:
                    isValid = SanValidationPatterns.DNSNAME.matcher(san.getSanValue()).matches();
                    break;
                case IPADDRESS:
                    isValid = SanValidationPatterns.IPADDRESS.matcher(san.getSanValue()).matches();
                    break;
                case RFC822NAME:
                    isValid = SanValidationPatterns.RFC822NAME.matcher(san.getSanValue()).matches();
                    break;
                case URI:
                    isValid = SanValidationPatterns.URI.matcher(san.getSanValue()).matches();
                    break;
                case OTHERNAME_GUID:
                    isValid = SanValidationPatterns.OTHERNAME_GUID.matcher(san.getSanValue()).matches();
                    break;
                case OTHERNAME_UPN:
                    isValid = SanValidationPatterns.OTHERNAME_UPN.matcher(san.getSanValue()).matches();
                    break;
                default:
                    isValid = true;
                    break;
            }

            if (!isValid) {
                Object[] args = { san.getSanValue(), san.getType().name() };
                throw new CertisRequestException("request.error.san.invalid.format", args, HttpStatus.BAD_REQUEST);
            }
        }
    }
}
@Service
public class SanServiceImpl implements SanService {
    // ...

    // MODIFIER cette méthode existante
    @Override
    public void validateSansPerRequest(RequestDto requestDto) {
        if (this.skipValidationIfDataMissing(requestDto)) {
            return;
        }
        
        // ... code existant ...
        if (requestDto.getUsage().equalsIgnoreCase(INT_USAGE)) { /* ... */ }
        if (requestDto.getUsage().equalsIgnoreCase(EXT_USAGE)) { /* ... */ }

        // ↓↓↓ LIGNE À AJOUTER ↓↓↓
        this.verifySanFormats(requestDto);
    }
    
    // ↓↓↓ NOUVELLE MÉTHODE PRIVÉE (bien plus simple maintenant) ↓↓↓
    private void verifySanFormats(RequestDto requestDto) {
        if (requestDto.getCertificate() == null || CollectionUtils.isEmpty(requestDto.getCertificate().getSans())) {
            return;
        }

        for (San san : requestDto.getCertificate().getSans()) {
            // La logique de validation est maintenant DANS l'enum SanType.
            // On vérifie que le type existe et que la valeur est valide pour ce type.
            if (san.getType() == null || !san.getType().isValid(san.getSanValue())) {
                Object[] args = { san.getSanValue(), san.getType() != null ? san.getType().name() : "INCONNU" };
                throw new CertisRequestException("request.error.san.invalid.format", args, HttpStatus.BAD_REQUEST);
            }
        }
    }
    
    // ...
}