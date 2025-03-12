/* Ce service :
1️⃣ Cherche les fichiers .asc dans le NAS.
2️⃣ Extrait l'identifiant et vérifie en base (Certificate).
3️⃣ Vérifie si le certificat est utilisé dans Request.
4️⃣ Interroge LDAP pour récupérer la clé PGP/GPG associée. */
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
     * Recherche un fichier .asc dans le NAS, vérifie si son identifiant correspond à un certificat en base,
     * s'il est utilisé dans Request et s'il existe une clé PGP/GPG associée en LDAP.
     */
    @Cacheable(value = "ldapKeysCache", key = "#ascFilename")
    public Optional<Certificate> findCertificateWithLdapKey(String ascFilename) {
        if (!ascFilename.endsWith(".asc")) {
            System.err.println("L'identifiant recherché doit être un fichier .asc !");
            return Optional.empty();
        }

        Path filePath = findFileInNas(ascFilename);
        if (filePath == null) {
            System.err.println("Fichier non trouvé : " + ascFilename);
            return Optional.empty();
        }

        // Extraire l'identifiant (ex: clé_pgp123.asc → clé_pgp123)
        String identifier = ascFilename.replace(".asc", "");

        // Vérifier si l'identifiant existe dans Certificate
        Optional<Certificate> certOpt = certificateRepository.findByIdentifier(identifier);
        if (certOpt.isEmpty()) {
            System.err.println("Aucun certificat en DB pour : " + identifier);
            return Optional.empty();
        }

        // Vérifier si le certificat est lié à une Request
        if (requestRepository.findByCertificateIdentifier(identifier).isEmpty()) {
            System.err.println("Certificat trouvé mais non utilisé dans Request : " + identifier);
            return Optional.empty();
        }

        // Vérifier si une clé PGP/GPG existe en LDAP pour cet identifiant
        List<String> keys = searchKeysInLdap(identifier);
        if (keys.isEmpty()) {
            System.err.println("Aucune clé trouvée en LDAP pour : " + identifier);
        } else {
            System.out.println("Clé LDAP trouvée pour : " + identifier + " -> " + keys);
        }

        return certOpt;
    }

    /**
     * Parcours le NAS pour trouver un fichier spécifique
     */
    private Path findFileInNas(String filename) {
        try {
            final Path[] foundFile = {null};
            Files.walkFileTree(Paths.get(NAS_PATH), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equals(filename)) {
                        foundFile[0] = file;
                        return FileVisitResult.TERMINATE; // Arrêter la recherche dès qu'on trouve
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

    /**
     * Recherche les clés PGP/GPG en LDAP pour un identifiant donné
     */
    private List<String> searchKeysInLdap(String identifier) {
        try {
            LdapQuery query = LdapQueryBuilder.query()
                    .where("mail").is(identifier)
                    .or("uid").is(identifier)
                    .and(
                        LdapQueryBuilder.query().where("pgpKey").isPresent()
                        .or("gpgKey").isPresent()
                    );

            return ldapTemplate.search(query, (attributes, name) -> {
                String pgpKey = attributes.get("pgpKey") != null ? attributes.get("pgpKey").get().toString() : null;
                String gpgKey = attributes.get("gpgKey") != null ? attributes.get("gpgKey").get().toString() : null;
                return pgpKey != null ? pgpKey : gpgKey; // Priorité à PGP si les deux existent
            });

        } catch (Exception e) {
            System.err.println("Erreur lors de la requête LDAP : " + e.getMessage());
        }

        return Collections.emptyList();
    }
}
