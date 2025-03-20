public class LdapCertInfoDTO {
    private String pgpUserID;
    private String pgpCertisID;

    // Constructors
    public LdapCertInfoDTO() {}

    public LdapCertInfoDTO(String pgpUserID, String pgpCertisID) {
        this.pgpUserID = pgpUserID;
        this.pgpCertisID = pgpCertisID;
    }

    // Getters & Setters
    public String getPgpUserID() { return pgpUserID; }
    public void setPgpUserID(String pgpUserID) { this.pgpUserID = pgpUserID; }

    public String getPgpCertisID() { return pgpCertisID; }
    public void setPgpCertisID(String pgpCertisID) { this.pgpCertisID = pgpCertisID; }
}
public class LdapCertInfoDTOMapper implements AttributesMapper<LdapCertInfoDTO> {

    @Override
    public LdapCertInfoDTO mapFromAttributes(Attributes attrs) throws NamingException {
        String pgpUserID = attrs.get("pgpUserID") != null ? attrs.get("pgpUserID").get().toString() : null;
        String pgpCertisID = attrs.get("pgpCertisID") != null ? attrs.get("pgpCertisID").get().toString() : null;

        return new LdapCertInfoDTO(pgpUserID, pgpCertisID);
    }
}
public LdapCertInfoDTO findLdapCertInfoDTOByCN(String cn) {
    try {
        String dn = String.format("CN=%s,OU=Users,DC=example,DC=com", cn);
        LdapQuery query = LdapQueryBuilder.query()
                .where("pgpUserID").is(dn)
                .and("pgpCertisID").isPresent();

        List<LdapCertInfoDTO> results = ldapTemplate.search(query, new LdapCertInfoDTOMapper());

        return results.isEmpty() ? null : results.get(0);

    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
}
