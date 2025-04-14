public class SearchCertificatesRequestDto {
    private int pageIndex;
    private int pageSize;

    // Constructor
    public SearchCertificatesRequestDto(int pageIndex, int pageSize) {
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
    }

    // Getters & Setters
    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
2. Mettre à jour l'Interface du Service
Mettez à jour

Java
Copie
import java.util.List;

public interface CertificateService {
    PaginationResponse<AutomationHubCertificateDto> searchForCertificates(SearchCertificatesRequestDto requestDto);
}
3. Mise en œuvre
Implémentez la méthode dans votre service :

texte
Copie
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CertificateServiceImpl implements CertificateService {

    @Value("${page.size:50}") // Valeur par défaut de 50
    private int defaultPageSize;

    private List<AutomationHubCertificateDto> database;

    // Constructeur pour ajouter des données fictives
    public CertificateServiceImpl() {
        database = new ArrayList<>();
        for (int i = 1; i <= 200; i++) { // Imaginons 200 certificats
            database.add(new AutomationHubCertificateDto("Certificate " + i));
        }
    }

    @Override
    public PaginationResponse<AutomationHubCertificateDto> searchForCertificates(SearchCertificatesRequestDto requestDto) {
        int pageSize = requestDto.getPageSize() > 0 ? requestDto.getPageSize() : defaultPageSize;
        int pageIndex = requestDto.getPageIndex();

        int totalElements = database.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        int start = pageIndex * pageSize;
        int end = Math.min(start + pageSize, totalElements);

        if (start >= totalElements) {
            return new PaginationResponse<>(new ArrayList<>(), pageIndex, pageSize, totalElements, totalPages);
        }

        List<AutomationHubCertificateDto> pagedData = database.subList(start, end);
        return new PaginationResponse<>(pagedData, pageIndex, pageSize, totalElements, totalPages);
    }

    // Autres classes (DTO et PaginationResponse) comme précédemment...
}
4. Utilisation du service
Voici comment appeler cette méthode dans votre classe principale :

texte
Copie
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(Main.class, args);
        CertificateService service = context.getBean(CertificateServiceImpl.class);

        // Boucle pour récupérer chaque page jusqu'à atteindre le total de pages
        int n = 50; // Limite de la taille de page
        for (int pageIndex = 0; pageIndex < 4; pageIndex++) { // Par exemple, pour 4 pages
            SearchCertificatesRequestDto requestDto = new SearchCertificatesRequestDto(pageIndex, n);
            PaginationResponse<CertificateServiceImpl.AutomationHubCertificateDto> response = service.searchForCertificates(requestDto);
            System.out.println(response);
        }
    }
}
Explication
DTO de Requête de Recherche : 

SearchCertificatesRequestDtoencapsulerpageIndexetpageSize.
Service de pagination :

La méthode searchForCertificatesprend en comptepageSizedupageSizeHNE
Boucle d'Appel : 

Dans la méthode main,pageIndexjusqu'à