Java
Copie
import java.util.List;

public interface CertificateService {
    PaginationResponse<AutomationHubCertificateDto> searchForCertificates(int page, int size);
}
2. Mise en œuvre du service
Java
Copie
import java.util.ArrayList;
import java.util.List;

public class CertificateServiceImpl implements CertificateService {

    private List<AutomationHubCertificateDto> database;

    // Constructeur pour ajouter des données fictives
    public CertificateServiceImpl() {
        database = new ArrayList<>();
        for (int i = 1; i <= 100; i++) { // Imaginons 100 certificats
            database.add(new AutomationHubCertificateDto("Certificate " + i));
        }
    }

    @Override
    public PaginationResponse<AutomationHubCertificateDto> searchForCertificates(int page, int size) {
        int totalElements = database.size();
        int totalPages = (int) Math.ceil((double) totalElements / size); // Calcul du nombre total de pages
        int start = page * size;
        int end = Math.min(start + size, totalElements);
        
        if (start >= totalElements) {
            return new PaginationResponse<>(new ArrayList<>(), page, size, totalElements, totalPages);
        }

        List<AutomationHubCertificateDto> pagedData = database.subList(start, end);
        return new PaginationResponse<>(pagedData, page, size, totalElements, totalPages);
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
3. Util
Voici comment utiliser le

Java
Copie
public class Main {
    public static void main(String[] args) {
        CertificateService service = new CertificateServiceImpl();

        // Exemple d'appel pour obtenir la première page
        PaginationResponse<CertificateServiceImpl.AutomationHubCertificateDto> response1 = service.searchForCertificates(0, 10);
        System.out.println(response1);

        // Exemple d'appel pour obtenir la deuxième page
        PaginationResponse<CertificateServiceImpl.AutomationHubCertificateDto> response2 = service.searchForCertificates(1, 10);
        System.out.println(response2);
        
        // Exemple d'appel pour obtenir la dernière page
        PaginationResponse<CertificateServiceImpl.AutomationHubCertificateDto> responseLast = service.searchForCertificates(9, 10);
        System.out.println(responseLast);
    }
}
Explication
Calcul du total de pages : 

Dans la méthode searchForCertificates,int totalPages = (int) Math.ceil((double) totalElements / size);.
Mise à jour de PaginationResponse:

La classe PaginationResponseinclut maintenant untotalPages.
Utilisation: 

Exemples d'appels dans la classe Mainpour
Rés
Lorsque vous exécutez le code, vous obtiendrez un résultat incluant le nombre total d'éléments et le nombre total de pages, vous permettant ainsi de savoir combien de pages sont disponibles pour la pagination.

