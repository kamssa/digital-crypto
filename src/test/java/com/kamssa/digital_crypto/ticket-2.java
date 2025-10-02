Partie 3 : La Solution - Les Fichiers Modifiés et Leurs Codes Finaux
Voici la liste de tous les fichiers que nous avons identifiés et corrigés, avec leur code final.
1. L'entité San.java
Rôle : Le cœur du refactoring. C'est ici que la structure de la donnée a été modifiée.
code
Java
// package com.bnpparibas.certis.certificate.request.model;

@Entity
@Table(name = "SAN")
public class San implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_San_Id")
    @SequenceGenerator(name = "seq_San_Id", sequenceName = "SEQ_SAN_ID", allocationSize = 1)
    private Long id;

    // L'ancien champ 'url' a été renommé en 'value'
    @Column(name = "san_value")
    private String value;

    // Le nouveau champ 'type', non-nul
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private SanType type;
    
    // Le champ 'url' est gardé temporairement pour la transition, avec des setters synchronisés
    @Transient // Important: Indique à JPA de ne pas essayer de persister ce champ
    private String url;

    // bi-directional many-to-one association to Certificate
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "CERTIFICATE_ID", nullable = false)
    private Certificate certificate;

    public San() {
    }

    // --- GETTERS ET SETTERS SYNCHRONISÉS ---

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        this.url = value; // Synchronisation
    }

    public SanType getType() {
        return type;
    }

    public void setType(SanType type) {
        this.type = type;
    }
    
    // Ancien getter/setter pour la compatibilité (sera supprimé plus tard)
    public String getUrl() {
        return this.value; // Retourne toujours la nouvelle valeur
    }

    public void setUrl(String url) {
        this.value = url; // Met à jour la nouvelle valeur
        this.url = url;
    }
    
    // ... autres getters et setters (id, certificate)
}
2. L'énumération SanType.java
Rôle : Définit les types de SANs possibles et contient la logique de validation par regex.
code
Java
// package com.bnpparibas.certis.certificate.request.model.enums;

public enum SanType {
    DNSNAME("^[a-zA-Z0-9\\.\\-\\*_]*$"),
    RFC822NAME("^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$"),
    IPADDRESS("^((25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(?!$)|$)){4}$"),
    OTHERNAME_GUID("^[a-fA-F0-9]{32}$"),
    OTHERNAME_UPN("^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$"),
    URI("^(https?|ldaps?|ftp|file|tag|urn|data|tel):[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");

    private final Pattern pattern;

    SanType(String regex) {
        this.pattern = Pattern.compile(regex);
    }
    
    public boolean isValid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        return this.pattern.matcher(value).matches();
    }
}
3. Le service UtilityService.java
Rôle : Centralise la logique de déduction du type de SAN et l'utilise pour intégrer les SANs d'un CSR.
code
Java
@Service
public class UtilityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UtilityService.class);

    /**
     * MÉTHODE CENTRALISÉE : Déduit le type d'un SAN à partir de sa valeur.
     */
    public SanType deduceSanTypeFromString(String sanValue) {
        if (sanValue == null || sanValue.trim().isEmpty()) {
            throw new IllegalArgumentException("La valeur du SAN ne peut pas être nulle ou vide.");
        }
        if (sanValue.matches("^[a-fA-F0-9]{32}$")) return SanType.OTHERNAME_GUID;
        if (sanValue.matches("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")) return SanType.IPADDRESS;
        if (sanValue.matches("^(https?|ldaps?|ftp|file|tag|urn|data|tel):.*")) return SanType.URI;
        if (sanValue.contains("@") && (sanValue.endsWith(".local") || !sanValue.matches(".*\\.(com|org|net|fr|io|dev|biz|info)$"))) return SanType.OTHERNAME_UPN;
        if (sanValue.contains("@")) return SanType.RFC822NAME;
        return SanType.DNSNAME;
    }

    /**
     * Intègre les SANs d'un CSR dans un certificat, en utilisant la logique de déduction.
     */
    public void integrateCsrSans(RequestDto requestDto) {
        try {
            String decodedCsr = new String(Base64.getDecoder().decode(requestDto.getCsr()), StandardCharsets.UTF_8);
            CertificateCsrDecoder csrDecoder = new CertificateCsrDecoder();
            List<String> sanStringsFromCsr = csrDecoder.getSansList(decodedCsr);

            List<San> sansInCsr = sanStringsFromCsr.stream()
                    .map(sanString -> {
                        San newSan = new San();
                        newSan.setValue(sanString);
                        newSan.setType(this.deduceSanTypeFromString(sanString));
                        return newSan;
                    }).collect(Collectors.toList());

            List<San> allSans = Stream.of(sansInCsr, requestDto.getCertificate().getSans())
                    .flatMap(list -> list == null ? Stream.empty() : list.stream())
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(San::getValue, san -> san, (e, r) -> e),
                            map -> new ArrayList<>(map.values())
                    ));

            requestDto.getCertificate().setSans(allSans);
            LOGGER.info("Intégration des SANs du CSR terminée. Nombre total de SANs : {}", allSans.size());
        } catch (Exception e) {
            LOGGER.error("Erreur critique lors de l'intégration des SANs à partir du CSR :", e);
        }
    }
}
4. Le service SanServiceImpl.java (ou similaire)
Rôle : Contient la logique métier liée aux SANs. Plusieurs méthodes ont été corrigées.
code
Java
@Service
public class SanServiceImpl implements SanService {

