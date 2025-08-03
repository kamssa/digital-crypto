package com.bnpparibas.certis.certificate.request.model;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "SAN") // C'est une bonne pratique d'être explicite
public class San implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_San_id")
    @SequenceGenerator(name = "seq_San_id", sequenceName = "SEQ_SAN_ID", allocationSize = 1)
    private long id;

    // CONSERVÉ : Le champ 'url' est conservé pour correspondre à la colonne existante.
    @Column(name = "url", nullable = false, length = 100)
    private String url;

    // NOUVEAU : Ajout du champ 'type' mappé sur la nouvelle colonne.
    // L'utilisation de @Enumerated(EnumType.STRING) est cruciale.
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SanType type;

    // NOUVEAU : Ajout du champ 'san_value' mappé sur la nouvelle colonne.
    // En Java, on utilise le camelCase (sanValue) pour le nom du champ.
    @Column(name = "san_value", nullable = false, length = 100)
    private String sanValue;

    // La relation avec Certificate ne change pas.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY) // Utiliser FetchType.LAZY est une bonne pratique
    @JoinColumn(name="CERTIFICATE_ID", nullable=false)
    private Certificate certificate;

    // Constructeur par défaut requis par JPA
    public San() {
    }

    // N'oubliez pas de mettre à jour les autres constructeurs si nécessaire.
    
    // --- GETTERS ET SETTERS ---

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public SanType getType() {
        return type;
    }

    public void setType(SanType type) {
        this.type = type;
    }

    public String getSanValue() {
        return sanValue;
    }

    public void setSanValue(String sanValue) {
        this.sanValue = sanValue;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
    }
}
//////////////////////////////////////enum ///////////////////////
public enum SanType {
    
    /**
     * Pour les noms de domaine (ex: "www.example.com")
     */
    DNSNAME,

    /**
     * Pour les adresses e-mail (ex: "user@example.com")
     */
    RFC822NAME,

    /**
     * Pour les adresses IP (ex: "192.168.1.1")
     */
    IPADDRESS,

    /**
     * Pour un Globally Unique Identifier (GUID)
     */
    OTHERNAME_GUID,

    /**
     * Pour un User Principal Name (UPN)
     */
    OTHERNAME_UPN,

    /**
     * Pour un Uniform Resource Identifier (ex: "https://service.example.com")
     */
    URI
}
/////DTO////////////////////////
import com.fasterxml.jackson.annotation.JsonProperty; // Import nécessaire pour l'annotation
import java.util.Objects; // Import nécessaire pour Objects.hash

// Le nom du package peut varier, assurez-vous qu'il soit correct.
// package com.bnpparibas.certis.automationhub.dto.business;

public class SanDto {

    private SanTypeEnum sanType;

    // Le nom en Java est camelCase (sanValue), mais sera "san_value" dans le JSON.
    @JsonProperty("san_value")
    private String sanValue;

    // --> AJOUT : Champ "url" pour la rétrocompatibilité.
    private String url;


    // --- GETTERS ET SETTERS ---

    public SanTypeEnum getSanType() {
        return sanType;
    }

    public void setSanType(SanTypeEnum sanType) {
        this.sanType = sanType;
    }

    // Le getter de sanValue n'a pas besoin de l'annotation,
    // le champ privé suffit si Jackson est bien configuré, mais l'ajouter ici est plus sûr.
    public String getSanValue() {
        return sanValue;
    }

    public void setSanValue(String sanValue) {
        this.sanValue = sanValue;
    }

    // --> AJOUT : Getter et Setter pour le champ 'url'.
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }


    // --- EQUALS ET HASHCODE (Mis à jour) ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SanDto sanDto = (SanDto) o;
        // On compare sur les champs qui définissent l'unicité.
        // Comme url et sanValue sont identiques, comparer l'un des deux suffit.
        return sanType == sanDto.sanType &&
               Objects.equals(sanValue, sanDto.sanValue);
    }

    @Override
    public int hashCode() {
        // Il est crucial de mettre à jour hashCode quand on met à jour equals.
        return Objects.hash(sanType, sanValue);
    }
}
Use code with caution.
Java
Étape 2 : Mettre à jour votre logique de "Mapping" (Le plus important)
Dans la classe où vous transformez l'entité San en SanDto (souvent un Mapper ou un Service), vous devez maintenant remplir les trois champs du DTO.
Voici à quoi ressemblera la méthode de conversion :
Generated java
// Dans une classe de type "SanMapper.java" ou un service...

