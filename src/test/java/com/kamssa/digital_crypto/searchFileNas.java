import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class AscFileScanner {
    public static void main(String[] args) throws IOException {
        String directoryPath = "/mnt/mon_nas";  // Modifier selon ton montage NAS
        String searchTerm = "identifiant"; // Modifier selon l’identifiant recherché

        Files.walkFileTree(Paths.get(directoryPath), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".asc") && searchInFile(file.toFile(), searchTerm)) {
                    System.out.println("Identifiant trouvé dans : " + file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean searchInFile(File file, String searchTerm) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains(searchTerm)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Impossible de lire : " + file.getAbsolutePath());
        }
        return false;
    }
}
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

@Service
public class NasFileScannerService {

    private static final String NAS_PATH = "/mnt/mon_nas"; // Adapter au chemin réel du NAS

    public List<String> searchIdentifierInAscFiles(String identifier) {
        List<String> foundFiles = new ArrayList<>();
        
        try {
            Files.walkFileTree(Paths.get(NAS_PATH), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".asc") && containsIdentifier(file.toFile(), identifier)) {
                        foundFiles.add(file.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return foundFiles;
    }

    private boolean containsIdentifier(File file, String identifier) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains(identifier)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Impossible de lire : " + file.getAbsolutePath());
        }
        return false;
    }
}
////////////////////////////////////////////////////////
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class NasFileScannerService {

    private static final String NAS_PATH = "/mnt/mon_nas"; // Modifier selon ton montage NAS
    private final CertificateRepository certificateRepository;

    public NasFileScannerService(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    public List<String> searchIdentifierInAscFiles(String identifier) {
        List<String> foundFiles = new ArrayList<>();

        try {
            Files.walkFileTree(Paths.get(NAS_PATH), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".asc") && containsIdentifier(file.toFile(), identifier)) {
                        if (isCertificateInDatabase(identifier)) {
                            foundFiles.add(file.toString() + " ✅ (Certificat trouvé en DB)");
                        } else {
                            foundFiles.add(file.toString() + " ❌ (Aucun certificat en DB)");
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return foundFiles;
    }

    private boolean containsIdentifier(File file, String identifier) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains(identifier)) {
                    return true;
                }
            }
        } catch (IOException e) {
            System.err.println("Impossible de lire : " + file.getAbsolutePath());
        }
        return false;
    }

    private boolean isCertificateInDatabase(String identifier) {
        Optional<Certificate> cert = certificateRepository.findByIdentifier(identifier);
        return cert.isPresent();
    }
}
//////////////////////////////////////////////////////////
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/nas")
public class NasController {

    private final NasFileScannerService scannerService;

    public NasController(NasFileScannerService scannerService) {
        this.scannerService = scannerService;
    }

    @GetMapping("/search")
    public List<String> searchIdentifier(@RequestParam String identifier) {
        return scannerService.searchIdentifierInAscFiles(identifier);
    }
}
curl "http://localhost:8080/nas/search?identifier=clé_pgp123.asc"
Rechercher l'identifiant dans la base (Certificate)
Vérifier s’il est aussi présent dans Request
Si oui, récupérer l'entité Certificate
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Service
public class NasFileScannerService {

    private static final String NAS_PATH = "/mnt/mon_nas"; // Modifier selon ton montage NAS
    private final CertificateRepository certificateRepository;
    private final RequestRepository requestRepository;

    public NasFileScannerService(CertificateRepository certificateRepository, RequestRepository requestRepository) {
        this.certificateRepository = certificateRepository;
        this.requestRepository = requestRepository;
    }

    public List<String> searchIdentifierInAscFiles(String identifier) {
        List<String> foundFiles = new ArrayList<>();

        try {
            Files.walkFileTree(Paths.get(NAS_PATH), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".asc") && containsIdentifier(file.toFile(), identifier)) {
                        Optional<Certificate> certOpt = certificateRepository.findByIdentifier(identifier);
                        
                        if (certOpt.isPresent()) {
                            Certificate cert = certOpt.get();
                            if (requestRepository.findByCertificateIdentifier(cert.getIdentifier()).isPresent()) {
                                foundFiles.add(file.toString() + " ✅ (Certificat en DB et utilisé dans Request)");
                            } else {
                                foundFiles.add(file.toString() + " ⚠️ (Certificat en DB mais pas dans Request)");
                            }
                        } else {
                            foundFiles.add(file.toString() + " ❌ (Aucun certificat en DB)");
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return foundFiles;
    }
}

import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/nas")
public class NasController {

    private final CertificateRepository certificateRepository;
    private final RequestRepository requestRepository;

    public NasController(CertificateRepository certificateRepository, RequestRepository requestRepository) {
        this.certificateRepository = certificateRepository;
        this.requestRepository = requestRepository;
    }

    @GetMapping("/certificate")
    public Optional<Certificate> getCertificateIfInRequest(@RequestParam String identifier) {
        Optional<Certificate> certOpt = certificateRepository.findByIdentifier(identifier);
        
        if (certOpt.isPresent() && requestRepository.findByCertificateIdentifier(identifier).isPresent()) {
            return certOpt;
        }

        return Optional.empty(); // Retourne vide si pas trouvé
    }
}
