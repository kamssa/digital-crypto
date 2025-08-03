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