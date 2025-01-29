public List<AutomationHubCertification> searchDiscovery() {
    SearchCertificateRequestDto searchCertificateRequestDto = null;
    Date dataAfter3Months = DateUtils.addMonths(new Date(), 3);

    if (discoveryQuery.isPresent()) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String dataAsString = format.format(dataAfter3Months);
        String rawQuery = discoveryQuery.get().replace("dataM+3", dataAsString);
        searchCertificateRequestDto = new SearchCertificateRequestDto(rawQuery, 1, 999999);
    } else {
        List<SearchCriterion> criterionList = new ArrayList<>();

        criterionList.add(new SearchTextCriterion(SearchTextFieldEnum.MODULE, ModuleEnum.DISCOVERY.getValue(), SearchCriterionTextOperatorEnum.EQ));
        criterionList.add(new SearchTextCriterion(SearchTextFieldEnum.STATUS, CertificatestatusEnum.VALID.getValue(), SearchCriterionTextOperatorEnum.EQ));
        criterionList.add(new SearchTextCriterion(SearchTextFieldEnum.TRUST_STATUS, TrustStatusEnum.TRUSTED.getValue(), SearchCriterionTextOperatorEnum.EQ));
        criterionList.add(new SearchDateCriterion(SearchDateFieldEnum.EKPLAY_DATE, dataAfter3Months, SearchCriterionDateOperatorEnum.BEFORE));

        searchCertificateRequestDto = new SearchCertificateRequestDto(criterionList, 1, 999999);
    }

    return this.searchCertificates(searchCertificateRequestDto);
}