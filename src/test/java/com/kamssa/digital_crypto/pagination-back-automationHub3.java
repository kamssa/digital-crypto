1. Créer un fichier de
Vous pouvez créer un fichier nomméapplication.propertiesdans le répertoire `srcsrc/main/resources:

propriétés
Copie
# application.properties
page.size=10   # Taille des pages
2. Lire le fichier de
Pour lire le fichier de configuration, si vous utilisez Spring Boot, vous pouvez simplement utiliser l'annotation @Value.

Module
Assurez-vous que

xml
Copie
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
Service avec configuration
Voici comment modifier le

texte
Copie
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CertificateServiceImpl implements CertificateService {

    @Value("${page.size}")
    private int pageSize; // Taille du nombre de certificats par page

    private List<AutomationHubCertificateDto> database;

    // Constructeur pour ajouter des données fictives
    public CertificateServiceImpl() {
        database = new ArrayList<>();
        for (int i = 1; i <= 100; i++) { // Imaginons 100 certificats
            database.add(new AutomationHubCertificateDto("Certificate " + i));
        }
    }

    @Override
    public PaginationResponse<AutomationHubCertificateDto> searchForCertificates(int page) {
        int totalElements = database.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize); // Calcul du nombre total de pages
        int start = page * pageSize;
        int end = Math.min(start + pageSize, totalElements);

        if (start >= totalElements) {
            return new PaginationResponse<>(new ArrayList<>(), page, pageSize, totalElements, totalPages);
        }

        List<AutomationHubCertificateDto> pagedData = database.subList(start, end);
        return new PaginationResponse<>(pagedData, page, pageSize, totalElements, totalPages);
    }
    
    // DTO pour les certificats
    public static class AutomationHubCertificateDto {
        private String name;

        public AutomationHubCertificateDto(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // Classe pour encapsuler la réponse paginée
    public static class PaginationResponse<T> {
        private List<T> data;
        private int page;
        private int size;
        private int totalElements;
        private int totalPages;

        public PaginationResponse(List<T> data, int page, int size, int totalElements, int totalPages) {
            this.data = data;
            this.page = page;
            this.size = size;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
        }

        public List<T> getData() {
            return data;
        }

        public int getPage() {
            return page;
        }

        public int getSize() {
            return size;
        }

        public int getTotalElements() {
            return totalElements;
        }

        public int getTotalPages() {
            return totalPages;
        }

        @Override
        public String toString() {
            return "Page " + page + ": " + data + " (Total: " + totalElements + ", Total Pages: " + totalPages + ")";
        }
    }
}
3.
Lorsque vous initialisez le service, la taille de la page sera

Java
Copie
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(Main.class, args);
        CertificateService service = context.getBean(CertificateServiceImpl.class);

        // Exemple d'appel pour obtenir la première page
        PaginationResponse<CertificateServiceImpl.AutomationHubCertificateDto> response1 = service.searchForCertificates(0);
        System.out.println(response1);

        // Exemple d'appel pour obtenir la deuxième page
        PaginationResponse<CertificateServiceImpl.AutomationHubCertificateDto> response2 = service.searchForCertificates(1);
        System.out