public SanDto toDto(San sanEntity) {
    if (sanEntity == null) {
        return null;
    }

    SanDto sanDto = new SanDto();
    
    // On suppose que vous avez un Enum SanTypeEnum dans le DTO et SanType dans l'entité.
    // Vous aurez peut-être besoin d'une petite méthode de conversion entre les deux.
    // sanDto.setSanType(convertSanTypeToEnum(sanEntity.getType()));

    // --- C'est la logique clé ---
    // On récupère la valeur de l'entité une seule fois.
    String valueFromUrl = sanEntity.getUrl();
    String valueFromSanValue = sanEntity.getSanValue();

    // On peuple les DEUX champs du DTO avec les valeurs correspondantes.
    sanDto.setUrl(valueFromUrl);             // On remplit le champ 'url'.
    sanDto.setSanValue(valueFromSanValue);   // On remplit le champ 'sanValue'.

    return sanDto;
}
Use code with caution.
Java
Résultat final dans le JSON
Grâce à ces modifications, lorsque votre API retournera cet objet, le JSON généré sera exactement comme attendu :
Generated json
{
  "sanType": "DNSNAME",
  "url": "example.com",
  "san_value": "example.com"
}
////////////////////////////////
// Dans votre classe AutoEnrollService.java

private List<San> getAutoEnrollSans(List<SanDto> subjectAlternateNames) {
    
    // Si la liste en entrée est nulle ou vide, on retourne une liste vide pour éviter les erreurs.
    if (subjectAlternateNames == null || subjectAlternateNames.isEmpty()) {
        return new ArrayList<>(); // Ou Collections.emptyList();
    }

    // On prépare la liste qui contiendra nos entités finales.
    List<San> sanList = new ArrayList<>();

    // On boucle sur chaque DTO reçu.
    for (SanDto sanDto : subjectAlternateNames) {
        
        // On crée une nouvelle instance de notre entité San.
        San sanEntity = new San();

        // 1. On récupère les informations depuis le DTO.
        String value = sanDto.getSanValue();
        SanTypeEnum typeFromDto = sanDto.getSanType();

        // 2. On peuple les champs de valeur de l'entité.
        //    Il est crucial de remplir les deux pour la cohérence des données.
        sanEntity.setUrl(value);
        sanEntity.setSanValue(value);

        // 3. On peuple le champ 'type' de l'entité en utilisant la méthode de conversion.
        //    C'est cette étape qui gère la "traduction" entre les types d'enums.
        sanEntity.setType(convertSanTypeEnumToSanType(typeFromDto));

        // 4. On ajoute l'entité maintenant complète à notre liste de résultats.
        sanList.add(sanEntity);
    }

    // On retourne la liste des entités prêtes à être sauvegardées.
    return sanList;
}

/**
 * Méthode d'aide (helper method) pour convertir l'enum du DTO (SanTypeEnum)
 * en son équivalent pour l'entité (SanType).
 * Cette méthode assure la "traduction" entre le monde de l'API et le monde de la base de données.
 *
 * @param dtoEnum L'enum provenant du DTO.
 * @return L'enum correspondant pour l'entité, ou null si l'entrée est nulle.
 */
