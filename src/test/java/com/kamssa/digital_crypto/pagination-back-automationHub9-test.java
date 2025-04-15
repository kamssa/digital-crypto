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