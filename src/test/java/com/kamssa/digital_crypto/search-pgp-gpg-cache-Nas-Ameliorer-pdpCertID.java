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
                    .where("pgUserID").is(formattedLdap)
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
////////////////////////////////////////////////////////////////////
@Query("SELECT c FROM Certificate c WHERE c.pgpCertisID = :pgpCertisID ORDER BY c.id DESC")
Optional<Certificate> findLatestByPgpCertisID(@Param("pgpCertisID") String pgpCertisID);
Puis l’appel :

java
Copier
Modifier
Optional<Certificate> certOpt = certificateRepository.findLatestByPgpCertisID(pgpCertisID);
3️⃣ Avec une Query Native SQL (si nécessaire)
Si l'ID ne suit pas un ordre strict ou si tu veux filtrer sur une date, une requête SQL directe peut être utilisée :

java
Copier
Modifier
@Query(value = "SELECT * FROM certificate WHERE pgp_certis_id = :pgpCertisID ORDER BY id DESC LIMIT 1", nativeQuery = true)
Optional<Certificate> findLatestByPgpCertisID(@Param("pgpCertisID") String pgpCertisID);
////// option1 sans DTO
public class KeyAttributesMapper implements AttributesMapper<Map<String, String>> {

    @Override
    public Map<String, String> mapFromAttributes(Attributes attrs) throws NamingException {
        Map<String, String> result = new HashMap<>();
        result.put("pgpUserID", attrs.get("pgpUserID") != null ? attrs.get("pgpUserID").get().toString() : null);
        result.put("pgpCertisID", attrs.get("pgpCertisID") != null ? attrs.get("pgpCertisID").get().toString() : null);
        return result;
    }
}
public Map<String, String> findLdapCertInfoMapByCN(String cn) {
    try {
        String dn = String.format("CN=%s,OU=Users,DC=example,DC=com", cn);
        LdapQuery query = LdapQueryBuilder.query()
                .where("pgpUserID").is(dn)
                .and("pgpCertisID").isPresent();

        List<Map<String, String>> results = ldapTemplate.search(query, new KeyAttributesMapper());

        return results.isEmpty() ? null : results.get(0);

    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
}
Map<String, String> result = ldapNasService.findLdapCertInfoMapByCN("john.doe");
if (result != null) {
    System.out.println("pgpUserID = " + result.get("pgpUserID"));
    System.out.println("pgpCertisID = " + result.get("pgpCertisID"));
}