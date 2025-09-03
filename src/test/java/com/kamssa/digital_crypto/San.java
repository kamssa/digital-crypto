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
/////////////////////////////
 <div *ngFor="let san of certificateRequest.certificate.sans" class="san-item-row" style="display: flex; align-items: center; margin-bottom: 5px;">
            
            <!-- Badge utilisant p-tag et la propriété 'type' -->
            <p-tag [styleClass]="sanStyleMapper[san.type]">
                {{san.type}}
            </p-tag>

            <!-- Espace -->
            &nbsp;

            <!-- Valeur utilisant la propriété 'value' -->
            <span class="san-value">
                {{ san.value }}
            </span>
            
        </div>
		
		
		///////////////////////////
		export const styleMapper = {
  // Clé (Type de SAN) : Valeur (Classe CSS)
  'DNSNAME':        'badge-info',
  'RFC822NAME':     'badge-success',
  'IPADDRESS':      'badge-warning',
  'URI':            'badge-danger',
  
  // Il est probable que vous ayez aussi les autres types ici :
  'OTHERNAME_GUID': 'badge-othername-guid', // J'invente un nom de classe, adaptez-le
  'OTHERNAME_UPN':  'badge-othername-upn',   // J'invente un nom de classe, adaptez-le
  
  // C'est une bonne pratique d'avoir un style par défaut
  'DEFAULT':        'badge-default'
};
////////////////////// payloadFinal///////////////
 const finalPayload = {
    // --- Propriétés au niveau RACINE ---
    // On prend la base de la requête existante (pour l'ID, etc.)
    ...this.certificateRequest, 
    
    // On prend les champs du formulaire qui vont à la racine
    requestType: formValue.requestDetails?.requestType,
    usage: formValue.requestDetails?.usage,
    environment: formValue.requestDetails?.environment,
    comment: formValue.requestDetails?.comment,
    osversion: formValue.certificateDetails?.osversion, // Exemple, à adapter
    numberOfCPU: formValue.certificateDetails?.numberOfCPU, // Exemple, à adapter
    operatingSystem: formValue.certificateDetails?.operatingSystem, // Exemple, à adapter
    licence: formValue.certificateDetails?.licence, // Exemple, à adapter
    
    // Le CSR est souvent géré séparément
    csr: this.certificateRequest?.certificate?.csrFile?.content,

    // --- L'objet 'certificate' ---
    certificate: {
      // On prend la base du certificat existant
      ...this.certificateRequest?.certificate,

      // On fusionne avec les champs du formulaire qui vont dans 'certificate'
      applicationCode: formValue.project?.applicationCode,
      applicationName: formValue.project?.applicationName,
      commonName: formValue.certificateDetails?.commonName,
      criticity: formValue.certificateDetails?.criticity,
      hostname: formValue.certificateDetails?.hostname,
      organisationName: formValue.certificateDetails?.organisationName,
      // ... autres champs spécifiques au certificat ...
      
      // On ajoute la liste des SANs ici, au bon endroit
      sans: formValue.sans 
    },

    // --- La liste des 'contacts' ---
    contacts: formValue.contacts
  };

  // --- Nettoyage des SANs (cette partie est toujours correcte) ---
  if (finalPayload.certificate?.sans && Array.isArray(finalPayload.certificate.sans)) {
    finalPayload.certificate.sans = finalPayload.certificate.sans.map(san => ({
      type: san.type,
      value: san.value
    }));
  }

  // --- Envoi de la requête (inchangé) ---
  let request$: Observable<any>;
  if (finalPayload.id) {
    request$ = this.requestService.editRequest(finalPayload);
  } else {
    request$ = this.requestService.addRequest(finalPayload);
  }
  
  // N'oubliez pas de vous abonner pour que la requête parte !
  request$.subscribe({
    next: (response) => console.log('Succès !', response),
    error: (err) => console.error('Erreur !', err)
  });
}
//////////////////////
public List<San> getSansList(String decodedCSR) throws IOException, FailedToParseCsrException { // On garde le nom pour la compatibilité

    if (decodedCSR == null || StringUtils.isEmpty(decodedCSR)) {
        return Collections.emptyList();
    }

    PKCS10CertificationRequest csr = csrPemToPKCS10(decodedCSR);
    assert csr != null;

    try {
        // La première partie de la méthode reste la même
        Attribute[] attrs = csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);
        if (attrs.length == 0) {
            return Collections.emptyList();
        }

        ASN1Encodable[] values = attrs[0].getAttributeValues();
        Extension extn = Extensions.getInstance(values[0]).getExtension(Extension.subjectAlternativeName);
        if (extn == null) {
            return Collections.emptyList();
        }
        
        // ===================================================================
        // ===                    DÉBUT DE LA MODIFICATION                 ===
        // ===================================================================

        // On crée la liste qui contiendra nos entités San
        List<San> sanList = new ArrayList<>();
        
        // On récupère la structure de données contenant tous les SANs
        GeneralNames generalNames = GeneralNames.getInstance(extn.getParsedValue()); // On utilise getParsedValue() qui est plus direct

        // On parcourt chaque SAN trouvé
        for (GeneralName name : generalNames.getNames()) {
            San sanEntity = new San(); // On crée une nouvelle entité pour chaque SAN

            // On utilise un 'switch' pour gérer tous les types possibles
            switch (name.getTagNo()) {
                case GeneralName.dNSName:
                    sanEntity.setType(SanTypeEnum.DNSNAME);
                    sanEntity.setSanValue(name.getName().toString());
                    sanList.add(sanEntity);
                    break;

                case GeneralName.iPAddress:
                    sanEntity.setType(SanTypeEnum.IPADDRESS);
                    sanEntity.setSanValue(name.getName().toString());
                    sanList.add(sanEntity);
                    break;
                
                case GeneralName.rfc822Name:
                    sanEntity.setType(SanTypeEnum.RFC822NAME);
                    sanEntity.setSanValue(name.getName().toString());
                    sanList.add(sanEntity);
                    break;
                    
                case GeneralName.uniformResourceIdentifier:
                    sanEntity.setType(SanTypeEnum.URI);
                    sanEntity.setSanValue(name.getName().toString());
                    sanList.add(sanEntity);
                    break;

                case GeneralName.otherName:
                    ASN1Sequence otherNameSequence = ASN1Sequence.getInstance(name.getName());
                    ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) otherNameSequence.getObjectAt(0);
                    
                    if (otherNameSequence.size() > 1) {
                        ASN1Encodable valueEncodable = ((DERTaggedObject) otherNameSequence.getObjectAt(1)).getObject();
                        String otherNameValue = valueEncodable.toString();

                        final String upnOid = "1.3.6.1.4.1.311.20.2.3";
                        final String guidOid = "1.3.6.1.4.1.311.25.1";

                        if (upnOid.equals(oid.getId())) {
                            sanEntity.setType(SanTypeEnum.OTHERNAME_UPN);
                            sanEntity.setSanValue(otherNameValue);
                            sanList.add(sanEntity);
                        } else if (guidOid.equals(oid.getId())) {
                            sanEntity.setType(SanTypeEnum.OTHERNAME_GUID);
                            sanEntity.setSanValue(otherNameValue);
                            sanList.add(sanEntity);
                        }
                    }
                    break;
            }
        }
        
        // On retourne la liste complète des entités San
        return sanList;

        // ===================================================================
        // ===                     FIN DE LA MODIFICATION                  ===
        // ===================================================================

    } catch (Exception e) {
        throw new FailedToParseCsrException(e.getMessage(), e.getCause());
    }
}
///////// sans dans renew /////////////////////

