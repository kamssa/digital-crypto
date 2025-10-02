Objectif : Gérer tous les types de SAN (Subject Alternative Name).
Types de SAN à gérer : DNSNAME, RFC822NAME, IPADDRESS, OTHERNAME_GUID, OTHERNAME_UPN, URI.
Demande initiale :
Ajouter une colonne type non nulle dans la table SAN.
Pour les données existantes, affecter la valeur DNSNAME au champ type.
Renommer la colonne url en san_value.
Dans les DTOs, conserver le champ url et ajouter le champ san_value.
Décision finale prise au cours de nos échanges :
Nous avons décidé de ne pas renommer url, mais de la conserver et d'ajouter une nouvelle colonne san_value.
Pour les données existantes, la valeur de url a été copiée dans san_value.
2. Scripts SQL pour la Base de Données
Script de Sauvegarde (à exécuter en premier)
code
SQL
-- Remplacez YYYYMMDD par la date du jour, par exemple : 20240927
CREATE TABLE SAN_BACKUP_YYYYMMDD AS
SELECT * FROM OWN_19382_COP.SAN;
Script de Modification de la Table SAN
code
SQL
-- SCRIPT DE MODIFICATION DE LA TABLE SAN (Version finale : AJOUT de colonnes)

-- Étape 1 : Ajouter les deux nouvelles colonnes.
ALTER TABLE OWN_19382_COP.SAN
ADD (
    type       VARCHAR2(20),
    san_value  VARCHAR2(100)
);

-- Étape 2 : Mettre à jour toutes les lignes existantes.
UPDATE OWN_19382_COP.SAN
SET
    type = 'DNSNAME',
    san_value = url;

-- Étape 3 : Appliquer les contraintes NOT NULL.
ALTER TABLE OWN_19382_COP.SAN
MODIFY (
    type      NOT NULL,
    san_value NOT NULL
);

-- Étape 4 (Recommandé) : Ajouter une contrainte CHECK pour le type.
ALTER TABLE OWN_19382_COP.SAN
ADD CONSTRAINT san_type_chk CHECK (type IN ('DNSNAME', 'RFC822NAME', 'IPADDRESS', 'OTHERNAME_GUID', 'OTHERNAME_UPN', 'URI'));

-- Étape finale : Valider toutes les modifications.
COMMIT;
3. Fichiers Java Modifiés et Leur Code Final
Fichier : SanTypeEnum.java (Utilisé à la fois par l'Entité et le DTO)
code
Java
package com.bnpparibas.certis.automationhub.dto.business; // Le package peut varier

public enum SanTypeEnum {
    DNSNAME,
    RFC822NAME,
    IPADDRESS,
    OTHERNAME_GUID,
    OTHERNAME_UPN,
    URI
}
Fichier : San.java (L'Entité JPA)
code
Java
package com.bnpparibas.certis.certificate.request.model;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "SAN")
public class San implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_San_id")
    @SequenceGenerator(name = "seq_San_id", sequenceName = "SEQ_SAN_ID", allocationSize = 1)
    private long id;

    @Column(name = "url", nullable = false, length = 100)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SanTypeEnum type;

    @Column(name = "san_value", nullable = false, length = 100)
    private String sanValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="CERTIFICATE_ID", nullable=false)
    private Certificate certificate;

    public San() {
    }

    // --- GETTERS ET SETTERS pour tous les champs ---
    // (id, url, type, sanValue, certificate)
}
Fichier : SanDto.java (Le Data Transfer Object)
code
Java
package com.bnpparibas.certis.automationhub.dto.business;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class SanDto {

    private SanTypeEnum sanType;

    @JsonProperty("san_value")
    private String sanValue;

    private String url;

    // --- GETTERS, SETTERS, equals et hashCode pour tous les champs ---
    // (sanType, sanValue, url)
}
Fichier : AutoEnrollService.java (Exemple de logique de conversion DTO -> Entité)
code
Java
// Dans AutoEnrollService.java ou une classe Mapper

