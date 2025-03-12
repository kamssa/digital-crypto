import org.springframework.stereotype.Service;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

@Service
public class NasFileScannerService {

    private static final String NAS_PATH = "/mnt/mon_nas"; // Modifier selon ton NAS
    private final CertificateRepository certificateRepository;
    private final LdapTemplate ldapTemplate;

    @Autowired
    public NasFileScannerService(CertificateRepository certificateRepository, LdapTemplate ldapTemplate) {
        this.certificateRepository = certificateRepository;
        this.ldapTemplate = ldapTemplate;
    }

    /**
     * Parcours tous les fichiers du NAS, recherche ceux avec l'extension .asc,
     * formate l'identifiant pour le LDAP et vérifie s'il existe dans LDAP ou dans la base de données.
     */
    public void checkCertificatesInNas() {
        try {
            // Parcours récursif de tous les fichiers du NAS
            Files.walkFileTree(Paths.get(NAS_PATH), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Vérifier si le fichier a l'extension .asc
                    if (file.getFileName().toString().endsWith(".asc")) {
                        // Extraire l'identifiant du certificat (sans l'extension .asc)
                        String identifier = file.getFileName().toString().replace(".asc", "");

                        // Formater l'identifiant au format LDAP (par exemple : "CN=identifier, OU=Users, DC=example, DC=com")
                        String formattedLdap = String.format("CN=%s, OU=Users, DC=example, DC=com", identifier);

                        // Vérifier si l'identifiant formaté existe dans le LDAP
                        if (existsInLdap(formattedLdap)) {
                            System.out.println("Certificat trouvé dans LDAP pour : " + formattedLdap);
                        } else {
                            System.out.println("Aucun certificat trouvé dans LDAP pour : " + formattedLdap);
                        }

                        // Vérifier si l'identifiant existe dans la base de données des certificats
                        Optional<Certificate> certOpt = certificateRepository.findByIdentifier(identifier);
                        if (certOpt.isPresent()) {
                            System.out.println("Certificat trouvé dans la base pour : " + identifier);
                        } else {
                            System.out.println("Aucun certificat en DB pour : " + identifier);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Vérifier si un identifiant formaté existe dans le LDAP.
     */
    private boolean existsInLdap(String formattedLdap) {
        try {
            // Utilisation de LdapTemplate pour vérifier si l'élément existe dans LDAP
            // Supposons que tu utilises un LdapQuery pour faire la recherche
            LdapQuery query = LdapQueryBuilder.query().base(formattedLdap);
            return !ldapTemplate.search(query, (attributes, name) -> true).isEmpty();
        } catch (Exception e) {
            System.err.println("Erreur lors de la recherche dans LDAP : " + e.getMessage());
            return false;
        }
    }
}
