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