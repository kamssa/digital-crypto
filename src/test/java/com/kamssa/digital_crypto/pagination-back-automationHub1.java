import java.util.ArrayList;
import java.util.List;

public class CertificateService {

    // Simulons une base de données de certificats
    private List<AutomationHubCertificateDto> database = new ArrayList<>();

    // Constructeur pour ajouter des données fictives
    public CertificateService() {
        for (int i = 1; i <= 100; i++) {
            database.add(new AutomationHubCertificateDto("Certificate " + i));
        }
    }

    public List<AutomationHubCertificateDto> searchForCertificates(int page, int size) {
        int start = page * size;
        int end = Math.min(start + size, database.size());

        if (start >= database.size()) {
            return new ArrayList<>(); // Retourne une liste vide si la page est au-delà de la taille
        }

        return database.subList(start, end); // Retourne la sous-liste correspondant à la page demandée
    }
    
    // Classe interne pour démontrer le DTO
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

    public static void main(String[] args) {
        CertificateService service = new CertificateService();

        // Exemple d'appel pour obtenir la première page avec 10 certificats
        List<AutomationHubCertificateDto> certificatesPage1 = service.searchForCertificates(0, 10);
        System.out.println("Page 1: " + certificatesPage1);

        // Exemple d'appel pour obtenir la deuxième page avec 10 certificats
        List<AutomationHubCertificateDto> certificatesPage2 = service.searchForCertificates(1, 10);
        System.out.println("Page 2: " + certificatesPage2);
    }
}
Explication
Service de certificats : La classe CertificateServicecontient une liste de certificats fictifs.

Méthode searchForCertificates: Prend deux paramètres : page(numéro de la page) et size(nombre maximum d'éléments par page). Elle calcule la portion de la liste de certificats à retourner.

Exemples d'appels : Dans la méthode main, des appels sont effectués pour obtenir les certificats des deux premières pages.

Résultat attendu
Pour un appel à searchForCertificates(0, 10), vous obtiendrez les certificats "Certificate 1" à "Certificate 10". Pour searchForCertificates(1, 10), les certificats "Certificate 11" à "Certificate 20" seront retournés.

N'hésitez pas à adapter cet exemple en fonction de votre implémentation et de vos besoins spécifiques !

je veux un retour paginer

avatar
Pour implémenter un retour paginé lorsque vous récupérez des données en Java, vous pouvez créer une méthode qui gère la pagination. Voici un exemple plus détaillé pour obtenir une liste paginée de List<AutomationHubCertificateDto>:

Exemple de pagination
Java
Copie
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
Explication
Classe CertificateService: Contient la liste de certificats et la méthode pour récupérer des données paginées.

Méthode searchForCertificates: 

Prend pageet sizecomme paramètres.
Calculez les indices de début et de fin pour la sous-liste.
Retourne un objet PaginationResponsecontenant les certificats de la page demandée.
Classe PaginationResponse: Enveloppe la liste des données retournées

Méthode main: Montre

Résultat
En appelant searchForCertificates(0,