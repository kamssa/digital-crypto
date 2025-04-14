import java.util.ArrayList;
import java.util.List;

public class CertificateService {

    private List<AutomationHubCertificateDto> database;

    // Constructeur pour ajouter des données fictives
    public CertificateService() {
        database = new ArrayList<>();
        for (int i = 1; i <= 100; i++) { // Exemple : ajout de 100 certificats
            database.add(new AutomationHubCertificateDto("Certificate " + i));
        }
    }

    public PaginationResponse<AutomationHubCertificateDto> searchForCertificates(int page, int size) {
        // Calcul des indices pour la pagination
        int start = page * size;
        int end = Math.min(start + size, database.size());

        // Si départ est au-delà de la taille de la liste, retourner une liste vide
        if (start >= database.size()) {
            return new PaginationResponse<>(new ArrayList<>(), page, size, database.size());
        }

        List<AutomationHubCertificateDto> pagedData = database.subList(start, end);
        return new PaginationResponse<>(pagedData, page, size, database.size());
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

        public PaginationResponse(List<T> data, int page, int size, int totalElements) {
            this.data = data;
            this.page = page;
            this.size = size;
            this.totalElements = totalElements;
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

        @Override
        public String toString() {
            return "Page " + page + ": " + data + " (Total: " + totalElements + ")";
        }
    }

    public static void main(String[] args) {
        CertificateService service = new CertificateService();

        // Exemple pour obtenir la première page de certificats
        PaginationResponse<AutomationHubCertificateDto> response1 = service.searchForCertificates(0, 10);
        System.out.println(response1);

        // Exemple pour obtenir la deuxième page de certificats
        PaginationResponse<AutomationHubCertificateDto> response2 = service.searchForCertificates(1, 10);
        System.out.println(response2);
    }
}