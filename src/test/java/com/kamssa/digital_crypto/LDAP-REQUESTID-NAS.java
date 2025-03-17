Étape	Action
1️⃣ LDAP	pgpCertisID = findPgpCertisIDByCN(cn)
2️⃣ DB	List<Request> reqs = requestRepository.findByCnOrderByIdDesc(cn)
3️⃣ NAS	Vérifie pgpCertisID.asc dans /mnt/mon_nas
✅ Résultat	Si présent dans NAS → retourne reqs, sinon → []
🧩 Code corrigé (Spring Boot Java 8)
1. Service – LdapNasService.java
java
Copier
Modifier
@Service
public class LdapNasService {

    private static final String NAS_PATH = "/mnt/mon_nas";

    @Autowired
    private LdapTemplate ldapTemplate;

    @Autowired
    private RequestRepository requestRepository;

    /**
     * 🔎 Recherche le pgpCertisID via CN dans le LDAP.
     */
    public String findPgpCertisIDByCN(String cn) {
        try {
            String dn = String.format("CN=%s,OU=Users,DC=example,DC=com", cn);
            LdapQuery query = LdapQueryBuilder.query()
                    .where("distinguishedName").is(dn)
                    .and("pgpCertisID").isPresent();

            List<String> results = ldapTemplate.search(query, (attributes, name) -> {
                return attributes.get("pgpCertisID") != null ? attributes.get("pgpCertisID").get().toString() : null;
            });

            return results.isEmpty() ? null : results.get(0);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 🔍 Vérifie la présence de pgpCertisID.asc dans le NAS.
     */
    private boolean isPgpCertisIDInNas(String pgpCertisID) {
        Path filePath = Paths.get(NAS_PATH, pgpCertisID + ".asc");
        return Files.exists(filePath) && Files.isReadable(filePath);
    }

    /**
     * 👇 Process : LDAP -> DB -> NAS -> Résultat.
     */
    public List<Request> processCN(String cn) {
        String pgpCertisID = findPgpCertisIDByCN(cn);
        if (pgpCertisID == null) {
            System.err.println("🔴 Aucun pgpCertisID trouvé pour CN=" + cn);
            return Collections.emptyList();
        }

        List<Request> requests = requestRepository.findByCnOrderByIdDesc(cn);
        if (requests.isEmpty()) {
            System.err.println("🔴 Aucun Request trouvé pour CN=" + cn);
            return Collections.emptyList();
        }

        if (!isPgpCertisIDInNas(pgpCertisID)) {
            System.err.println("🔴 Fichier NAS non trouvé : " + pgpCertisID + ".asc");
            return Collections.emptyList();
        }

        // ✅ Tout est valide, retourne les Request
        return requests;
    }
}