✅ Résumé des améliorations
✅ Recherche pgpCertisID en LDAP avec CN
✅ Vérifie pgpCertisID.asc dans le NAS
✅ Vérifie pgpCertisID dans la base Certificate
✅ Vérifie pgpCertisID dans Request
✅ Retourne le certificat si tout est validé

import org.springframework.cache.annotation.Cacheable;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Service
public class NasLdapScannerService {

    private static final String NAS_PATH = "/mnt/mon_nas"; // Modifier selon ton NAS
    private final CertificateRepository certificateRepository;
    private final RequestRepository requestRepository;
    private final LdapTemplate ldapTemplate;

    public NasLdapScannerService(CertificateRepository certificateRepository, RequestRepository requestRepository, LdapTemplate ldapTemplate) {
        this.certificateRepository = certificateRepository;
        this.requestRepository = requestRepository;
        this.ldapTemplate = ldapTemplate;
    }

    /**
     * Recherche un certificat via son CN, puis vérifie sa présence dans LDAP, le NAS et Request.
     */
    @Cacheable(value = "ldapKeysCache", key = "#cn")
    public Optional<Certificate> findCertificateByCN(String cn) {
        // 1️⃣ Recherche `pgpCertisID` en LDAP via CN
        String pgpCertisID = searchPgpCertisIDInLdap(cn);
        if (pgpCertisID == null) {
            System.err.println("Aucun pgpCertisID trouvé pour CN=" + cn);
            return Optional.empty();
        }

        // 2️⃣ Vérifier la présence du `pgpCertisID` dans le NAS
        if (!isPgpCertisIDInNas(pgpCertisID)) {
            System.err.println("pgpCertisID non trouvé dans le NAS : " + pgpCertisID);
            return Optional.empty();
        }

        // 3️⃣ Vérifier dans Certificate en base de données
        Optional<Certificate> certOpt = certificateRepository.findByPgpCertisID(pgpCertisID);
        if (certOpt.isEmpty()) {
            System.err.println("pgpCertisID non trouvé dans Certificate : " + pgpCertisID);
            return Optional.empty();
        }

        // 4️⃣ Vérifier si ce certificat est aussi présent dans Request
        if (!requestRepository.existsByPgpCertisID(pgpCertisID)) {
            System.err.println("pgpCertisID non trouvé dans Request : " + pgpCertisID);
            return Optional.empty();
        }

        return certOpt;
    }

    /**
     * 🔎 Recherche `pgpCertisID` en LDAP à partir du `CN`
     */
    private String searchPgpCertisIDInLdap(String cn) {
        try {
            String formattedLdap = String.format("CN=%s, OU=Users, DC=example, DC=com", cn);

            LdapQuery query = LdapQueryBuilder.query()
                    .where("distinguishedName").is(formattedLdap)
                    .and("pgpCertisID").isPresent();

            List<String> results = ldapTemplate.search(query, (attributes, name) -> {
                return attributes.get("pgpCertisID") != null ? attributes.get("pgpCertisID").get().toString() : null;
            });

            return results.isEmpty() ? null : results.get(0);

        } catch (Exception e) {
            System.err.println("Erreur LDAP : " + e.getMessage());
            return null;
        }
    }

    /**
     * 🔍 Vérifie si `pgpCertisID.asc` existe dans le NAS
     */
    private boolean isPgpCertisIDInNas(String pgpCertisID) {
        Path filePath = findFileInNas(pgpCertisID + ".asc");
        return filePath != null;
    }

    /**
     * 🔍 Recherche un fichier spécifique dans le NAS
     */
    private Path findFileInNas(String filename) {
        try {
            final Path[] foundFile = {null};
            Files.walkFileTree(Paths.get(NAS_PATH), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equals(filename)) {
                        foundFile[0] = file;
                        return FileVisitResult.TERMINATE; // Stop dès qu'on trouve
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return foundFile[0];
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
///////////////////////////////////////////////////////
@RestController
@RequestMapping("/certificates")
public class CertificateController {

    @Autowired
    private NasLdapScannerService scannerService;

    @GetMapping("/validate/{cn}")
    public ResponseEntity<Certificate> validateCertificate(@PathVariable String cn) {
        return scannerService.findCertificateByCN(cn)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
