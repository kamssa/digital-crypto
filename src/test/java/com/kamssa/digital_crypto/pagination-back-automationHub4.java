import java.util.List;

public interface CertificateService {
    PaginationResponse<AutomationHubCertificateDto> searchForCertificates(int pageIndex, int pageSize, boolean withCount);
}
2. Mise en œuvre du service
Voici comment implémenter cela dans votre service :

Java
Copie
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CertificateServiceImpl implements CertificateService {

    @Value("${page.size}")
    private int defaultPageSize; // Valeur par défaut si non spécifiée

    private List<AutomationHubCertificateDto> database;

    // Constructeur pour ajouter des données fictives
    public CertificateServiceImpl() {
        database = new ArrayList<>();
        for (int i = 1; i <= 100; i++) { // Imaginons 100 certificats
            database.add(new AutomationHubCertificateDto("Certificate " + i));
        }
    }

    @Override
    public PaginationResponse<AutomationHubCertificateDto> searchForCertificates(int pageIndex, int pageSize, boolean withCount) {
        if (withCount) {
            int totalElements = database.size();
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);
            int start = pageIndex * pageSize;
            int end = Math.min(start + pageSize, totalElements);

            if (start >= totalElements) {
                return new PaginationResponse<>(new ArrayList<>(), pageIndex, pageSize, totalElements, totalPages);
            }

            List<AutomationHubCertificateDto> pagedData = database.subList(start, end);
            return new PaginationResponse<>(pagedData, pageIndex, pageSize, totalElements, totalPages);
        } else {
            int start = pageIndex * pageSize;
            int end = Math.min(start + pageSize, database.size());

            if (start >= database.size()) {
                return new PaginationResponse<>(new ArrayList<>(), pageIndex, pageSize, 0, 0);
            }

            List<AutomationHubCertificateDto> pagedData = database.subList(start, end);
            return new PaginationResponse<>(pagedData, pageIndex, pageSize, 0, 0); // Pas de total si withCount = false
        }
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
        private int pageIndex;
        private int pageSize;
        private int totalElements;
        private int totalPages;

        public PaginationResponse(List<T> data, int pageIndex, int pageSize, int totalElements, int totalPages) {
            this.data = data;
            this.pageIndex = pageIndex;
            this.pageSize = pageSize;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
        }

        public List<T> getData() {
            return data;
        }

        public int getPageIndex() {
            return pageIndex;
        }

        public int getPageSize() {
            return pageSize;
        }

        public int getTotalElements() {
            return totalElements;
        }

        public int getTotalPages() {
            return totalPages;
        }

        @Override
        public String toString() {
            return "Page " + pageIndex + ": " + data + " (Total: " + totalElements + ", Total Pages: " + totalPages + ")";
        }
    }
}
3. Utilisation du service
Voici un exemple

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
        PaginationResponse<CertificateServiceImpl.AutomationHubCertificateDto> response1 = service.searchForCertificates(1, 100, true);
        System.out.println(response1);

        // Exemple d'appel pour obtenir la
Photocopieuse
Régénérer