private SanType convertSanTypeEnumToSanType(SanTypeEnum dtoEnum) {
    if (dtoEnum == null) {
        return null;
    }
    
    // Cette conversion simple fonctionne car les noms des valeurs
    // dans les deux enums (SanType et SanTypeEnum) sont identiques.
    // Ex: SanTypeEnum.DNSNAME.name() retourne "DNSNAME", et SanType.valueOf("DNSNAME") retourne SanType.DNSNAME.
    return SanType.valueOf(dtoEnum.name());
}
/////////// buildSan///////////////////
@Override
protected List<EnrollPayloadTemplateSanDto> buildSan() {
    
    // On récupère la liste des DTOs en entrée.
    List<SanDto> inputSans = automationHubRequestDto.getSanList();

    // On prépare la liste qui contiendra nos DTOs de payload finaux.
    List<EnrollPayloadTemplateSanDto> enrollPayloadTemplateSanDtos = new ArrayList<>();

    // CAS 1 : La liste de SANs est vide ou nulle.
    // On se rabat sur le "Common Name" comme solution de secours, en appliquant la règle de validation existante.
    if (inputSans == null || inputSans.isEmpty()) {
        
        String commonName = automationHubRequestDto.getCommonName();
        
        // On vérifie que le commonName existe, n'est pas vide, ET ne contient pas le caractère '|'.
        if (commonName != null && !commonName.isEmpty() && !commonName.contains("|")) {
            
            // Si toutes les conditions sont remplies, on crée un SAN à partir du commonName.
            EnrollPayloadTemplateSanDto payloadSan = new EnrollPayloadTemplateSanDto();
            
            // On définit le type comme DNSNAME par défaut, ce qui est logique pour un commonName.
            payloadSan.setType(SanTypeEnum.DNSNAME.name());
            
            // On met le commonName comme valeur.
            // La méthode setValue attend une List<String>, donc on utilise Arrays.asList() pour l'encapsuler.
            payloadSan.setValue(Arrays.asList(commonName));
            
            enrollPayloadTemplateSanDtos.add(payloadSan);
        }
        // Si le commonName contient un '|', il est ignoré (comportement de l'ancien code).
        
    } 
    // CAS 2 : La liste de SANs contient un ou plusieurs éléments.
    // Dans ce cas, on ignore le commonName et on traite la liste.
    else {
        // ON BOUCLE SUR TOUS LES SANS D'ENTRÉE, ET NON PLUS SEULEMENT LE PREMIER.
        for (SanDto inputSan : inputSans) {
            EnrollPayloadTemplateSanDto payloadSan = new EnrollPayloadTemplateSanDto();
            
            // On récupère le type et la valeur de CHAQUE san.
            // On ajoute des vérifications pour éviter les NullPointerException.
            if (inputSan.getSanType() != null) {
                payloadSan.setType(inputSan.getSanType().name());
            }
            
            if (inputSan.getSanValue() != null) {
                // La méthode setValue attend une List<String>.
                payloadSan.setValue(Arrays.asList(inputSan.getSanValue()));
            }
            
            // On ajoute le payload SAN créé à notre liste de résultats.
            enrollPayloadTemplateSanDtos.add(payloadSan);
        }
    }

    return enrollPayloadTemplateSanDtos;
}
///////// version final de builSan/////////////////////////////////
protected List<EnrollPayloadTemplateSanDto> buildSan() {

    List<SanDto> inputSans = automationHubRequestDto.getSanList();
    List<EnrollPayloadTemplateSan-Dto> enrollPayloadTemplateSanDtos = new ArrayList<>();

    // CAS 1 : La liste de SANs est vide ou nulle.
    if (inputSans == null || inputSans.isEmpty()) {
        String commonName = automationHubRequestDto.getCommonName();
        if (commonName != null && !commonName.isEmpty() && !commonName.contains("|")) {
            EnrollPayloadTemplateSanDto payloadSan = new EnrollPayloadTemplateSanDto();
            payloadSan.setType(SanTypeEnum.DNSNAME.name());
            payloadSan.setValue(Collections.singletonList(commonName));
            enrollPayloadTemplateSanDtos.add(payloadSan);
        }
    } 
    // CAS 2 : Une liste de SANs a été fournie.
    else {
        Map<String, List<String>> sanBuildingMap = new HashMap<>();

        // Étape 1 : Remplir la map de regroupement.
        for (SanDto san : inputSans) {
            if (san != null && san.getSanType() != null && san.getSanValue() != null) {
                String sanType = san.getSanType().name();
                
                // On récupère la liste des valeurs pour ce type.
                List<String> currentValues = sanBuildingMap.get(sanType);
                
                // Si cette liste n'existe pas, c'est la première fois qu'on voit ce type.
                if (currentValues == null) {
                    // On crée une nouvelle liste.
                    currentValues = new ArrayList<>();
                    // ET ON LA MET DANS LA MAP. C'est l'étape que vous avez notée.
                    sanBuildingMap.put(sanType, currentValues);
                }
                
                // Maintenant, on est sûr que 'currentValues' est la bonne liste, on y ajoute la valeur.
                currentValues.add(san.getSanValue());
            }
        }

        // Étape 2 : Construire les DTOs finaux à partir de la map.
        for (Map.Entry<String, List<String>> entry : sanBuildingMap.entrySet()) {
            EnrollPayloadTemplateSanDto finalDto = new EnrollPayloadTemplateSanDto();
            finalDto.setType(entry.getKey());
            finalDto.setValue(entry.getValue());
            enrollPayloadTemplateSanDtos.add(finalDto);
        }
    }

    return enrollPayloadTemplateSanDtos;
}