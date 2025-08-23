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