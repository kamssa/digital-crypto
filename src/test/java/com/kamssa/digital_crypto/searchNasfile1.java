import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

@Service
public class NasFileScannerService {

    private static final String NAS_PATH = "/mnt/mon_nas"; // Modifier selon ton NAS
    private final CertificateRepository certificateRepository;
    private final RequestRepository requestRepository;

    public NasFileScannerService(CertificateRepository certificateRepository, RequestRepository requestRepository) {
        this.certificateRepository = certificateRepository;
        this.requestRepository = requestRepository;
    }

    public Optional<Certificate> findCertificateIfUsedInRequest(String ascFilename) {
        if (!ascFilename.endsWith(".asc")) {
            System.err.println("L'identifiant recherché doit être un fichier .asc !");
            return Optional.empty();
        }

        Path filePath = findFileInNas(ascFilename);
        if (filePath == null) {
            System.err.println("Fichier non trouvé : " + ascFilename);
            return Optional.empty();
        }

        // Extraire l'identifiant du nom de fichier (ex: clé_pgp123.asc -> clé_pgp123)
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

        // Retourner le certificat
        return certOpt;
    }

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
}
