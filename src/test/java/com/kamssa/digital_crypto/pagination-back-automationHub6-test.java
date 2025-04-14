private PaginationResponse<AutomationHubCertificateDto> searchCertificates(SearchCertificatesRequestDto request) {
    int pageSize = request.getPageSize() > 0 ? request.getPageSize() : defaultPageSize;
    int pageIndex = request.getPageIndex();
    
    int totalElements = database.size();
    int totalPages = (int) Math.ceil((double) totalElements / pageSize);
    
    int start = pageIndex * pageSize;
    int end = Math.min(start + pageSize, totalElements);
    
    List<AutomationHubCertificateDto> data = new ArrayList<>();

    if (start < totalElements) {
        data = database.subList(start, end);
    }

    if (request.isWithCount()) {
        return new PaginationResponse<>(data, pageIndex, pageSize, totalElements, totalPages);
    } else {
        return new PaginationResponse<>(data, pageIndex, pageSize, 0, 0);
    }
}
2. Gestion des Exceptions
Bon
Validation des paramètres : Vérifiez si pageIndex et pageSize sont valides.
Exceptions personnalisées : Créez des exceptions pour une gestion fine.
Exemple de Gestion des Exceptions
java
Copy
if (pageIndex < 0 || pageSize <= 0) {
    throw new InvalidPaginationParametersException("Page index and size must be positive.");
}
3. Optimisation de la Performance
Points à Considérer
Lazy Loading : Chargez seulement les données nécessaires.
Limitation de la taille de la base de données : Évitez de charger toute la liste en mémoire si elle est trop grande.
Indexation : Si vous utilisez une base de données, assurez-vous que vos colonnes sont indexées pour des recherches rapides.
Exemple d'Optimisation
Utilisez par exemple un appel à la base de données qui supporte la pagination (si applicable), pour éviter de charger toute la liste dans la mémoire.

4. Tests Unitaires et d'Intégration
Tests Unitaires
Vérifiez les différentes cases de pagination.
Testez les limites comme pageIndex supérieur à la taille totale.
Exemple de Test Unitaire
java
Copy
@Test
public void testSearchCertificates() {
    SearchCertificatesRequestDto request = new SearchCertificatesRequestDto(0, 10, true);
    
    PaginationResponse<AutomationHubCertificateDto> response = service.searchForCertificates(request);
    
    assertEquals(10, response.getData().size());
    assertEquals(0, response.getPageIndex());
    assertEquals(10, response.getPageSize());
    assertEquals(totalElements, response.getTotalElements());
}
Tests d'Intégration
Exécutez des tests pour vérifier l'intégration avec des bases de données ou d'autres services.
Assurez-vous que le service fonctionne comme prévu dans un environnement réel.
Conclusion
En appliquant ces bonnes pratiques, vous pouvez garantir que votre méthode de recherche de certificats est non seulement fonctionnelle, mais aussi robuste, performante et testable. Si vous avez besoin de détails supplémentaires sur un aspect particulier ou d'autres exemples de code, n'hésitez pas à demander !