import { Component, OnInit, OnDestroy, Input } from '@angular/core';
import { FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { takeUntil } from 'rxjs/operators';
import { Subject } from 'rxjs';

@Component({
  selector: 'app-request-detail-section',
  templateUrl: './request-detail-section.component.html',
  styleUrls: ['./request-detail-section.component.css']
})
export class RequestDetailSectionComponent implements OnInit, OnDestroy {

  // --- Propriétés (beaucoup viennent de votre code existant) ---
  @Input() requestDetailsSectionForm: FormGroup;
  @Input() certificateRequest: any; // Pour les données existantes
  @Input() isRenew: boolean = false;
  
  private onDestroy$ = new Subject<void>();
  
  sanTypes = [ /* ... votre liste de types ... */ ];
  SANS_REGEX_PATTERNS = { /* ... votre objet avec les regex ... */ };

  constructor(private fb: FormBuilder, /* ... autres services */) {}

  ngOnInit(): void {
    // 1. VOTRE LOGIQUE EXISTANTE : Initialisation du FormArray
    if (!this.requestDetailsSectionForm.get('sans')) {
      this.requestDetailsSectionForm.addControl('sans', this.fb.array([]));
    }

    // 2. NOUVELLE LOGIQUE INTÉGRÉE : Chargement des données
    // On vérifie si on est en renouvellement et si on a des données à charger
    if (this.isRenew && this.certificateRequest?.certificate?.sans?.length > 0) {
      this.loadExistingSans(this.certificateRequest.certificate.sans);
    } else {
      // Sinon (création), on exécute votre logique originale : ajouter un champ vide.
      this.addSan(); 
    }
    
    // ... reste de votre logique ngOnInit ...
  }

  // Getter (votre code original)
  get sans(): FormArray {
    return this.requestDetailsSectionForm.get('sans') as FormArray;
  }

  /**
   * VOTRE MÉTHODE ORIGINALE (légèrement modifiée pour être réutilisable)
   * Crée un FormGroup pour une seule ligne de SAN avec validation dynamique.
   */
  createSanGroup(initialValue: string = '', initialType: string = 'DNSNAME'): FormGroup {
    const sanGroup = this.fb.group({
      type: [initialType, Validators.required],
      value: [initialValue, [Validators.required, Validators.pattern(this.SANS_REGEX_PATTERNS[initialType])]]
    });

    // Votre logique de validation dynamique reste ici, elle est parfaite.
    sanGroup.get('type').valueChanges
      .pipe(takeUntil(this.onDestroy$))
      .subscribe(type => {
        const valueControl = sanGroup.get('value');
        const regex = this.SANS_REGEX_PATTERNS[type];
        
        if (regex) {
          valueControl.setValidators([Validators.required, Validators.pattern(regex)]);
        } else {
          valueControl.setValidators(Validators.required);
        }
        valueControl.updateValueAndValidity();
      });
      
    return sanGroup;
  }

  // Méthode pour ajouter (votre code original)
  addSan(): void {
    this.sans.push(this.createSanGroup());
  }
  
  // Méthode pour supprimer (votre code original)
  removeSan(index: number): void {
    if (this.sans.length > 1) {
      this.sans.removeAt(index);
    }
  }

  /**
   * NOUVELLE MÉTHODE INTÉGRÉE
   * Peuple le formulaire avec les SANs existants lors d'un renouvellement.
   */
  loadExistingSans(existingSans: any[]): void {
    this.sans.clear();
    existingSans.forEach(san => {
      const sanValue = san.sanValue || san.url || '';
      const sanType = san.type || 'DNSNAME';
      
      // On utilise votre méthode `createSanGroup` améliorée pour créer chaque ligne
      this.sans.push(this.createSanGroup(sanValue, sanType));
    });
  }

  ngOnDestroy(): void {
    this.onDestroy$.next();
    this.onDestroy$.complete();
  }
}
/////////////////////////////
 ngOnChanges(changes: SimpleChanges): void {
    // On vérifie si l'input 'certificateRequest' a changé ET s'il a maintenant une valeur.
    if (changes.certificateRequest && this.certificateRequest) {
        
        console.log('Données reçues du backend (via ngOnChanges) :', this.certificateRequest);

        // Maintenant que nous sommes sûrs d'avoir les données, on lance le chargement.
        if (this.certificateRequest.certificate?.sans?.length > 0) {
            this.loadExistingSans(this.certificateRequest.certificate.sans);
        } else {
            // S'il n'y a pas de SANs existants, on s'assure qu'il y a au moins un champ vide.
            if (this.sans.length === 0) {
                this.addSan();
            }
        }
    }
  }

  // ... (get sans(), createSanGroup(), addSan(), removeSan(), loadExistingSans() restent inchangés) ...
}
////////////////////////
@Override
public RequestDto createRequest(RequestDto requestDto) {

    // ===================================================================
    // ===       BLOC DE FUSION ET CORRECTION DES SANs (FINAL)         ===
    // ===================================================================
    // On s'assure que l'objet certificat existe avant de manipuler les SANs
    if (requestDto.getCertificate() != null) {

        // 1. On récupère les SANs saisis dans Angular de manière sécurisée.
        List<San> sansFromInput = new ArrayList<>();
        if (requestDto.getCertificate().getSans() != null) {
            sansFromInput.addAll(requestDto.getCertificate().getSans());
        }

        // 2. On récupère les SANs du CSR sous forme de DTOs.
        List<SanDto> sansDtoFromCsr = new ArrayList<>();
        final String csr = this.fileManagerService.extractCsr(requestDto, Boolean.TRUE);
        if (!StringUtils.isEmpty(csr)) {
            try {
                // Note : On utilise bien une méthode qui retourne List<SanDto>
                sansDtoFromCsr = this.csrDecoder.extractSansWithTypesFromCsr(csr);
            } catch (Exception e) {
                LOGGER.error("Message de CSR create:" + e.getMessage());
                throw new CertisRequestException("error.request.csr.invalid_format", HttpStatus.BAD_REQUEST);
            }
        }

        // 3. On convertit les DTOs du CSR en nouvelles entités San.
        List<San> sansFromCsrEntities = new ArrayList<>();
        // CORRECTION D'UNE ERREUR : La boucle doit itérer sur des SanDto
        for (SanDto dto : sansDtoFromCsr) {
            San sanEntity = new San();
            sanEntity.setType(dto.getSanType());
            sanEntity.setSanValue(dto.getSanValue());
            sansFromCsrEntities.add(sanEntity);
        }

        // 4. On fusionne les deux listes d'entités et on supprime les doublons.
        Set<San> finalUniqueSans = new LinkedHashSet<>();
        finalUniqueSans.addAll(sansFromInput);
        finalUniqueSans.addAll(sansFromCsrEntities);

        // 5. LA CORRECTION CRUCIALE : On lie chaque SAN à son certificat parent.
        // C'est cette étape qui empêche l'erreur de transaction.
        List<San> finalSanList = new ArrayList<>(finalUniqueSans);
        for (San san : finalSanList) {
            san.setCertificate(requestDto.getCertificate());
        }
        
        // 6. On met à jour le DTO avec la liste finale, propre et correctement liée.
        requestDto.getCertificate().setSans(finalSanList);
    }
    // ===================================================================
    // ===                      FIN DU BLOC DE CORRECTION              ===
    // ===================================================================


    // --- VOTRE CODE ORIGINAL (légèrement nettoyé) ---
    
    if (requestDto.getComment() != null && requestDto.getComment().length() > 3999) {
        requestDto.setComment(requestDto.getComment().substring(0, 3998));
    }
    
    Request request = dtoToEntity(requestDto);
    
    // Cette boucle est maintenant REDONDANTE car nous l'avons déjà fait plus haut de manière plus sûre.
    // Vous pouvez la supprimer pour garder le code propre.
    /*
    if (!CollectionUtils.isEmpty(requestDto.getCertificate().getSans())) {
        for (San san : requestDto.getCertificate().getSans()) {
            san.setCertificate(requestDto.getCertificate());
        }
    }
    */
    
    if (!CollectionUtils.isEmpty(requestDto.getContacts())) {
        for (Contact cont : requestDto.getContacts()) {
            cont.setRequests(request);
        }
    }
    
    RequestDto requestDtoResult = entityToDto(requestDao.save(request));
    
    return requestDtoResult;
}
/////////////////////////
 if (requestDto.getCertificate() != null) {

            List<San> sansFromInput = new ArrayList<>();
            if (requestDto.getCertificate().getSans() != null) {
                sansFromInput.addAll(requestDto.getCertificate().getSans());
            }

            List<SanDto> sansDtoFromCsr = new ArrayList<>();
            final String csr = this.fileManagerService.extractCsr(requestDto, Boolean.TRUE);
            if (!StringUtils.isEmpty(csr)) {
                try {
                    sansDtoFromCsr = this.csrDecoder.extractSansWithTypesFromCsr(csr);
                } catch (Exception e) {
                    LOGGER.error("Message de CSR create:" + e.getMessage());
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
            finalUniqueSans.addAll(sansFromInput);
            finalUniqueSans.addAll(sansFromCsrEntities);

            List<San> finalSanList = new ArrayList<>(finalUniqueSans);
            for (San san : finalSanList) {
                san.setCertificate(requestDto.getCertificate());
            }
            
            requestDto.getCertificate().setSans(finalSanList);
        }
        
        if (requestDto.getComment() != null && requestDto.getComment().length() > 3999) {
            requestDto.setComment(requestDto.getComment().substring(0, 3998));
        }
        
        Request request = dtoToEntity(requestDto);
        
        if (!CollectionUtils.isEmpty(requestDto.getContacts())) {
            for (Contact cont : requestDto.getContacts()) {
                cont.setRequests(request);
            }
        }
        
        RequestDto requestDtoResult = entityToDto(requestDao.save(request));
        
        return requestDtoResult;

    } catch (Exception e) {
        // CE BLOC VA SE DÉCLENCHER ET NOUS MONTRER LA VRAIE ERREUR
        // AVANT QUE SPRING NE LA MASQUE
        
        LOGGER.error("===============================================================");
        LOGGER.error("=== LA VRAIE CAUSE DE L'ERREUR DE TRANSACTION EST ICI ===");
        LOGGER.error("===============================================================");
        LOGGER.error("Exception de type : {}", e.getClass().getName());
        LOGGER.error("Message : {}", e.getMessage());
        LOGGER.error("Stack Trace : ", e); // Le 'e' à la fin est crucial, il imprime la stack trace complète
        LOGGER.error("===============================================================");
        
        // On relance une exception claire pour que l'API ne reste pas bloquée
        throw new RuntimeException("Erreur interceptée, vérifiez les logs pour la cause réelle.", e);
    }
    // ===================================================================
    // ===                       FIN DU BLOC DE DÉBOGAGE               ===
    // ===================================================================
}
///////////////////////////////////////

    // --- BLOC DE FUSION ET PRÉPARATION DES DONNÉES ---
    if (requestDto.getCertificate() != null) {

        List<San> sansFromInput = new ArrayList<>();
        if (requestDto.getCertificate().getSans() != null) {
            sansFromInput.addAll(requestDto.getCertificate().getSans());
        }

        List<SanDto> sansDtoFromCsr = new ArrayList<>();
        final String csr = this.fileManagerService.extractCsr(requestDto, Boolean.TRUE);
        if (!StringUtils.isEmpty(csr)) {
            try {
                sansDtoFromCsr = this.csrDecoder.extractSansWithTypesFromCsr(csr);
            } catch (Exception e) {
                LOGGER.error("Message de CSR create:" + e.getMessage());
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
        finalUniqueSans.addAll(sansFromInput);
        finalUniqueSans.addAll(sansFromCsrEntities);
        
        // ÉTAPE ESSENTIELLE : On met à jour le DTO pour que 'dtoToEntity' ait toutes les données.
        requestDto.getCertificate().setSans(new ArrayList<>(finalUniqueSans));
    }
    // --- FIN DU BLOC ---

    
    if (requestDto.getComment() != null && requestDto.getComment().length() > 3999) {
        requestDto.setComment(requestDto.getComment().substring(0, 3998));
    }
    
    // 1. On transforme le DTO complet en entités JPA.
    Request request = dtoToEntity(requestDto);
    
    // ===================================================================
    // ===                 LA CORRECTION FINALE EST ICI                ===
    // ===================================================================
    // 2. On s'assure que chaque entité San dans l'entité Request finale 
    //    est bien liée à son Certificate parent. C'EST CETTE BOUCLE QUI RÈGLE TOUT.
    if (request.getCertificate() != null && !CollectionUtils.isEmpty(request.getCertificate().getSans())) {
        for (San san : request.getCertificate().getSans()) {
            san.setCertificate(request.getCertificate());
        }
    }
    // ===================================================================

    if (!CollectionUtils.isEmpty(request.getContacts())) {
        for (Contact cont : request.getContacts()) {
            cont.setRequests(request);
        }
    }
    
    // 3. On sauvegarde l'entité Request, maintenant qu'elle est parfaitement cohérente.
    RequestDto requestDtoResult = entityToDto(requestDao.save(request));
    
    return requestDtoResult;
	
	
	////////////////////////////
	private List<San> mergeSans(RequestDto requestDto) {
    List<San> sansFromInput = Optional.ofNullable(requestDto.getCertificate().getSans())
            .orElseGet(Collections::emptyList);

    List<SanDto> sansDtoFromCsr = Collections.emptyList();
    final String csr = this.fileManagerService.extractCsr(requestDto, Boolean.TRUE);

    if (StringUtils.hasText(csr)) {
        try {
            sansDtoFromCsr = this.csrDecoder.extractSansWithTypesFromCsr(csr);
        } catch (Exception e) {
            LOGGER.error("Erreur lors du décodage du CSR", e);
            throw new CertisRequestException("error.request.csr.invalid_format", HttpStatus.BAD_REQUEST);
        }
    }

    List<San> sansFromCsrEntities = sansDtoFromCsr.stream()
            .map(dto -> {
                San san = new San();
                san.setType(dto.getSanType());
                san.setSanValue(dto.getSanValue());
                return san;
            })
            .collect(Collectors.toList());

    Set<San> finalUniqueSans = new LinkedHashSet<>();
    finalUniqueSans.addAll(sansFromInput);
    finalUniqueSans.addAll(sansFromCsrEntities);

    return new ArrayList<>(finalUniqueSans);
}
 List<San> finalSanList = new ArrayList<>(); // On prépare notre liste finale

    if (requestDto.getCertificate() != null) {
        List<San> sansFromInput = new ArrayList<>();
        if (requestDto.getCertificate().getSans() != null) {
            sansFromInput.addAll(requestDto.getCertificate().getSans());
        }

        List<SanDto> sansDtoFromCsr = new ArrayList<>();
        final String csr = this.fileManagerService.extractCsr(requestDto, Boolean.TRUE);
        if (!StringUtils.isEmpty(csr)) {
            try {
                sansDtoFromCsr = this.csrDecoder.extractSansWithTypesFromCsr(csr);
            } catch (Exception e) {
                LOGGER.error("Message de CSR create:" + e.getMessage());
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
        finalUniqueSans.addAll(sansFromInput);
        finalUniqueSans.addAll(sansFromCsrEntities);
        
        finalSanList.addAll(finalUniqueSans);
    }
    
    // ON NE MODIFIE PAS requestDto.getCertificate().setSans(...) !
    
    if (requestDto.getComment() != null && requestDto.getComment().length() > 3999) {
        requestDto.setComment(requestDto.getComment().substring(0, 3998));
    }
    
    // 1. On appelle dtoToEntity avec le DTO d'origine. Il va créer les entités
    //    en se basant UNIQUEMENT sur les SANs d'Angular. C'est OK.
    Request request = dtoToEntity(requestDto);
    
    // ===================================================================
    // ===                 LA CORRECTION FINALE EST ICI                ===
    // ===================================================================
    // 2. MAINTENANT que les entités de base sont créées, on REMPLACE
    //    la liste de SANs de l'entité Certificate par notre liste finale et propre.
    if (request.getCertificate() != null) {
        // On vide l'ancienne liste et on ajoute tous les nouveaux éléments.
        request.getCertificate().getSans().clear();
        request.getCertificate().getSans().addAll(finalSanList);

        // 3. ET ON LIE CHAQUE SAN au certificat parent. C'est l'étape la plus importante.
        for (San san : request.getCertificate().getSans()) {
            san.setCertificate(request.getCertificate());
        }
    }
    // ===================================================================

    if (!CollectionUtils.isEmpty(request.getContacts())) {
        for (Contact cont : request.getContacts()) {
            cont.setRequests(request);
        }
    }
    
    RequestDto requestDtoResult = entityToDto(requestDao.save(request));
    
    return requestDtoResult;
	
	////////////////////////////////////////// 
	
@Service
public class RequestServiceImpl implements RequestService {

    // ... Vos injections @Autowired (requestDao, fileManagerService, csrDecoder, etc.) ...
    @Autowired
    private FileManagerService fileManagerService; 

    @Autowired
    private CertificateCsrDecoder certificateCsrDecoder;


    @Override
    public RequestDto createRequest(RequestDto requestDto) {

        // 1. On appelle notre nouvelle méthode privée pour préparer la liste finale de SANs.
        List<San> finalSanList = mergeSansFromDtoAndCsr(requestDto);

        // 2. LA CORRECTION CRUCIALE : On met à jour le DTO pour que 'dtoToEntity' ait toutes les données.
        if (requestDto.getCertificate() != null) {
            requestDto.getCertificate().setSans(finalSanList);
        }

        // --- Le reste de votre code original ---
        if (requestDto.getComment() != null && requestDto.getComment().length() > 3999) {
            requestDto.setComment(requestDto.getComment().substring(0, 3998));
        }
        
        // 3. On transforme le DTO complet en entités JPA.
        Request request = dtoToEntity(requestDto);
        
        // 4. On s'assure que chaque entité San est bien liée à son Certificate parent.
        if (request.getCertificate() != null && !CollectionUtils.isEmpty(request.getCertificate().getSans())) {
            for (San san : request.getCertificate().getSans()) {
                san.setCertificate(request.getCertificate());
            }
        }

        if (!CollectionUtils.isEmpty(request.getContacts())) {
            for (Contact cont : request.getContacts()) {
                cont.setRequests(request);
            }
        }
        
        // 5. On sauvegarde l'entité Request, maintenant qu'elle est parfaitement cohérente.
        RequestDto requestDtoResult = entityToDto(requestDao.save(request));
        
        return requestDtoResult;
    }


    // ===================================================================
    // ===                 NOUVELLE MÉTHODE PRIVÉE                     ===
    // ===================================================================
    /**
     * Fusionne les SANs provenant du DTO (saisie manuelle) et ceux extraits du CSR.
     * Le résultat est une liste d'entités San uniques.
     * @param requestDto Le DTO de la requête entrante.
     * @return Une liste finale et propre d'entités San.
     */
    private List<San> mergeSansFromDtoAndCsr(RequestDto requestDto) {
        if (requestDto.getCertificate() == null) {
            return new ArrayList<>();
        }

        // Étape A : Récupérer les SANs de la saisie (Angular)
        List<San> sansFromInput = new ArrayList<>();
        if (requestDto.getCertificate().getSans() != null) {
            sansFromInput.addAll(requestDto.getCertificate().getSans());
        }

        // Étape B : Extraire et convertir les SANs du CSR
        List<San> sansFromCsrEntities = new ArrayList<>();
        final String csr = this.fileManagerService.extractCsr(requestDto, Boolean.TRUE);
        if (!StringUtils.isEmpty(csr)) {
            try {
                // On utilise le décodeur qui retourne des DTOs
                List<SanDto> sansDtoFromCsr = this.csrDecoder.extractSansWithTypesFromCsr(csr);
                
                // On convertit les DTOs en entités
                for (SanDto dto : sansDtoFromCsr) {
                    San sanEntity = new San();
                    sanEntity.setType(dto.getSanType());
                    sanEntity.setSanValue(dto.getSanValue());
                    sansFromCsrEntities.add(sanEntity);
                }
            } catch (Exception e) {
                LOGGER.error("Erreur lors de l'extraction des SANs du CSR: " + e.getMessage());
                throw new CertisRequestException("error.request.csr.invalid_format", HttpStatus.BAD_REQUEST);
            }
        }

        // Étape C : Fusionner les deux listes et supprimer les doublons
        Set<San> finalUniqueSans = new LinkedHashSet<>();
        finalUniqueSans.addAll(sansFromInput);
        finalUniqueSans.addAll(sansFromCsrEntities);
        
        return new ArrayList<>(finalUniqueSans);
    }
    
    // ... Le reste de vos autres méthodes (updateRequest, dtoToEntity, etc.) ...
}
////////////////////////////
export const SANS_REGEX_PATTERNS = {
  // Regex pour les noms de domaine. En JS, \\ devient \.
  [SanType.DNSNAME]: /^[a-zA-Z0-9\.\-\*_]+$/,

  // Regex pour les adresses email. En JS, \. devient .
  [SanType.RFC822NAME]: /^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$/,

  // Regex pour les adresses IP. En JS, \\d devient \d.
  [SanType.IPADDRESS]: /^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$/,

  // Regex pour les GUID. Elle est identique.
  [SanType.OTHERNAME_GUID]: /^[a-fA-F0-9]{32}$/,

  // Regex pour les UPN (souvent similaire à un email). La regex Java est complexe,
  // celle-ci est une version JS équivalente.
  [SanType.OTHERNAME_UPN]: /^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$/,

  // Regex pour les URIs. En JS, pas besoin d'échapper certains caractères.
  [SanType.URI]: /^(https?|ldaps?|ftp|file|tag|urn|data|tel):[a-zA-Z0-9+&@#/%?=~_!:,.;]*[a-zA-Z0-9+&@#/%=~_]/i
};
import { Component } from '@angular/core';

interface City {
  name: string;
  code: string;
}

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html'
})
export class AppComponent {
  cities: City[] = [];
  selectedCity!: City;

  ngOnInit() {
	  
    this.cities = [
      { name: 'New York', code: 'NY' },
      { name: 'Rome', code: 'RM' },
      { name: 'London', code: 'LDN' },
      { name: 'Istanbul', code: 'IST' },
      { name: 'Paris', code: 'PRS' }
    ];
  }
}
/////////////////////////
.filter(k => isNaN(Number(k)));
/////////////////////////// correction voir san dans input ////////////////////////////////
Dans request-detail-section.component.ts, modifiez la méthode statique buildRequestDetailForm pour qu'elle crée la bonne structure de FormArray.
code
TypeScript
// Fichier : request-detail-section.component.ts

import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms'; // Assurez-vous d'avoir ces imports

// ...

static buildRequestDetailForm(certificate: CertificateRequest, comment: string): FormGroup {
    // Si certificate.SANS n'existe pas ou est vide, on initialise avec un tableau vide
    const initialSansData = certificate?.SANS || [];

    return new FormGroup({
      // ... autres form controls
      certificateName: new FormControl(certificate.certificateName, Validators.minLength(3)),

      // ======================= CORRECTION PRINCIPALE =======================
      SANS: new FormArray(
        initialSansData.map(san => new FormGroup({
          // Je suppose que votre objet 'san' a les propriétés 'type' et 'value'
          type: new FormControl(san.type, Validators.required),
          value: new FormControl(san.value, Validators.required) 
        }))
      ),
      // =====================================================================
      comment: new FormControl(certificate.comment, Validators.minLength(3))
    });
}
Étape 2 : Mettre à jour le composant enfant (san.component.ts)
C'est l'étape la plus importante. Vous devez adapter toute la logique de ce composant pour qu'il fonctionne avec les contrôles type et value.
code
TypeScript
// Fichier : san.component.ts

// ...
export class SanComponent extends SubscribeAware implements OnInit, OnDestroy {
  @Input() index: number;
  @Input() sanForm: FormGroup; // Reçoit un FormGroup qui contient 'type' et 'value'
  // ... autres @Input

  // ===== SUPPRIMEZ L'ANCIEN GETTER =====
  // get san() { return this.sanForm.get('san'); } // À SUPPRIMER

  // ===== AJOUTEZ DES GETTERS UTILES =====
  get typeControl(): FormControl {
    return this.sanForm.get('type') as FormControl;
  }

  get valueControl(): FormControl {
    return this.sanForm.get('value') as FormControl;
  }


  // ===== SUPPRIMEZ L'ANCIENNE MÉTHODE STATIQUE =====
  // static buildSanForm(san: string = '') { ... } // À SUPPRIMER (elle est maintenant gérée dans createSanGroup du parent)

  // ... constructor ...

  ngOnInit() {
    this.formConstraintsService.getConstraint().subscribe(constraint => {
      this.constraint = constraint;
    });

    // ===== MODIFIEZ TOUTE LA LOGIQUE POUR UTILISER 'valueControl' =====
    this.valueControl.statusChanges
      .pipe(
        startWith(this.valueControl.status),
        debounceTime(500),
        filter(status => status === 'VALID'),
        map(() => this.valueControl.value),
        filter(sanValue => sanValue.length > 3),
        distinctUntilChanged(),
        tap(sanValue => {
          this.checkSan(sanValue); // On utilise la valeur du contrôle 'value'
        }),
        takeUntil(this.destroy$)
      )
      .subscribe();

    // Mettez à jour les validateurs et autres logiques de la même manière
    // Exemple pour la suite de la logique :
    const certificateNameRefwebValidator = certificateNameRefwebValidatorFactory(this.sanForm.parent, false, this.api, this.sanForm);
    this.valueControl.valueChanges
      .pipe(
        takeUntil(this.destroy$),
        switchMap(() => {
          if (this.valueControl.valid) { // On vérifie la validité du contrôle 'value'
            return certificateNameRefwebValidator(this.valueControl);
          }
          return of(null);
        })
      )
      .subscribe({
        next: errors => this.refwebWarnings = errors,
        error: () => this.refwebWarnings = { inactif: true }
      });
    
    // etc. pour le reste des validateurs...
  }
  
  checkSan(sanValue: string) { // La signature est bonne
    this.api.checkSan(sanValue).subscribe(/* ... */);
  }

  // ...
}
Étape 3 : Assurer que le template du composant enfant est correct (san.component.html)
Le code HTML que vous nous avez montré au tout début doit se trouver dans le fichier san.component.html. Il doit être lié au sanForm que le composant reçoit en @Input.
code
Html
<!-- Fichier : san.component.html -->
<!-- Ce formulaire est lié au [formGroup]="sanForm" reçu en Input -->
<div [formGroup]="sanForm" class="row row-style">

  <!-- Champ pour la valeur -->
  <div class="san-value-column">
    <input type="text" pInputText formControlName="value" style="width: 100%;">
  </div>

  &nbsp;&nbsp;

  <!-- Champ pour le type -->
  <div class="san-type-column">
    <p-dropdown [options]="sanType" formControlName="type" optionLabel="name">
      <!-- Les templates pour le dropdown -->
    </p-dropdown>
  </div>
  
  &nbsp;&nbsp;

  <!-- Bouton d'action -->
  <div class="san-action-col">
      <button pButton type="button" icon="pi pi-trash" (click)="removeThisSan()">...</button>
  </div>

  <!-- Affichage des erreurs -->
  <div class="ui-g-12">
    <div *ngIf="valueControl.invalid && (valueControl.dirty || valueControl.touched)">
      <small class="p-error" *ngIf="valueControl.errors?.pattern">
        Message d'erreur...
      </small>
    </div>
  </div>
</div>
Résumé de la correction
request-detail-section.component.ts : Modifiez buildRequestDetailForm pour qu'il crée un FormArray où chaque élément est un FormGroup({ type: ..., value: ... }) à partir des données initiales.
san.component.ts : Refactorisez entièrement ce composant. Supprimez toute référence à un contrôle san et utilisez à la place des getters pour les contrôles type et value. Adaptez toute la logique de validation dans ngOnInit pour qu'elle fonctionne avec this.valueControl.
san.component.html : Assurez-vous que ce template contient les champs input et dropdown avec les formControlName="value" et formControlName="type" respectivement.
Une fois ces trois points corrigés, votre application sera cohérente : la création des données, la logique du composant enfant et l'affichage HTML fonctionneront tous avec la même structure de données {type, value}.
//////////////////////// regex /////////////////////
// Ligne 696-717
createSanGroup(): FormGroup {
  const sanGroup = this.fb.group({
    type: [SanType.DNSNAME, Validators.required],
    value: ['', [Validators.required, Validators.pattern(SANS_REGEX_PATTERNS[SanType.DNSNAME])]],
  });

  // Cette partie est cruciale : elle change le validateur quand le type change
  sanGroup.get('type').valueChanges.subscribe((type: SanType) => {
    const valueControl = sanGroup.get('value');
    const regex = SANS_REGEX_PATTERNS[type];
    if (regex) {
      // Si une regex existe, on ajoute le validateur 'pattern'
      valueControl.setValidators([Validators.required, Validators.pattern(regex)]);
    } else {
      valueControl.setValidators(Validators.required);
    }
    valueControl.updateValueAndValidity();
  });
  return sanGroup;
}
Cette logique ajoute un validateur Validators.pattern au champ value. Si la valeur entrée ne correspond pas à l'expression régulière (regex), Angular ajoutera un objet d'erreur au contrôle, qui ressemblera à { pattern: { requiredPattern: '...', actualValue: '...' } }.
Dans le HTML (request-detail-section.component.html ou san.component.html) :
Vous affichez le message d'erreur avec cette condition :
code
Html
<small class="p-error" *ngIf="sanGroup.get('value')?.errors?.pattern">
  {{ 'requestDetailSection.errors.sanFormat.' + sanGroup.get('type').value | translate }}
</small>
Le message ne s'affiche QUE SI sanGroup.get('value').errors existe et contient une clé nommée pattern.
Les Causes Possibles du Problème
Il y a deux raisons principales pour lesquelles le message ne s'affiche pas :
Cause n°1 : Le validateur pattern ne se déclenche pas (le *ngIf est faux)
C'est la cause la plus probable. L'objet errors sur votre FormControl ne contient pas la clé pattern.
Comment vérifier :
La valeur entrée est peut-être valide : Assurez-vous que vous testez avec une valeur qui est certainement invalide par rapport à votre regex. Par exemple, si vous attendez une adresse IP, tapez "hello world".
La regex est incorrecte ou manquante : La logique dépend de l'objet SANS_REGEX_PATTERNS. Il est possible que pour le type que vous sélectionnez dans le dropdown, la regex correspondante soit manquante ou incorrecte.
Action de débogage : Dans votre méthode createSanGroup, ajoutez des console.log pour voir exactement quel validateur est appliqué.
code
TypeScript
sanGroup.get('type').valueChanges.subscribe((type: SanType) => {
  const valueControl = sanGroup.get('value');
  const regex = SANS_REGEX_PATTERNS[type];

  // AJOUTEZ CE CONSOLE.LOG
  console.log(`Type changé en : ${type}. Regex appliquée :`, regex);

  if (regex) {
    valueControl.setValidators([Validators.required, Validators.pattern(regex)]);
  } else {
    console.warn(`Aucune regex trouvée pour le type ${type}`); // Avertissement utile
    valueControl.setValidators(Validators.required);
  }
  valueControl.updateValueAndValidity();
});
Ouvrez la console de votre navigateur. Changez le type dans le dropdown et vérifiez si une regex correcte est affichée. Si vous voyez undefined ou un avertissement, c'est que votre objet SANS_REGEX_PATTERNS n'a pas de clé pour ce type.
Vérifier l'état du formulaire en temps réel : La meilleure façon de savoir est d'afficher l'objet d'erreurs. Dans votre HTML, juste pour le débogage, ajoutez ceci sous votre champ input :
code
Html
<pre>{{ sanGroup.get('value').errors | json }}</pre>
Tapez une valeur invalide dans le champ. Si rien ne s'affiche dans la balise <pre>, c'est que le validateur ne se déclenche pas. Si vous voyez { "required": true } mais pas pattern, c'est que seul le validateur required est actif. Si vous voyez bien { "pattern": { ... } } et que le message ne s'affiche toujours pas, le problème vient de la cause n°2.
Cause n°2 : Le validateur se déclenche, mais la traduction échoue
Dans ce cas, le *ngIf est bien true, mais le texte à l'intérieur ne s'affiche pas car la clé de traduction est introuvable.
Comment vérifier :
La clé de traduction est construite dynamiquement : 'requestDetailSection.errors.sanFormat.' + sanGroup.get('type').value.
La valeur du type est inattendue : Le sanGroup.get('type').value pourrait ne pas être la chaîne de caractères que vous attendez (par exemple DNSNAME). Il pourrait s'agir d'un objet ou d'une valeur avec une casse différente.
Action de débogage : Ajoutez ce console.log dans le même subscribe que précédemment :
console.log('Valeur du type utilisée pour la traduction :', sanGroup.get('type').value);
La clé n'existe pas dans vos fichiers de traduction : Vérifiez vos fichiers JSON de traduction (par ex. fr.json). Assurez-vous d'avoir des entrées qui correspondent exactement aux clés générées. Par exemple :
code
JSON
// Dans fr.json
{
  "requestDetailSection": {
    "errors": {
      "sanFormat": {
        "DNSNAME": "Le format du nom de domaine est invalide.",
        "IPADDRESS": "Le format de l'adresse IP est invalide.",
        "EMAIL": "Le format de l'adresse email est invalide."
        // ... etc. pour tous les types possibles
      }
    }
  }
}
/////////////////// certificat.ts ameliorer ///////////////////////////////
Convention de nommage : En TypeScript, les noms d'interfaces et de classes commencent par une majuscule (PascalCase). Renommons-la San.
La propriété url : Est-elle toujours présente ? Si le type est URI, la value contiendra déjà l'URL. Cette propriété semble redondante. Pour plus de sécurité, nous pouvons la rendre optionnelle avec un ?.
code
TypeScript
// Fichier: Certificate.ts

// ... (imports)

// Définition claire et réutilisable pour un objet SAN
export interface San {
  type: string; // Par ex: 'DNSNAME', 'IPADDRESS'
  value: string; // Par ex: 'example.com', '192.168.1.1'
  url?: string;  // Optionnel, au cas où il est utilisé ailleurs
}

// ...
Étape 2 : Mettre à jour la classe Certificate
Maintenant que nous avons une interface San propre, utilisons-la pour typer la propriété SANS dans la classe Certificate.
code
TypeScript
// Fichier: Certificate.ts

// ...

export class Certificate {
  // ... autres propriétés

  // AVANT
  // SANS: any[];

  // APRÈS : Le tableau est maintenant fortement typé
  SANS: San[];

  // ... reste de la classe
}
Pourquoi c'est important ?
Sécurité : TypeScript vous signalera une erreur si vous essayez d'ajouter quelque chose qui n'est pas un objet San dans ce tableau.
Auto-complétion : Quand vous écrirez monCertificat.SANS[0]., votre éditeur de code vous proposera automatiquement type et value.
Lisibilité : N'importe quel développeur qui lit ce code sait immédiatement à quoi ressemblent les données.
Étape 3 : Adapter la méthode statique fromJSON (Très important)
Cette méthode est responsable de la conversion des données brutes reçues de l'API en une instance de la classe Certificate. Nous devons nous assurer qu'elle crée correctement le tableau SANS.
La méthode fromJSON reçoit data: any. Nous devons explicitement transformer data.SANS en un tableau de type San[].
code
TypeScript
// Fichier: Certificate.ts

export class Certificate {

  // ...

  static fromJSON(data: any): Certificate {
    const certificate = new Certificate();
    
    // ... assignation des autres propriétés

    // ================== MODIFICATION CRUCIALE ==================
    // On s'assure que data.SANS existe, et on le transforme en un tableau de San.
    // Cela garantit que même si l'API envoie des propriétés en trop, 
    // on ne garde que ce qui nous intéresse (type, value).
    if (data.SANS && Array.isArray(data.SANS)) {
      certificate.SANS = data.SANS.map(san => ({
        type: san.type,
        value: san.value
        // On peut ignorer la propriété 'url' si on ne l'utilise pas
      }));
    } else {
      // Si aucune donnée SAN n'est reçue, on initialise avec un tableau vide.
      certificate.SANS = [];
    }
    // =============================================================

    // ... le reste de la méthode fromJSON

    return certificate;
  }

  // ...
}
Résumé : Le Fichier Certificate.ts Modifié
Voici à quoi devrait ressembler votre fichier final.
code
TypeScript
// src/app/shared/beans/Certificate.ts

// ... autres imports si nécessaire

/**
 * Interface définissant la structure d'un Subject Alternative Name (SAN).
 */
export interface San {
  type: string;
  value: string;
  url?: string;
}

export class Certificate {
  // ... toutes les autres propriétés
  certificateName: string;
  // ...

  // La propriété SANS est maintenant fortement typée.
  SANS: San[];

  // ...

  constructor() {
    this.commonName = this.certificateName;
    // Initialiser le tableau pour éviter les erreurs si aucune donnée n'est chargée
    this.SANS = []; 
  }

  /**
   * Crée une instance de Certificate à partir d'un objet JSON brut (ex: réponse d'API).
   */
  static fromJSON(data: any): Certificate {
    const certificate = new Certificate();

    // ... assignation de toutes les autres propriétés depuis 'data'
    Object.assign(certificate, data);

    // Transformation et typage sécurisé du tableau SANS
    if (data.SANS && Array.isArray(data.SANS)) {
      certificate.SANS = data.SANS.map(san => ({
        type: san.type,
        value: san.value
      }));
    } else {
      certificate.SANS = [];
    }

    // ... autre logique spécifique à fromJSON (ex: gestion de csrFile, componentToSign)

    return certificate;
  }
}
Avec ces modifications, votre modèle de données (Certificate.ts) est maintenant parfaitement aligné avec la logique de votre formulaire Angular. Cela résoudra les problèmes de liaison de données et rendra votre application beaucoup plus fiable.
////////////////////////////// certificatsRequest.ts//////////////////////////////
La Solution : Déléguer la Création
Nous devons modifier CertificateRequest.fromJSON pour qu'il délègue la création de l'objet certificate à la méthode Certificate.fromJSON.
Modification à apporter dans CertificateRequest.ts
code
TypeScript
// src/app/shared/beans/CertificateRequest.ts

import { Certificate, San } from './Certificate'; // Importez San si ce n'est pas déjà fait

// ...

export class CertificateRequest {
  // ... propriétés
  certificate: Certificate = new Certificate();
  // ...

  static fromJSON(data: any): CertificateRequest {
    const certificateRequest = new CertificateRequest();

    // ... assignation des propriétés directes (id, status, requestDate, etc.)
    // La logique existante pour ces champs est probablement correcte.
    Object.assign(certificateRequest, data); // Gardons cette base

    // ================== MODIFICATION CRUCIALE ==================
    // Au lieu de simplement copier l'objet, nous allons créer une
    // véritable instance de Certificate en utilisant sa propre méthode fromJSON.
    if (data.certificate) {
      certificateRequest.certificate = Certificate.fromJSON(data.certificate);
    } else {
      // S'il n'y a pas de données de certificat, on s'assure d'avoir une instance vide.
      certificateRequest.certificate = new Certificate();
    }
    // =============================================================

    // ... le reste de la logique de fromJSON (gestion des contacts, des fichiers, etc.)

    return certificateRequest;
  }
  
  // ...
}
Analyse des Autres Méthodes
La méthode toJSON()
Regardons comment les données sont renvoyées à l'API. Dans vos captures d'écran, je vois :
code
TypeScript
// Dans la méthode toJSON() de CertificateRequest
'certificate': {
    // ...
    'sans': this.sansAsJSON(),
    // ...
}
Elle appelle une méthode privée sansAsJSON().
La méthode private sansAsJSON()
code
TypeScript
private sansAsJSON(): object[] {
  console.log(this);
  if (!this.certificate || !Array.isArray(this.certificate.SANS)) {
    console.log('Coucou');
    return [];
  }

  console.log("Voirs les sans du certificatRequest:", this.certificate.SANS);
  return this.certificate.SANS.map(
    san => ({
      type: san.type,
      value: san.value
    })
  );
}
Bonne nouvelle : cette méthode est parfaite ! Elle est déjà conçue pour fonctionner avec un tableau d'objets San ayant les propriétés type et value.
Grâce aux modifications que nous avons apportées :
this.certificate.SANS sera maintenant un San[] fortement typé.
Le map fonctionnera de manière plus sûre, avec l'auto-complétion et la vérification de type.
Vous n'avez donc rien à changer dans les méthodes toJSON ou sansAsJSON. Elles bénéficieront automatiquement de la robustesse que nous avons ajoutée en amont.
Résumé des Actions
Typage Fort : Vous avez bien défini l'interface San et typé Certificate.SANS en San[] dans Certificate.ts.
Instanciation Correcte : Vous avez mis à jour Certificate.fromJSON pour qu'il mappe correctement les données brutes vers un San[].
Délégation : La dernière étape est de modifier CertificateRequest.fromJSON pour qu'il appelle Certificate.fromJSON(data.certificate).
Une fois cette dernière modification effectuée, votre chaîne de données sera cohérente et fortement typée depuis la réception des données de l'API jusqu'à leur utilisation dans votre formulaire Angular.
///////////////////////// FormComponent ///////////////////////////////////////
1. Le Point le Plus Important : La Création de l'Objet certificateRequest
Dans votre constructor, vous recevez les données brutes de la route. Ces données sont des objets JSON génériques, pas des instances de vos classes. Vous devez explicitement utiliser votre méthode fromJSON pour les transformer.
Problème :
code
TypeScript
// Ligne ~86
.subscribe(([data, params]) => {
    const request = data.request as CertificateRequest; // <-- Ceci ne fait qu'un 'cast' de type, pas une vraie conversion. 'request' reste un objet JSON simple.
    // ...
});
Solution :
Nous devons appeler CertificateRequest.fromJSON pour créer une véritable instance, qui à son tour appellera Certificate.fromJSON.
code
TypeScript
// Fichier: form.component.ts (dans le constructor)

// ...
.subscribe(([data, params]) => {
    // ================== CORRECTION CRUCIALE ==================
    // On transforme l'objet JSON brut en une véritable instance de CertificateRequest
    // C'est cette ligne qui va déclencher toute notre logique de typage.
    const request = CertificateRequest.fromJSON(data.request);
    // =======================================================
    const action = params.get('action');

    // Le reste du code peut maintenant travailler avec un objet 'request' propre et bien typé.
    this.isNew = false;
    this.isDuplicate = this.isDuplicateRequest(action);
    // ...
});
2. La Création du Formulaire (createForm)
Votre méthode createForm passe les données au sous-composant RequestDetailSectionComponent pour qu'il construise sa partie du formulaire. Il faut s'assurer de lui passer les bonnes données.
Problème :
code
TypeScript
// Ligne ~174
requestDetails: RequestDetailSectionComponent.buildRequestDetailForm(request),
La méthode buildRequestDetailForm attend un objet Certificate (et un comment), mais vous lui passez l'objet request entier, qui est de type CertificateRequest.
Solution :
Passez l'objet certificate contenu à l'intérieur de la requête.
code
TypeScript
// Fichier: form.component.ts (dans la méthode createForm)

private createForm(request: CertificateRequest) {
    // ...
    this.form = this.fb.group({
        // ... autres form groups
        requestDetails: RequestDetailSectionComponent.buildRequestDetailForm(request.certificate, request.certificate.comment),
        // ...
    });
    // ...
}
3. La Synchronisation des Données (Formulaire -> Modèle)
Quand l'utilisateur modifie le formulaire, vous avez une méthode manageRequestDetailSection qui remet à jour votre objet this.certificateRequest. Il faut vérifier que la mise à jour des SANs est correcte.
Problème :
code
TypeScript
// Ligne ~407
this.certificateRequest.certificate.SANS = this.form.get(this.REQUEST_DETAILS_FORM_NAME).value.SANS?.map(san => ({ san })).filter(san => san);
Le .map(san => ({ san })) est incorrect. Il prend un objet {type, value} et le transforme en { san: {type, value} }, ce qui ne correspond pas à notre interface San[].
Solution :
Le formulaire vous donne déjà les données dans le bon format. Il suffit de filtrer les éventuelles lignes vides et d'assigner le résultat.
code
TypeScript
// Fichier: form.component.ts (dans la méthode manageRequestDetailSection)

// ...
.subscribe(status => {
    if (status === 'VALID') {
        const formValue = this.form.get(this.REQUEST_DETAILS_FORM_NAME).value;
        // ... assignation des autres propriétés

        // ================== CORRECTION DE LA SYNCHRONISATION ==================
        // On récupère le tableau SANS du formulaire.
        const sansFromForm = formValue.SANS || [];

        // On filtre les entrées qui pourraient être vides ou invalides (optionnel mais prudent)
        this.certificateRequest.certificate.SANS = sansFromForm.filter(san => san && san.value);
        // ======================================================================
    }
});
Résumé des Modifications pour FormComponent.ts
Dans le constructor : Utilisez CertificateRequest.fromJSON(data.request) pour créer une instance correctement typée de votre modèle de données dès le départ. C'est la correction la plus importante.
Dans createForm : Passez request.certificate à la méthode buildRequestDetailForm pour que le sous-formulaire soit initialisé avec les bonnes données.
Dans manageRequestDetailSection : Simplifiez la mise à jour de this.certificateRequest.certificate.SANS pour qu'elle corresponde à la structure San[].
En effectuant ces changements, vous aurez une chaîne de données cohérente et robuste à travers toute votre application :
API (JSON) -> Modèles (fromJSON) -> Formulaire Angular -> Modèles (synchronisation) -> API (toJSON).
//////////////////////////////////////
// Décommentez ce bloc si vous l'aviez laissé en commentaire
this._formConstraintsService.getConstraint()
    .pipe(
        // On intercepte chaque 'constraint' émis par le service
        map(originalConstraint => {

            // On crée une copie de l'objet original pour ne pas le modifier directement
            const modifiedConstraint = { ...originalConstraint };

            // On s'assure que la structure 'fields' existe
            if (!modifiedConstraint.fields) {
                modifiedConstraint.fields = {};
            }

            // On ajoute notre permission pour les SANs
            modifiedConstraint.fields['SANS'] = { required: false };
            
            // On retourne le nouvel objet modifié qui continuera son chemin vers le subscribe
            return modifiedConstraint;
        })
    )
    .subscribe(constraint => {
        // Le 'constraint' reçu ici est maintenant NOTRE version modifiée
        console.log('Contenu du constraint MODIFIÉ reçu :', constraint);
        this.constraint = constraint;
		// On récupère le FormArray 'sans'. Il peut ne pas exister encore.
        const sansArray = this.requestDetailSectionForm.get('sans') as FormArray;

    console.log('--- DÉBUT DU TEST DE PERSISTANCE ---');
    if (sansArray) {
        console.log('Longueur du FormArray AVANT notre ajout :', sansArray.length);
        
        if (sansArray.length === 0) {
            sansArray.push(this.createSanGroup());
            console.log('Longueur du FormArray JUSTE APRÈS notre ajout :', sansArray.length); // Devrait afficher 1
        }
    } else {
        console.log('Le FormArray "sans" n\'existe pas, nous allons le créer.');
        this.requestDetailSectionForm.addControl('sans', this.fb.array([ this.createSanGroup() ]));
        console.log('Le FormArray a été créé et contient maintenant 1 élément.');
    }

    // On vérifie l'état du FormArray un tout petit peu plus tard, après que les autres logiques aient eu le temps de réagir
    setTimeout(() => {
        const finalSansArray = this.requestDetailSectionForm.get('sans') as FormArray;
        console.log('Longueur du FormArray 10ms PLUS TARD :', finalSansArray.length); // <-- C'est le log le plus important !
        console.log('--- FIN DU TEST DE PERSISTANCE ---');
    }, 10); // Un délai très court suffit
// ▲▲▲ FIN DE LA SOLUTION ▲▲▲

// ...

/////////////////////
this._formConstraintsService.getConstraint()
    .pipe(
        // On intercepte chaque 'constraint' émis par le service
        map(originalConstraint => {
            // On crée une copie profonde de l'objet pour éviter les mutations inattendues
            const modifiedConstraint = JSON.parse(JSON.stringify(originalConstraint || {}));

            // On s'assure que la structure 'fields' existe
            if (!modifiedConstraint.fields) {
                modifiedConstraint.fields = {};
            }

            // On ajoute notre permission pour les SANs pour forcer l'affichage de la section
            modifiedConstraint.fields['SANS'] = { required: false };
            
            // On retourne le nouvel objet modifié
            return modifiedConstraint;
        })
    )
    .subscribe(constraint => {
        // Le 'constraint' reçu ici est notre version modifiée
        this.constraint = constraint;

        // On s'assure qu'il y a au moins un champ de saisie SAN.
        // On récupère le FormArray 'sans'. Il peut ne pas exister encore.
        let sansArray = this.requestDetailSectionForm.get('sans') as FormArray;

        // Si le FormArray 'sans' n'existe pas du tout dans le formulaire, on le crée.
        if (!sansArray) {
            this.requestDetailSectionForm.addControl('sans', this.fb.array([ this.createSanGroup() ]));
        } 
        // S'il existe mais qu'il est vide, on ajoute le premier champ.
        else if (sansArray.length === 0) {
            sansArray.push(this.createSanGroup());
        }

        // ** LA CORRECTION FINALE **
        // On force Angular à rafraîchir la vue pour qu'il voie le contenu du FormArray.
        this.cdRef.detectChanges();
    });

// =========================================================================
// ▲▲▲ FIN DU BLOC ▲▲▲
// =========================================================================

///////////////////////////////
<div style="background: #222; color: #eee; padding: 15px; margin: 15px; border: 1px solid red;">
    <h3>État du Formulaire de Détail</h3>
    <p>Formulaire valide ? <strong>{{ requestDetailSectionForm.valid }}</strong></p>
    
    <h4>Erreurs du formulaire global :</h4>
    <pre>{{ requestDetailSectionForm.errors | json }}</pre>

    <h4>Détail des erreurs par champ :</h4>
    <pre>{{ getFormErrors() | json }}</pre>
</div>
///////////////////

getFormErrors() {
    const errors = {};
    Object.keys(this.requestDetailSectionForm.controls).forEach(key => {
        const controlErrors = this.requestDetailSectionForm.get(key).errors;
        if (controlErrors != null) {
            errors[key] = controlErrors;
        }
    });
    return errors;
}
////////////////////////
case 'edit':
    console.log("Voir info dans le request dans edit",request);
    this.createForm(request);
    break;

// ▼▼▼ MODIFIEZ CE BLOC ▼▼▼
case 'create':
case null:
case undefined:
    // 1. Créez un objet de base vide
    const newRequest = new CertificateRequest(); 
    
    // 2. Pré-remplissez les valeurs par défaut qui débloquent le formulaire
    newRequest.certificate = {
        usage: 'INTERNE', // Ou 'EXTERNE', selon ce qui est le plus courant
        certificateType: 'SSL_TLS_SERVER', // LE PLUS IMPORTANT : Mettez ici un type qui autorise les SANs
        requestType: RequestType.CREATION,
        // Ajoutez d'autres valeurs par défaut si nécessaire pour activer le bouton "Suivant"
    };

    // 3. Appelez createForm avec ce nouvel objet pré-rempli
    this.createForm(newRequest); 
    break;
// ▲▲▲ FIN DE LA MODIFICATION ▲▲▲

default:
    this.router.navigate(['/pageNotFound']);
    break;

// ...
/////////////////////
 if (!this.isRenew) {
        const sansArray = this.requestDetailSectionForm.get('sans') as FormArray;
        
        // S'il est vide, on ajoute le premier champ.
        if (sansArray && sansArray.length === 0) {
            sansArray.push(this.createSanGroup());
        }
    }
	////////////////////////
	 // On vérifie si on est en mode création (isRenew est false)
    if (!this.isRenew) {
        // On récupère le FormArray 'sans'.
        const sansArray = this.requestDetailSectionForm.get('SANS') as FormArray; // Note: 'SANS' en majuscules

        // Si le FormArray existe mais qu'il est vide (ce qui est le cas pour 'create')
        if (sansArray && sansArray.length === 0) {
            // On y ajoute le premier champ par défaut.
            sansArray.push(this.createSanGroup());
        }
    }
	///////////////////
	
createSanGroup(): FormGroup {
    const sanGroup = this.fb.group({
        // ▼▼▼ CORRECTION 1 : Initialiser avec un objet ▼▼▼
        type: [{ name: SanType.DNSNAME }, Validators.required],
        value: ['', [Validators.required, Validators.pattern(SANS_REGEX_PATTERNS[SanType.DNSNAME])]],
    });

    // On écoute les changements sur le dropdown de type de SAN
    const typeControl = sanGroup.get('type');
    if (typeControl) {
        typeControl.valueChanges
            .pipe(takeUntil(this.onDestroy$))
            .subscribe(selectedTypeObject => { // La valeur est maintenant un objet { name: '...' }
                
                // ▼▼▼ CORRECTION 2 : Extraire le nom de l'objet ▼▼▼
                const typeName = selectedTypeObject ? selectedTypeObject.name : null;
                const valueControl = sanGroup.get('value');
                
                if (typeName && valueControl) {
                    const regex = SANS_REGEX_PATTERNS[typeName];
                    // On met à jour les validateurs du champ de saisie en fonction du type choisi
                    if (regex) {
                        valueControl.setValidators([Validators.required, Validators.pattern(regex)]);
                    } else {
                        valueControl.setValidators(Validators.required);
                    }
                    valueControl.updateValueAndValidity();
                }
            });
    }

    return sanGroup;
}
////////////////////////
 console.log('Bouton "Ajouter un SAN" cliqué !'); // <-- AJOUTEZ CECI

    const sans = this.requestDetailSectionForm.get('SANS') as FormArray;
    
    console.log('Nombre de SANs AVANT ajout :', sans.length);
    sans.push(this.createSanGroup());
    console.log('Nombre de SANs APRÈS ajout :', sans.length);
	////////////////////////////////// create ///////////////////////////////
	code
Html
<div style="background: #282c34; color: #abb2bf; padding: 15px; margin: 15px; border: 2px solid #56b6c2; border-radius: 5px;">
    <h3>🕵️‍♂️ Inspecteur de la Section 'requestDetails'</h3>
    <p>Cette section est-elle valide ? 
        <strong [style.color]="requestDetailSectionForm.valid ? '#98c379' : '#e06c75'">
            {{ requestDetailSectionForm.valid }}
        </strong>
    </p>
    <h4>Détail des erreurs pour cette section :</h4>
    <pre>{{ getFormErrors() | json }}</pre>
</div>
Dans request-detail-section.component.ts (comme méthode de la classe) :
code
TypeScript
getFormErrors() {
    if (!this.requestDetailSectionForm) { return null; }
    const errors = {};
    Object.keys(this.requestDetailSectionForm.controls).forEach(key => {
        const control = this.requestDetailSectionForm.get(key);
        if (control && control.invalid) {
            if (key === 'SANS' && control instanceof FormArray) {
                const sanErrors = [];
                control.controls.forEach((sanGroup, index) => {
                    if (sanGroup.invalid) {
                        sanErrors.push({ index: index, errors: sanGroup.errors, value: sanGroup.value });
                    }
                });
                if (sanErrors.length > 0) errors[key] = sanErrors;
            } else {
                errors[key] = control.errors;
            }
        }
    });
    return errors;
}
///////////////////////
getFormErrors() {
    if (!this.requestDetailSectionForm) { return null; }
    
    const result = {};
    Object.keys(this.requestDetailSectionForm.controls).forEach(key => {
        const control = this.requestDetailSectionForm.get(key);
        // On ne s'intéresse qu'aux contrôles qui sont invalides ET qui ont des erreurs
        if (control && control.invalid && control.errors) {
            result[key] = control.errors;
        }
    });

    // Cas spécial pour le FormArray SANS
    const sansArray = this.requestDetailSectionForm.get('SANS') as FormArray;
    if (sansArray && sansArray.invalid && sansArray.errors) {
        result['SANS_ARRAY_ERRORS'] = sansArray.errors;
    }
    
    // On inspecte aussi chaque élément à l'intérieur du FormArray
    const invalidSans = [];
    sansArray.controls.forEach((group, index) => {
        if (group.invalid) {
            invalidSans.push({ index: index, errors: group.errors });
        }
    });

    if (invalidSans.length > 0) {
        result['SANS_CONTROLS'] = invalidSans;
    }

    return result;
}
////////////////////////
// DANS le subscribe de typeControl.valueChanges
.subscribe(selectedTypeObject => {
    console.log('--- Changement de type de SAN ---');
    console.log('Objet sélectionné:', selectedTypeObject);
    
    const typeName = selectedTypeObject ? selectedTypeObject.name : null;
    console.log('Nom du type extrait:', typeName);
    
    const valueControl = sanGroup.get('value');
    if (typeName && valueControl) {
        const regex = SANS_REGEX_PATTERNS[typeName];
        console.log('Regex correspondante:', regex);
        
        if (regex) {
            console.log('Application du validateur pattern.');
            valueControl.setValidators([Validators.required, Validators.pattern(regex)]);
        } else {
            console.log('Application du validateur required uniquement.');
            valueControl.setValidators(Validators.required);
        }
        
        valueControl.updateValueAndValidity();
        console.log('Validateurs mis à jour. État du contrôle "value":', valueControl.errors);
    }
    console.log('---------------------------------');
});
/////////////////////////////////
this.certificateRequest.certificate.environment = this.form.get(THIS.REQUEST_DETAILS_FORM_NAME).value.environment;
this.certificateRequest.certificate.certificateType = this.form.get(THIS.REQUEST_DETAILS_FORM_NAME).value.certificateType;
this.certificateRequest.certificate.certificateSubType = this.form.get(THIS.REQUEST_DETAILS_FORM_NAME).value.certificateSubType;
// ... etc ...
this.certificateRequest.certificate.certificateName = this.form.get(THIS.REQUEST_DETAILS_FORM_NAME).value.certificateName;

// V-- REMPLACEZ PAR CE BLOC DE CODE --V
const sansFromForm = this.form.get(THIS.REQUEST_DETAILS_FORM_NAME).value.SANS;
if (sansFromForm) {
    this.certificateRequest.certificate.SANS = sansFromForm.filter(san => san && san.value && san.value.trim() !== '');
} else {
    this.certificateRequest.certificate.SANS = [];
}
// A------------------------------------A

this.certificateRequest.certificate.comment = this.form.get(THIS.REQUEST_DETAILS_FORM_NAME).value.comment;
/////////////////////////////////
// DANS la méthode manageRequestDetailsSection()

// ...
const sansFromForm = this.form.get(THIS.REQUEST_DETAILS_FORM_NAME).value.SANS;
// AJOUTEZ CETTE LIGNE
console.log('1. [SYNC] SANs lus depuis le formulaire :', JSON.parse(JSON.stringify(sansFromForm)));

if (sansFromForm) {
    this.certificateRequest.certificate.SANS = sansFromForm.filter(san => san && san.value && san.value.trim() !== '');
} else {
    this.certificateRequest.certificate.SANS = [];
}
// AJOUTEZ CETTE LIGNE
console.log('2. [SYNC] SANs APRES le filtre dans certificateRequest :', JSON.parse(JSON.stringify(this.certificateRequest.certificate.SANS)));
// ...
(J'utilise JSON.parse(JSON.stringify(...)) pour être certain que la console affiche une "photographie" de l'objet à l'instant T et non une référence qui se met à jour).
2. Modification dans onSubmit()
C'est l'endroit le plus important. Nous allons vérifier l'état des données juste au moment où l'utilisateur clique sur le bouton pour soumettre.
code
TypeScript
// DANS la méthode onSubmit()

onSubmit(): void {
    // AJOUTEZ CES 3 LIGNES AU TOUT DÉBUT DE LA MÉTHODE
    console.log('3. [SUBMIT] Valeur TOTALE du formulaire au moment du clic :', this.form.value);
    console.log('4. [SUBMIT] Etat de `this.certificateRequest` AU DÉBUT de onSubmit :', JSON.parse(JSON.stringify(this.certificateRequest)));

    const certificateRequest = this.certificateRequest;
    
    // AJOUTEZ CETTE LIGNE JUSTE AVANT L'APPEL AU SERVICE
    console.log('5. [SUBMIT] Payload FINAL juste AVANT envoi au service :', certificateRequest);

    let request$: Observable<any>;
    // ... le reste de la méthode
}
//////////////////////////////////////
public static RequestType getInstance(String certificateType) {
    // V-- AJOUTEZ CE BLOC DE CODE --V
    if (certificateType == null) {
        // Si le type est null, on retourne un type par défaut pour éviter le crash.
        // C'est ce que votre code fait déjà à la fin, donc c'est logique.
        return new DefaultCertificateType();
    }
    // A-----------------------------A

    RequestType requestType = null;
    if (certificateType.contains(RequestType.list.SSL.toString())) {
        requestType = new SslCertificate();
    } 
    // ... reste de votre code ...
    
    // Cette partie gère déjà le cas où aucun type ne correspond
    else if (requestType == null) {
        return new DefaultCertificateType();
    }
    return requestType;
}
//////////////////
// Cherchez un code qui ressemble à ça :
this.dataService.getCertificateTypes(usage).subscribe(types => {
    this.certificateTypes = types; // Vous peuplez la liste des options

    // AJOUTEZ CETTE LOGIQUE :
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    if (types && types.length > 0) {
        // Si aucune valeur n'est déjà sélectionnée OU si la valeur sélectionnée n'est plus valide
        if (!certificateTypeControl.value || !types.some(t => t.value === certificateTypeControl.value)) {
             // On sélectionne la première option de la nouvelle liste par défaut
            certificateTypeControl.setValue(types[0].value); // Assurez-vous d'utiliser la bonne propriété (ex: types[0].code ou types[0].name)
        }
    } else {
        // S'il n'y a pas de types, on vide le champ
        certificateTypeControl.setValue(null);
    }
});
/////////////////////////////////
getCertificateTypes(): void {
    // ... (début du code inchangé) ...

    this.certificateTypeList = usage.valueChanges
        .pipe(
            // ... (les premiers opérateurs pipe inchangés) ...
            switchMap((usage) => this.dataService.getCertificateTypes(usage)), // La requête est faite ici
            map(asSelectItem), // Transforme les données pour l'affichage
            tap((types) => {
                // 'types' est maintenant la liste des options pour le dropdown
                let certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
                
                if (types && types.length > 0) {
                    // Si aucune valeur n'est sélectionnée OU si l'ancienne n'est plus valide
                    if (!certificateTypeControl.value || !types.some(t => t.value === certificateTypeControl.value)) {
                        // On définit la première option comme nouvelle valeur par défaut
                        certificateTypeControl.setValue(types[0].value); 
                    }
                } else {
                    // S'il n'y a plus d'options, on s'assure que le champ est bien vide
                    certificateTypeControl.setValue(null);
                }
                
                // On peut maintenant réactiver le champ
                certificateType.enable();
                loading.disable();
            })
        );
}
////////////////////////////
getCertificateTypes(): void {
    // On récupère les contrôles du formulaire une seule fois pour plus de clarté
    const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    // On s'abonne aux changements de la valeur de "usage"
    usageControl.valueChanges.pipe(
        startWith(usageControl.value), // Déclenche immédiatement avec la valeur actuelle
        tap(() => {
            // A chaque changement d'usage, on désactive le champ "type" et on montre le chargement
            certificateTypeControl.reset(null, { emitEvent: false }); // On vide le champ sans déclencher d'autres événements
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        switchMap(usageValue => {
            // On appelle le service pour obtenir les nouveaux types de certificat
            // Si usageValue est null, on retourne un tableau vide pour éviter une erreur
            if (!usageValue) {
                return of([]); // 'of' vient de 'rxjs', il faut peut-être l'importer
            }
            return this.dataService.getCertificateTypes(usageValue);
        }),
        map(asSelectItem), // On formate les données pour le composant dropdown
        tap(availableTypes => {
            // C'est ici que la magie opère !
            // 'availableTypes' est la nouvelle liste d'options (ex: [{label: 'SSL', value: 'SSL_SERVER'}, ...])
            
            // On met à jour la liste des options dans le composant
            this.certificateTypeList = availableTypes; // Assurez-vous que cette variable est bien celle utilisée par votre HTML

            if (availableTypes && availableTypes.length > 0) {
                // S'il y a au moins une option disponible, on sélectionne la première par défaut
                certificateTypeControl.setValue(availableTypes[0].value); // On utilise la propriété 'value' de l'option
                certificateTypeControl.enable(); // On réactive le champ pour que l'utilisateur puisse changer
            } else {
                // S'il n'y a aucune option, le champ reste désactivé et vide
                certificateTypeControl.disable();
            }

            // On cache l'indicateur de chargement
            loadingControl.setValue(false);
        }),
        takeUntil(this.onDestroy$) // Pour éviter les fuites de mémoire, si vous utilisez cette pratique
    ).subscribe();
}
///////////////////////////
 const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    usageControl.valueChanges.pipe(
        startWith(usageControl.value),
        tap(() => {
            certificateTypeControl.reset(null, { emitEvent: false });
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        // Début du switchMap
        switchMap(usageValue => {
            if (!usageValue) {
                return of([]); // Retourne un Observable qui contient un tableau vide
            }

            // L'appel au service ET sa transformation se font A L'INTERIEUR
            return this.dataService.getCertificateTypes(usageValue).pipe(
                map(asSelectItem) // <-- LE MAP EST ICI, AU BON ENDROIT
            );
        }),
        // Fin du switchMap

        // Maintenant, 'tap' reçoit un tableau de SelectItem, c'est garanti
        tap(availableTypes => {
            this.certificateTypeList = availableTypes; // Plus d'erreur ici !

            if (availableTypes && availableTypes.length > 0) { // Plus d'erreur ici !
                certificateTypeControl.setValue(availableTypes[0].value);
                certificateTypeControl.enable();
            } else {
                certificateTypeControl.disable();
            }

            loadingControl.setValue(false);
        }),
        takeUntil(this.onDestroy$) // Si vous l'utilisez
    ).subscribe();
	///////////////////////////
	// Assurez-vous d'avoir les imports en haut de votre fichier
import { of } from 'rxjs';
import { startWith, tap, switchMap, map } from 'rxjs/operators';

// ... (dans votre classe RequestDetailSectionComponent)

getCertificateTypes(): void {
    const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    usageControl.valueChanges.pipe(
        startWith(usageControl.value),
        tap(() => {
            certificateTypeControl.reset(null, { emitEvent: false });
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        // Début du switchMap
        switchMap(usageValue => {
            if (!usageValue) {
                return of([]); // Retourne un Observable qui contient un tableau vide
            }

            // L'appel au service ET sa transformation se font A L'INTERIEUR
            return this.dataService.getCertificateTypes(usageValue).pipe(
                map(asSelectItem) // <-- LE MAP EST ICI, AU BON ENDROIT
            );
        }),
        // Fin du switchMap

        // Maintenant, 'tap' reçoit un tableau de SelectItem, c'est garanti
        tap(availableTypes => {
            this.certificateTypeList = availableTypes; // Plus d'erreur ici !

            if (availableTypes && availableTypes.length > 0) { // Plus d'erreur ici !
                certificateTypeControl.setValue(availableTypes[0].value);
                certificateTypeControl.enable();
            } else {
                certificateTypeControl.disable();
            }
			
			
			//////////////////////////////////////
			const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    usageControl.valueChanges.pipe(
        startWith(usageControl.value),
        tap(() => {
            certificateTypeControl.reset(null, { emitEvent: false });
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        switchMap(usageValue => {
            if (!usageValue) {
                return of([]);
            }
            // CHAÎNAGE CORRECT : service().pipe(map(...))
            return this.dataService.getCertificateTypes(usageValue).pipe(
                map(asSelectItem)
            );
        }),
        tap(availableTypes => {
            this.certificateTypeList = availableTypes;

            if (availableTypes && availableTypes.length > 0) {
                certificateTypeControl.setValue(availableTypes[0].value);
                certificateTypeControl.enable();
            } else {
                certificateTypeControl.disable();
            }
            loadingControl.setValue(false);
        }),
        takeUntil(this.onDestroy$)
    ).subscribe();
	
	///////////////////////////////////
	getCertificateTypes(): void {
    const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    usageControl.valueChanges.pipe(
        startWith(usageControl.value),
        tap(() => {
            certificateTypeControl.reset(null, { emitEvent: false });
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        switchMap(usageValue => {
            if (!usageValue) {
                return of([]);
            }
            // CHAÎNAGE CORRECT : service().pipe(map(...))
            return this.dataService.getCertificateTypes(usageValue).pipe(
                map(asSelectItem)
            );
        }),
        tap(availableTypes => {
            this.certificateTypeList = availableTypes;

            if (availableTypes && availableTypes.length > 0) {
                certificateTypeControl.setValue(availableTypes[0].value);
                certificateTypeControl.enable();
            } else {
                certificateTypeControl.disable();
            }
            loadingControl.setValue(false);
        }),
        takeUntil(this.onDestroy$)
    ).subscribe();
}
//////////////////////////////////////////
getCertificateTypes(): void {
    const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    // On stocke l'observable dans une variable.
    // L'HTML devra utiliser : <p-dropdown [options]="certificateTypeList | async"></p-dropdown>
    this.certificateTypeList = usageControl.valueChanges.pipe(
        startWith(usageControl.value),
        tap(() => {
            // 1. On réinitialise et désactive le champ à chaque changement d'usage
            certificateTypeControl.reset(null, { emitEvent: false });
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        // 2. On appelle le service. switchMap est plus moderne que flatMap, mais les deux fonctionnent.
        switchMap(usageValue => {
            if (!usageValue) {
                return of([]); // Si pas d'usage, on retourne un tableau vide
            }
            return this.dataService.getCertificateTypes(usageValue);
        }),
        // 3. LA CORRECTION PRINCIPALE EST ICI. Un seul 'tap' pour tout gérer.
        tap(types => {
            // 'types' est le tableau brut qui vient du service

            // On formate ces données pour le dropdown
            const selectItems = asSelectItems(types); // On appelle la transformation ici

            if (selectItems && selectItems.length > 0) {
                // S'il y a des options, on prend la première comme valeur par défaut
                certificateTypeControl.setValue(selectItems[0].value);
                certificateTypeControl.enable(); // Et on active le champ
            } else {
                // S'il n'y a pas d'options, le champ reste désactivé
                certificateTypeControl.disable();
            }

            loadingControl.setValue(false); // On cache le chargement
        }),
        // 4. L'étape finale transforme les données pour le template HTML
        map(types => asSelectItems(types))
    );
}
/////////////////
getCertificateTypes(): void {
    const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    // On stocke l'observable dans une variable.
    // L'HTML devra utiliser : <p-dropdown [options]="certificateTypeList | async"></p-dropdown>
    this.certificateTypeList = usageControl.valueChanges.pipe(
        startWith(usageControl.value),
        tap(() => {
            // 1. On réinitialise et désactive le champ à chaque changement d'usage
            certificateTypeControl.reset(null, { emitEvent: false });
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        // 2. On appelle le service. switchMap est plus moderne que flatMap, mais les deux fonctionnent.
        switchMap(usageValue => {
            if (!usageValue) {
                return of([]); // Si pas d'usage, on retourne un tableau vide
            }
            return this.dataService.getCertificateTypes(usageValue);
        }),
        // 3. LA CORRECTION PRINCIPALE EST ICI. Un seul 'tap' pour tout gérer.
        tap(types => {
            // 'types' est le tableau brut qui vient du service

            // On formate ces données pour le dropdown
            const selectItems = asSelectItems(types); // On appelle la transformation ici

            if (selectItems && selectItems.length > 0) {
                // S'il y a des options, on prend la première comme valeur par défaut
                certificateTypeControl.setValue(selectItems[0].value);
                certificateTypeControl.enable(); // Et on active le champ
            } else {
                // S'il n'y a pas d'options, le champ reste désactivé
                certificateTypeControl.disable();
            }

            loadingControl.setValue(false); // On cache le chargement
        }),
        // 4. L'étape finale transforme les données pour le template HTML
        map(types => asSelectItems(types))
    );
}
///////////////////////////////////////////
getCertificateTypes(): void {
    const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    this.certificateTypeList = usageControl.valueChanges.pipe(
        startWith(usageControl.value),
        tap(() => {
            // Étape 1 : On prépare le formulaire pour le changement
            certificateTypeControl.reset(null, { emitEvent: false });
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        switchMap(usageValue => {
            // Étape 2 : On appelle le service pour chercher les données
            if (!usageValue) {
                return of([]); // Retourne un tableau vide si pas d'usage
            }
            return this.dataService.getCertificateTypes(usageValue);
        }),
        tap(typesBruts => {
            // Étape 3 : On utilise les données brutes pour mettre à jour le formulaire
            // On transforme les données une première fois juste pour la logique
            const options = asSelectItems(typesBruts);

            if (options && options.length > 0) {
                // Si on a des options, on met la première comme valeur par défaut
                certificateTypeControl.setValue(options[0].value);
                certificateTypeControl.enable();
            } else {
                // Sinon, le champ reste désactivé
                certificateTypeControl.disable();
            }
            loadingControl.setValue(false);
        }),
        // Étape 4 : On re-transforme les données pour le template HTML
        map(typesBruts => asSelectItems(typesBruts))
    );
}
//////////////////////////////

getCertificateTypes(): void {
    const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    // On assigne l'Observable à la variable de classe.
    this.certificateTypeList = usageControl.valueChanges.pipe(
        startWith(usageControl.value),
        tap(() => {
            certificateTypeControl.reset(null, { emitEvent: false });
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        switchMap(usageValue => {
            if (!usageValue) {
                return of([]);
            }
            return this.dataService.getCertificateTypes(usageValue);
        }),
        tap(typesBruts => {
            const options = asSelectItems(typesBruts);

            if (options && options.length > 0) {
                certificateTypeControl.setValue(options[0].value);
                certificateTypeControl.enable();
            } else {
                certificateTypeControl.disable();
            }
            loadingControl.setValue(false);
        }),
        map(typesBruts => asSelectItems(typesBruts))
        
    ); // <-- La parenthèse ici ferme le .pipe() et le point-virgule termine l'instruction.
}

            loadingControl.setValue(false);
        }),
        takeUntil(this.onDestroy$) // Si vous l'utilisez
    ).subscribe();
}
///////////////////////////
getCertificateTypes(): void {
    const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    // On assigne l'Observable à la variable de classe.
    this.certificateTypeList = usageControl.valueChanges.pipe(
        startWith(usageControl.value),
        tap(() => {
            certificateTypeControl.reset(null, { emitEvent: false });
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        switchMap(usageValue => {
            if (!usageValue) {
                return of([]);
            }
            return this.dataService.getCertificateTypes(usageValue);
        }),
        tap(typesBruts => {
            const options = asSelectItems(typesBruts);

            if (options && options.length > 0) {
                certificateTypeControl.setValue(options[0].value);
                certificateTypeControl.enable();
            } else {
                certificateTypeControl.disable();
            }
            loadingControl.setValue(false);
        }),
        map(typesBruts => asSelectItems(typesBruts))
        
    ); // <-- La parenthèse ici ferme le .pipe() et le point-virgule termine l'instruction.
}
///////////////////////
getCertificateTypes(): void {
    console.log("DEMARRAGE de getCertificateTypes()"); // LOG 1

    const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    this.certificateTypeList = usageControl.valueChanges.pipe(
        startWith(usageControl.value),
        tap(() => {
            console.log("CHANGEMENT D'USAGE. Réinitialisation du type de certif."); // LOG 2
            certificateTypeControl.reset(null, { emitEvent: false });
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        switchMap(usageValue => {
            console.log("Appel du service avec l'usage :", usageValue); // LOG 3
            if (!usageValue) {
                return of([]);
            }
            return this.dataService.getCertificateTypes(usageValue);
        }),
        tap(typesBruts => {
            console.log("Réponse du service (données brutes) :", typesBruts); // LOG 4

            const options = asSelectItems(typesBruts);
            console.log("Données transformées en 'options' :", options); // LOG 5

            if (options && options.length > 0) {
                const premiereOption = options[0].value;
                console.log("ACTION : On va définir la valeur par défaut à :", premiereOption); // LOG 6
                certificateTypeControl.setValue(premiereOption);
                certificateTypeControl.enable();
            } else {
                console.log("ACTION : Aucune option disponible. Le champ reste désactivé."); // LOG 7
                certificateTypeControl.disable();
            }
            loadingControl.setValue(false);
        }),
        map(typesBruts => asSelectItems(typesBruts))
    );
}
//////////////////////////////////////
 console.log("%c JE SUIS BIEN DANS RequestDetailSectionComponent ! Le composant vient de se charger.", "color: lime; font-size: 20px;");
 ////////////////////////////////////////////////////////////////
 getCertificateTypes(): void {
    // Récupération des contrôles du formulaire
    const usageControl = this.requestDetailSectionForm.get('usage');
    const certificateTypeControl = this.requestDetailSectionForm.get('certificateType');
    const loadingControl = this.requestDetailSectionForm.get('certificateLoading');

    // Assigner l'Observable à la variable de classe.
    // Le template HTML doit utiliser le pipe `| async` sur cette variable.
    this.certificateTypeList = usageControl.valueChanges.pipe(
        startWith(usageControl.value), // Déclenche immédiatement avec la valeur actuelle
        tap(() => {
            // Étape 1 : Réinitialiser le champ "Type de certificat" à chaque changement d'usage
            certificateTypeControl.reset(null, { emitEvent: false });
            certificateTypeControl.disable();
            loadingControl.setValue(true);
        }),
        switchMap(usageValue => {
            // Étape 2 : Appeler le service pour obtenir la liste des types
            if (!usageValue) {
                return of([]); // Si pas d'usage, retourner une liste vide
            }
            return this.dataService.getCertificateTypes(usageValue);
        }),
        tap(typesBruts => {
            // Étape 3 (LA CORRECTION) : Utiliser la liste pour définir une valeur par défaut
            
            // On transforme les données pour pouvoir les utiliser
            const options = asSelectItems(typesBruts);

            if (options && options.length > 0) {
                // S'il y a des options, on sélectionne la PREMIÈRE comme valeur par défaut
                certificateTypeControl.setValue(options[0].value);
                certificateTypeControl.enable(); // On réactive le champ
            } else {
                // S'il n'y a pas d'options, le champ reste désactivé
                certificateTypeControl.disable();
            }
            
            // On cache l'indicateur de chargement
            loadingControl.setValue(false);
        }),
        // Étape 4 : Transformer les données brutes pour les passer au template HTML
        map(typesBruts => asSelectItems(typesBruts))
    );
}
Rappels importants pour que cela fonctionne :
Nom de la fonction : J'ai utilisé asSelectItems (au pluriel). Si votre fonction s'appelle asSelectItem (au singulier), corrigez le nom dans les deux endroits où elle est utilisée dans le code ci-dessus.
HTML : Votre fichier request-detail-section.component.html doit utiliser le async pipe sur la liste déroulante, comme ceci :
code
Html
<p-dropdown [options]="certificateTypeList | async" formControlName="certificateType"></p-dropdown>
this.fb.group({
  dropdowns: this.fb.array([]),
});
///////////////////////////////
mport { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-certificate-details',
  templateUrl: './certificate-details.component.html',
  styleUrls: ['./certificate-details.component.scss']
})
export class CertificateDetailsComponent implements OnInit {

  public certificateRequest: any; 

  private sanTypes = [
    { label: 'DNSNAME',       value: 'DNSNAME',       styleClass: 'styleMapper[DNSNAME]' },
    { label: 'RFC822NAME',    value: 'RFC822NAME',    styleClass: 'styleMapper[RFC822NAME]' },
    { label: 'IPADDRESS',     value: 'IPADDRESS',     styleClass: 'styleMapper[IPADDRESS]' },
    { label: 'URI',           value: 'URI',           styleClass: 'styleMapper[URI]' },
    { label: 'OTHERNAME_GUID',value: 'OTHERNAME_GUID',styleClass: 'styleMapper[OTHERNAME_GUID]' },
    { label: 'OTHERNAME_UPN', value: 'OTHERNAME_UPN', styleClass: 'styleMapper[OTHERNAME_UPN]' }
  ];

  constructor() { }

  ngOnInit(): void {
    // Logique pour charger les données du certificat
  }

  public sanStyle(type: string): string {
    const foundType = this.sanTypes.find(san => san.value === type);
    if (foundType) {
        return foundType.styleClass;
    }
    return 'default-san-style';
  }
}
/////////////////////////////////////////////////////
// 1. L'énumération que vous avez déjà
export enum SanType {
  DNSNAME = 'DNSNAME',
  RFC822NAME = 'RFC822NAME',
  IPADDRESS = 'IPADDRESS',
  URI = 'URI',
  OTHERNAME_GUID = 'OTHERNAME_GUID',
  OTHERNAME_UPN = 'OTHERNAME_UPN',
}

// 2. Le mapper de style que vous avez déjà
export const styleMapper = {
  [SanType.DNSNAME]: 'badge-dnsname',
  [SanType.RFC822NAME]: 'badge-rfc822name',
  [SanType.IPADDRESS]: 'badge-ipaddress',
  [SanType.URI]: 'badge-uri',
  [SanType.OTHERNAME_GUID]: 'badge-othername',
  [SanType.OTHERNAME_UPN]: 'badge-othername_upn',
};

// 3. Les patterns Regex que vous avez déjà
export const SANS_REGEX_PATTERNS = {
  // ... vos regex ici
};

// 4. === AJOUT : Le tableau d'options pour les dropdowns ===
// On le génère dynamiquement à partir de l'énumération. C'est plus propre !
export const SAN_TYPES_OPTIONS = Object.values(SanType).map(type => ({
  label: type,
  value: type,
  styleClass: styleMapper[type]
}));

// 5. L'interface que vous avez déjà
export interface San {
  type?: string;
  value?: string;
  url?: string;
}
/////////////////////////////
// Fichier: request-detail-section.component.ts
import { Component, OnInit } from '@angular/core';
// Importez la nouvelle constante depuis utils.ts
import { SAN_TYPES_OPTIONS } from 'src/app/shared/utils';

@Component({
  //...
})
export class RequestDetailSectionComponent implements OnInit {
  
  // Utilisez directement la constante importée. Plus besoin de la définir ici !
  sanTypes = SAN_TYPES_OPTIONS;

  // ... le reste de votre composant
}
B. Dans le composant d'affichage (certificate-details.component.ts) :
code
TypeScript
// Fichier: certificate-details.component.ts
import { Component, OnInit } from '@angular/core';
// Importez le styleMapper et SanType depuis utils.ts
import { styleMapper, SanType } from 'src/app/shared/utils';

@Component({
  //...
})
export class CertificateDetailsComponent implements OnInit {
  // ...
  
  // La fonction sanStyle devient beaucoup plus simple !
  public sanStyle(type: string): string {
    // On utilise directement le styleMapper importé
    return styleMapper[type as SanType] || 'badge-default';
  }
  
  // ... le reste de votre composant
}
////////////////////////////////////////
Cette solution est plus élégante et plus sûre. Au lieu de forcer le type, vous allez créer une petite fonction qui vérifie si la string est bien une valeur valide de votre énumération SanType.
1. Créez la fonction de garde dans utils.ts :
Ajoutez cette fonction à votre fichier utils.ts. Elle va vérifier si une chaîne de caractères donnée fait partie des valeurs possibles de l'énumération SanType.
code
TypeScript
// Fichier: src/app/shared/utils.ts

// ... (votre enum SanType et styleMapper) ...

// AJOUTEZ CETTE FONCTION
export function isSanType(value: string): value is SanType {
  return Object.values(SanType).includes(value as SanType);
}
value is SanType est la partie magique : si la fonction retourne true, TypeScript saura que la variable value est bien de type SanType dans le reste du bloc de code.
2. Utilisez la fonction de garde dans votre composant :
Maintenant, dans votre composant, vous pouvez utiliser cette fonction pour avoir un code propre et sans erreur.
code
TypeScript
// Fichier: certificate-details.component.ts

import { styleMapper, SanType, isSanType } from 'src/app/shared/utils'; // N'oubliez pas d'importer isSanType

// ...

public sanStyle(type: string): string {
  // On utilise notre fonction de garde pour vérifier le type
  if (isSanType(type)) {
    // A l'intérieur de ce 'if', TypeScript est certain que 'type' est de type SanType
    return styleMapper[type];
  }

  // Si le type n'est pas valide, on retourne la valeur par défaut
  return 'badge-default';
}
//////////////////////////////////////////////
Solution 2 : La Fonction de Garde (plus sûre et plus explicite)
Cette solution est plus élégante et plus sûre. Au lieu de forcer le type, vous allez créer une petite fonction qui vérifie si la string est bien une valeur valide de votre énumération SanType.
1. Créez la fonction de garde dans utils.ts :
Ajoutez cette fonction à votre fichier utils.ts. Elle va vérifier si une chaîne de caractères donnée fait partie des valeurs possibles de l'énumération SanType.
code
TypeScript
// Fichier: src/app/shared/utils.ts

// ... (votre enum SanType et styleMapper) ...

// AJOUTEZ CETTE FONCTION
export function isSanType(value: string): value is SanType {
  return Object.values(SanType).includes(value as SanType);
}
value is SanType est la partie magique : si la fonction retourne true, TypeScript saura que la variable value est bien de type SanType dans le reste du bloc de code.
2. Utilisez la fonction de garde dans votre composant :
Maintenant, dans votre composant, vous pouvez utiliser cette fonction pour avoir un code propre et sans erreur.
code
TypeScript
// Fichier: certificate-details.component.ts

import { styleMapper, SanType, isSanType } from 'src/app/shared/utils'; // N'oubliez pas d'importer isSanType

// ...

public sanStyle(type: string): string {
  // On utilise notre fonction de garde pour vérifier le type
  if (isSanType(type)) {
    // A l'intérieur de ce 'if', TypeScript est certain que 'type' est de type SanType
    return styleMapper[type];
  }

  // Si le type n'est pas valide, on retourne la valeur par défaut
  return 'badge-default';
}
Quelle solution choisir ?
Solution 1 (Double Cast) : Parfaite pour une correction rapide et si vous êtes certain que les données type correspondront toujours à votre énumération. C'est la plus concise.
Solution 2 (Fonction de Garde) : C'est la méthode la plus robuste et la plus "propre" selon les standards de TypeScript. Elle rend votre code plus sûr en gérant explicitement les cas où la string d'entrée ne serait pas un SanType valide.
Pour votre cas, les deux sont excellentes. La solution 1 est probablement suffisante et plus rapide à mettre en place.