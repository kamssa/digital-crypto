@Override
public List<AutomationHubCertificateDto> searchNotCompliantsTest() {
    int page = 1;
    int size = 2;

    if (!nonCompliantDiscoveryQuery.isPresent()) {
        throw new NotImplementedException("You must set the query from config because OR filters are not implemented yet");
    }

    List<AutomationHubCertificateDto> allResults = new ArrayList<>();
    boolean hasMore;

    do {
        SearchCertificateRequestDto requestDto = buildRequestDto(page, size);
        List<AutomationHubCertificateDto> pageResults = searchCertificates(requestDto);

        allResults.addAll(pageResults);
        hasMore = pageResults.size() == size;
        page++;

    } while (hasMore);

    return allResults;
}

private SearchCertificateRequestDto buildRequestDto(int page, int size) {
    // À terme tu peux enrichir ici selon le cas (module, status, OR logic…)
    return new SearchCertificateRequestDto(nonCompliantDiscoveryQuery.get(), page, size);
}
//////////////////////////////////
@Override
public List<AutomationHubCertificateDto> searchNotCompliantsTest() {
    int page = 1;
    int size = 1000;
    int maxElements = 100_000;

    if (!nonCompliantDiscoveryQuery.isPresent()) {
        throw new NotImplementedException("Discovery query not configured.");
    }

    List<AutomationHubCertificateDto> allResults = new ArrayList<>();
    boolean hasMore;

    do {
        SearchCertificateRequestDto requestDto = buildRequestDto(page, size);
        List<AutomationHubCertificateDto> pageResults = searchCertificates(requestDto);

        allResults.addAll(pageResults);
        hasMore = pageResults.size() == size && allResults.size() < maxElements;
        page++;

        System.out.println("Page " + (page - 1) + " fetched, total so far: " + allResults.size());

    } while (hasMore);

    return allResults;
}

private SearchCertificateRequestDto buildRequestDto(int page, int size) {
    return new SearchCertificateRequestDto(nonCompliantDiscoveryQuery.get(), page, size);
}
🧠 Ce que ça fait :
Récupère les certificats par paquets de 1000

Continue tant que le résultat contient 1000 éléments et que tu n’as pas dépassé les 100 000

S'arrête automatiquement à la fin des résultats ou à 100k

🚀 Et si tu veux rendre ça encore plus propre/configurable :
Tu peux externaliser size et maxElements dans un application.properties

Ou les passer en paramètres de méthode