    // ...

    public RequestDto buildSANs(RequestDto requestDto) {
        // ... (votre logique 'try/catch' et 'if (RequestTypeList...)')
        List<San> sanList = requestDto.getCertificate().getSans();
        if (sanList == null) sanList = new ArrayList<>();

        Set<String> existingSanValues = sanList.stream().map(San::getValue).collect(Collectors.toSet());
        String cn = requestDto.getCertificate().getCommonName();

        if (cn != null && !cn.trim().isEmpty()) {
            String lowerCaseCn = cn.toLowerCase().trim();
            String domain = lowerCaseCn.startsWith("www.") ? lowerCaseCn.substring(4) : lowerCaseCn;
            String domainWWW = "www." + domain;

            if (!existingSanValues.contains(domain)) {
                San sanDomain = new San();
                sanDomain.setValue(domain);
                sanDomain.setType(SanType.DNSNAME);
                sanList.add(sanDomain);
            }
            if (!existingSanValues.contains(domainWWW)) {
                San sanDomainWWW = new San();
                sanDomainWWW.setValue(domainWWW);
                sanDomainWWW.setType(SanType.DNSNAME);
                sanList.add(sanDomainWWW);
            }
        }
        requestDto.getCertificate().setSans(sanList);
        return requestDto;
    }

    public List<String> validateSansOnRefweb(RequestDto requestDto) throws Exception {
        // ...
        List<String> urls = requestDto.getCertificate().getSans().stream()
                .map(San::getValue) // <-- CORRECTION ICI
                .collect(Collectors.toList());
        // ...
    }
    
    // ... (la méthode evaluateSan3W a été corrigée de la même manière que buildSANs)
}
5. Les Mappers (FilterRequestMapper.java, RequestDtoToAutomationHubRequestDtoMapper.java, etc.)
Rôle : Gèrent la conversion entre les entités et les DTOs. C'est ici qu'on a géré la rétrocompatibilité.
Exemple FilterRequestMapper.java :
code
Java
@Mapper(componentModel = "spring")
public interface FilterRequestMapper {
    // ... autres mappings

    @Mapping(source = "value", target = "url") // Rétrocompatibilité
    @Mapping(source = "type", target = "sanType")
    FilterSanDto sanToFilterSanDto(San san);

    @Mapping(source = "type", target = "sanType")
    SanDto toSanDto(San san);

    // Méthodes de conversion pour les enums
    default SanTypeEnum toSanTypeEnum(SanType sanType) {
        return (sanType == null) ? null : SanTypeEnum.valueOf(sanType.name());
    }
    default SanType toSanType(SanTypeEnum sanTypeEnum) {
        return (sanTypeEnum == null) ? null : SanType.valueOf(sanTypeEnum.name());
    }
}
Exemple RequestDtoToAutomationHubRequestDtoMapper.java :
code
Java
@Mapper(componentModel = "spring")
public abstract class RequestDtoToAutomationHubRequestDtoMapper {
    // ...

    protected SanDto toSanDto(San sourceSan) {
        if (sourceSan == null) return null;
        SanDto res = new SanDto();
        res.setSanValue(sourceSan.getValue());
        res.setSanType(toSanTypeEnum(sourceSan.getType()));
        return res;
    }

    private SanTypeEnum toSanTypeEnum(SanType sanType) {
        return (sanType == null) ? null : SanTypeEnum.valueOf(sanType.name());
    }
    
    // ...
}
Partie 4 : La Prochaine Étape - La Migration de la Base de Données
Votre code est maintenant correct. La toute dernière étape, avant de déployer, est de préparer un script de migration de base de données pour mettre à jour le schéma de votre table SAN.
Créez un script SQL (par exemple pour Flyway ou à exécuter manuellement) :
code
SQL
-- Étape 1: Ajouter la nouvelle colonne 'type', en l'autorisant à être nulle pour le moment.
ALTER TABLE SAN ADD COLUMN type VARCHAR(255);

-- Étape 2: Remplir la nouvelle colonne avec la valeur par défaut pour toutes les lignes existantes.
UPDATE SAN SET type = 'DNSNAME';

-- Étape 3: Maintenant que toutes les lignes ont une valeur, appliquer la contrainte NOT NULL.
-- La syntaxe peut varier légèrement selon votre base de données (PostgreSQL, Oracle, MySQL...).
-- Pour Oracle/PostgreSQL :
ALTER TABLE SAN ALTER COLUMN type SET NOT NULL;
-- Pour MySQL :
-- ALTER TABLE SAN MODIFY COLUMN type VARCHAR(255) NOT NULL;

-- Étape 4: Renommer l'ancienne colonne 'url' en 'san_value'.
-- La syntaxe varie beaucoup selon la base de données.
-- Pour PostgreSQL/Oracle :
ALTER TABLE SAN RENAME COLUMN url TO san_value;
-- Pour MySQL :
-- ALTER TABLE SAN CHANGE COLUMN url san_value VARCHAR(255); -- (Assurez-vous que le type de colonne est correct)
Une fois ce script exécuté sur votre base de données, votre code et votre base de données seront parfaitement synchronisés. Votre refactoring sera alors un succès complet.
Use Arrow Up and Arrow Down to select a turn, Enter to jump to it, and Escape to return to the chat.