private List<San> getAutoEnrollSans(List<SanDto> subjectAlternateNames) {
    if (subjectAlternateNames == null || subjectAlternateNames.isEmpty()) {
        return new ArrayList<>();
    }

    List<San> sanList = new ArrayList<>();

    for (SanDto sanDto : subjectAlternateNames) {
        San sanEntity = new San();
        String value = sanDto.getSanValue();
        SanTypeEnum type = sanDto.getSanType();

        sanEntity.setUrl(value);
        sanEntity.setSanValue(value);
        sanEntity.setType(type);

        sanList.add(sanEntity);
    }
    return sanList;
}
Fichier : EnrollPayloadBuilder.java (ou EnrollNacPayloadBuilder.java - La classe de base)
code
Java
// Dans EnrollPayloadBuilder.java ou EnrollNacPayloadBuilder.java

@Override
protected List<EnrollPayloadTemplateSanDto> buildSan() {

    List<SanDto> inputSans = automationHubRequestDto.getSanList();
    List<EnrollPayloadTemplateSanDto> enrollPayloadTemplateSanDtos = new ArrayList<>();

    if (inputSans == null || inputSans.isEmpty()) {
        String commonName = automationHubRequestDto.getCommonName();
        if (commonName != null && !commonName.isEmpty() && !commonName.contains("|")) {
            EnrollPayloadTemplateSanDto payloadSan = new EnrollPayloadTemplateSanDto();
            payloadSan.setType(SanTypeEnum.DNSNAME.name());
            payloadSan.setValue(Collections.singletonList(commonName));
            enrollPayloadTemplateSanDtos.add(payloadSan);
        }
    } else {
        Map<String, List<String>> sanBuildingMap = new HashMap<>();

        for (SanDto san : inputSans) {
            if (san != null && san.getSanType() != null && san.getSanValue() != null) {
                String sanType = san.getSanType().name();
                List<String> currentValues = sanBuildingMap.computeIfAbsent(sanType, k -> new ArrayList<>());
                currentValues.add(san.getSanValue());
            }
        }

        for (Map.Entry<String, List<String>> entry : sanBuildingMap.entrySet()) {
            EnrollPayloadTemplateSanDto dto = new EnrollPayloadTemplateSanDto();
            dto.setType(entry.getKey());
            dto.setValue(entry.getValue());
            enrollPayloadTemplateSanDtos.add(dto);
        }
    }

    return enrollPayloadTemplateSanDtos;
}
Fichier : EnrollExternalSslPayloadBuilder.java (La classe fille)
code
Java
// Dans EnrollExternalSslPayloadBuilder.java

@Override
protected List<EnrollPayloadTemplateSanDto> buildSan() {
    
    List<EnrollPayloadTemplateSanDto> sanList = super.buildSan();
    
    String commonName = this.automationHubRequestDto.getCommonName();
    
    if (commonName == null || commonName.isEmpty()) {
        return sanList;
    }

    EnrollPayloadTemplateSanDto dnsNameDto = null;
    for (EnrollPayloadTemplateSanDto dto : sanList) {
        if (SanTypeEnum.DNSNAME.name().equalsIgnoreCase(dto.getType())) {
            dnsNameDto = dto;
            break;
        }
    }

    if (dnsNameDto != null) {
        List<String> values = dnsNameDto.getValue() != null ? new ArrayList<>(dnsNameDto.getValue()) : new ArrayList<>();
        if (!values.contains(commonName)) {
            values.add(commonName);
        }
        dnsNameDto.setValue(values);
        
    } else {
        EnrollPayloadTemplateSanDto newDnsNameDto = new EnrollPayloadTemplateSanDto();
        newDnsNameDto.setType(SanTypeEnum.DNSNAME.name());
        newDnsNameDto.setValue(Collections.singletonList(commonName));
        sanList.add(newDnsNameDto);
    }

    return sanList;
}
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
