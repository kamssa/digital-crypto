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
     * 🔎 Recherche pgpCertisID en LDAP via CN.
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
     * 🔍 Vérifie si le fichier pgpCertisID.asc existe dans le NAS.
     */
    private boolean isPgpCertisIDInNas(String pgpCertisID) {
        Path filePath = Paths.get(NAS_PATH, pgpCertisID + ".asc");
        return Files.exists(filePath) && Files.isReadable(filePath);
    }

    /**
     * 🔄 Pour chaque Request, vérifie pgpCertisID.asc dans NAS.
     */
    public List<Request> getValidRequestsWithNasFile(String cn) {
        // 1️⃣ LDAP
        String pgpCertisID = findPgpCertisIDByCN(cn);
        if (pgpCertisID == null) {
            System.err.println("🔴 Aucun pgpCertisID trouvé pour CN=" + cn);
            return Collections.emptyList();
        }

        // 2️⃣ Récupère les Requests pour ce CN
        List<Request> allRequests = requestRepository.findByCnOrderByIdDesc(cn);
        if (allRequests.isEmpty()) {
            System.err.println("🔴 Aucun Request pour CN=" + cn);
            return Collections.emptyList();
        }

        // 3️⃣ Filtrer uniquement ceux dont le pgpCertisID.asc existe
        List<Request> validRequests = new ArrayList<>();
        for (Request req : allRequests) {
            if (req.getPgpCertisID() != null && isPgpCertisIDInNas(req.getPgpCertisID())) {
                validRequests.add(req);
            } else {
                System.out.println("❌ Fichier NAS manquant pour Request ID=" + req.getId());
            }
        }

        return validRequests;
    }
}
@RestController
@RequestMapping("/api")
public class RequestController {

    @Autowired
    private NasRequestScannerService scannerService;

    @GetMapping("/requests/valid-nas")
    public ResponseEntity<List<Request>> getValidRequestsFromNas() {
        List<Request> validRequests = scannerService.getAllValidRequestsWithNasFile();
        if (validRequests.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(validRequests);
    }
}

