@RestController
@RequestMapping("/api/certificates")
public class CertificateRestController {

    private final RequestRepository requestRepository;
    private final CertificateRepository certificateRepository;
    private final LdapService ldapService;
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
     * Exemple : GET /api/certificates?cn=user123
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
        // 1️⃣ Récupérer la Map des pgpCertisID depuis LDAP
        Map<String, String> pgpCertisMap = ldapService.findPgpCertisIDByCN(cn);
        if (pgpCertisMap == null || pgpCertisMap.isEmpty()) {
            System.err.println("🔴 Aucun pgpCertisID trouvé pour CN=" + cn);
            return Optional.empty();
        }

        // 2️⃣ Récupérer les Requests associées au CN, triées du plus récent au plus ancien
        List<Request> allRequests = requestRepository.findByCnOrderByIdDesc(cn);
        if (allRequests.isEmpty()) {
            System.err.println("🔴 Aucun Request trouvé pour CN=" + cn);
            return Optional.empty();
        }

        // 3️⃣ Parcourir chaque pgpCertisID et valider l'existence du fichier NAS
        for (String pgpCertisID : pgpCertisMap.values()) {
            Optional<Request> validRequest = allRequests.stream()
                    .filter(req -> pgpCertisID.equals(req.getPgpCertisID()) && isPgpCertisIDInNas(pgpCertisID))
                    .findFirst();

            // 4️⃣ Si une Request valide est trouvée, retourner le certificat
            if (validRequest.isPresent()) {
                System.out.println("✅ Request ID=" + validRequest.get().getId() + " validée. Récupération du certificat.");
                return certificateRepository.findById(validRequest.get().getCertificateID());
            }
        }

        return Optional.empty();
    }

    /**
     * 📂 Vérifie si un fichier pgpCertisID.asc existe dans le NAS.
     */
    private boolean isPgpCertisIDInNas(String pgpCertisID) {
        Path filePath = Paths.get(NAS_PATH, pgpCertisID + ".asc");
        return Files.exists(filePath) && Files.isReadable(filePath);
    }
}
