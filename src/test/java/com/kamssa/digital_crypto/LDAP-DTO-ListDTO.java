public class LdapCertInfoDTOMapper implements AttributesMapper<LdapCertInfoDTO> {

    @Override
    public LdapCertInfoDTO mapFromAttributes(Attributes attrs) throws NamingException {
        String pgpUserID = attrs.get("pgpUserID") != null ? attrs.get("pgpUserID").get().toString() : null;
        String pgpCertisID = attrs.get("pgpCertisID") != null ? attrs.get("pgpCertisID").get().toString() : null;

        return new LdapCertInfoDTO(pgpUserID, pgpCertisID);
    }
}
public List<LdapCertInfoDTO> findLdapCertInfoDTOListByOU(String organizationalUnit) {
    try {
        String baseDn = String.format("OU=%s,DC=example,DC=com", organizationalUnit);
        LdapQuery query = LdapQueryBuilder.query()
                .base(baseDn)
                .where("pgpCertisID").isPresent();

        List<LdapCertInfoDTO> results = ldapTemplate.search(query, new LdapCertInfoDTOMapper());

        return results;

    } catch (Exception e) {
        e.printStackTrace();
        return Collections.emptyList();
    }
}
public class CertInfoDTO {
    private String pgpCertisID;
    private Date issueDate;

    // Constructors, getters, setters
    public CertInfoDTO(String pgpCertisID, Date issueDate) {
        this.pgpCertisID = pgpCertisID;
        this.issueDate = issueDate;
    }

    public String getPgpCertisID() { return pgpCertisID; }
    public void setPgpCertisID(String pgpCertisID) { this.pgpCertisID = pgpCertisID; }

    public Date getIssueDate() { return issueDate; }
    public void setIssueDate(Date issueDate) { this.issueDate = issueDate; }
}
Repository : Native Query vers DTO
java
Copier
Modifier
@Query(value = "SELECT pgp_certis_id AS pgpCertisID, issue_date AS issueDate FROM certificate WHERE pgp_certis_id IS NOT NULL ORDER BY issue_date DESC", nativeQuery = true)
List<CertInfoDTO> findAllCertInfos();
⚠️ Pour que ça fonctionne, il te faut une interface projection si tu utilises JPA natif :

java
Copier
Modifier
public interface CertInfoProjection {
    String getPgpCertisID();
    Date getIssueDate();
}
Puis :

java
Copier
Modifier
@Query(value = "SELECT pgp_certis_id AS pgpCertisID, issue_date AS issueDate FROM certificate WHERE pgp_certis_id IS NOT NULL ORDER BY issue_date DESC", nativeQuery = true)
List<CertInfoProjection> findAllCertInfos();
