@RestController
@RequestMapping("/api/certificates")
public class CertificateRestController {

    private final RequestRepository requestRepository;
    private final CertificateRepository certificateRepository;
    private final LdapService ldapService; // Service pour gérer la recherche LDAP
    private static final String NAS_PATH = "/mnt/nas/";

    public CertificateRestController(RequestRepository requestRepository,
                                     CertificateRepository certificateRepository,
                                     LdapService ldapService) {
        this.requestRepository = requestRepository;
        this.certificateRepository = certificateRepository;
        this.ldapService = ldapService;
    }

    /**
     * 🔍 Endpoint pour récupérer le certificat à partir d'un CN.
     * Exemple d'appel : GET /api/certificates?cn=user123
     */
    @GetMapping
    public ResponseEntity<?> getCertificateByCn(@RequestParam String cn) {
        return findCertificateFromValidRequest(cn)
                .map(cert -> ResponseEntity.ok().body(cert))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ Aucun certificat valide trouvé pour CN=" + cn));
    }

    /**
     * 📜 Recherche la première Request valide et retourne le certificat associé.
     */
    private Optional<Certificate> findCertificateFromValidRequest(String cn) {
        // 1️⃣ Récupérer le pgpCertisID à partir de LDAP
        String pgpCertisID = ldapService.findPgpCertisIDByCN(cn);
        if (pgpCertisID == null) {
            System.err.println("🔴 Aucun pgpCertisID trouvé pour CN=" + cn);
            return Optional.empty();
        }

        // 2️⃣ Récupérer les Requests associées au CN, triées du plus récent au plus ancien
        List<Request> allRequests = requestRepository.findByCnOrderByIdDesc(cn);
        if (allRequests.isEmpty()) {
            System.err.println("🔴 Aucun Request trouvé pour CN=" + cn);
            return Optional.empty();
        }

        // 3️⃣ Trouver la première Request valide avec un fichier NAS existant
        return allRequests.stream()
                .filter(req -> pgpCertisID.equals(req.getPgpCertisID()) && isPgpCertisIDInNas(req.getPgpCertisID()))
                .findFirst()
                .flatMap(req -> {
                    System.out.println("✅ Request ID=" + req.getId() + " validée. Récupération du certificat.");
                    return certificateRepository.findById(req.getCertificateID());
                });
    }

    /**
     * 📂 Vérifie si un fichier pgpCertisID.asc existe dans le NAS.
     */
    private boolean isPgpCertisIDInNas(String pgpCertisID) {
        Path filePath = Paths.get(NAS_PATH, pgpCertisID + ".asc");
        return Files.exists(filePath) && Files.isReadable(filePath);
    }
}
