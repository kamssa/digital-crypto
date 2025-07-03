public List<CmdbObjectDto> getByCNFromBusinessApp(String cn) {
    String sql = "SELECT * FROM servicenow.cmdb_all_classes " +
                 "WHERE sys_class_name = 'cmdb_ci_business_app' " +
                 "AND name ILIKE ? " +
                 "ORDER BY sys_id ASC LIMIT 100";

    return openDataJdbcTemplate.query(
        sql,
        new Object[]{"%" + cn + "%"},
        new CmdbObjectMapper()
    );
}
✅ 2. getByCNFromServerClasses(String cn)
java
Copier
Modifier
public List<CmdbObjectDto> getByCNFromServerClasses(String cn) {
    String sql = "SELECT * FROM servicenow.cmdb_all_classes " +
                 "WHERE sys_class_name IN (" +
                 "  'u_cmdb_ci_appliance_server'," +
                 "  'cmdb_ci_osx_server'," +
                 "  'cmdb_ci_esx_server'," +
                 "  'cmdb_ci_mainframe_lpar'," +
                 "  'cmdb_ci_linux_server'," +
                 "  'cmdb_ci_win_server'," +
                 "  'cmdb_ci_netware_server'," +
                 "  'cmdb_ci_server'," +
                 "  'cmdb_ci_aix_server'," +
                 "  'cmdb_ci_hpux_server'," +
                 "  'cmdb_ci_mainframe'," +
                 "  'cmdb_ci_solaris_server'," +
                 "  'cmdb_ci_unix_server'" +
                 ") AND name ILIKE ? " +
                 "ORDER BY sys_id ASC LIMIT 100";

    return openDataJdbcTemplate.query(
        sql,
        new Object[]{"%" + cn + "%"},
        new CmdbObjectMapper()
    );
}
✅ 3. getByCNFromBusinessAppChildren(String cn)
java
Copier
Modifier
public List<CmdbObjectDto> getByCNFromBusinessAppChildren(String cn) {
    String sql = "SELECT ba.* FROM servicenow.cmdb_all_classes ba " +
                 "LEFT JOIN servicenow.business_app_childs rel " +
                 "ON rel.ba_sys_id = ba.sys_id " +
                 "WHERE rel.child_sys_id ILIKE ? " +
                 "ORDER BY ba.sys_id ASC LIMIT 100";

    return openDataJdbcTemplate.query(
        sql,
        new Object[]{"%" + cn + "%"},
        new CmdbObjectMapper()
    );
}
🧪 Optionnel : méthode combinée
Si un jour tu veux tout fusionner, tu peux faire :

java
Copier
Modifier
public List<CmdbObjectDto> getAllByCN(String cn) {
    List<CmdbObjectDto> result = new ArrayList<>();
    result.addAll(getByCNFromBusinessApp(cn));
    result.addAll(getByCNFromServerClasses(cn));
    result.addAll(getByCNFromBusinessAppChildren(cn));
    return result;
}
////////////////////////////////////////////////////////
///////////////////////////////////////////////////////
 Nouvelle version avec filtre sur une date :
Si ton champ s'appelle valid.until (comme souvent dans les cas d’expiration de certificats), alors tu peux écrire :

properties
Copier
Modifier
ipki.query.autoEnrollCertExpiring=module in ["est","acme","acme-external","scep","webra"] \
and (owner in ["CIS-mTLS-EST-Enrollment", ...]) \
and valid.until >= "2025-07-01T00:00:00Z" \
and valid.until <= "2025-08-01T00:00:00Z"
📌 Si tu veux rendre ça paramétrable (Spring Boot style)
Dans application.properties ou application.yml :

properties
Copier
Modifier
ipki.query.autoEnrollCertExpiring=module in ["est","acme","acme-external","scep","webra"] \
and (owner in ["CIS-mTLS-EST-Enrollment", ...]) \
and valid.until >= "${cert.search.start}" \
and valid.until <= "${cert.search.end}"
Et dans ta classe Java :

java
Copier
Modifier
@Value("${cert.search.start}")
private String dateDebut;

@Value("${cert.search.end}")
private String dateFin;
🔁 Résumé
Ajoute simplement à la fin de ta requête :

text
Copier
Modifier
and valid.until >= "YYYY-MM-DDTHH:mm:ssZ"
and valid.until <= "YYYY-MM-DDTHH:mm:ssZ"
✅ Assure-toi d’utiliser le champ de date correct (valid.until, expiresAt, notAfter, etc.).

Souhaites-tu que je t’aide à écrire un service complet qui lit ces dates depuis un fichier .properties et applique dynamiquement la requête ?









Demander à ChatGPT
Vous avez dit :
peut mettre la requte sur une ligne 


ChatGPT a dit :
Oui ✅, tu peux parfaitement écrire la requête sur une seule ligne dans ton fichier .properties.

Voici comment transformer cette requête multi-lignes en version compacte sur une seule ligne :

✅ Version sur une ligne
properties
Copier
Modifier
ipki.query.autoEnrollCertExpiring=module in ["est","acme","acme-external","scep","webra"] and (owner in ["CIS-mTLS-EST-Enrollment", ...]) and valid.until >= "2025-07-01T00:00:00Z" and valid.until <= "2025-08-01T00:00:00Z"
🔁 Remplace les ... dans owner par les valeurs complètes si tu les as.

✅ Avec variables Spring Boot (optionnel)
Si tu veux que les dates soient dynamiques :

properties
Copier
Modifier
ipki.query.autoEnrollCertExpiring=module in ["est","acme","acme-external","scep","webra"] and (owner in ["CIS-mTLS-EST-Enrollment", ...]) and valid.until >= "${cert.search.start}" and valid.until <= "${cert.search.end}"
Et dans ton code :

java
Copier
Modifier
@Value("${cert.search.start}")
private String startDate;

@Value("${cert.search.end}")
private String endDate;
Souhaites-tu aussi que la requête soit encodée (ex: pour un appel HTTP GET ou JSON) ?

///////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class CertificateService {

    @Value("${ipki.query.autoEnrollQueryCert}")
    private String autoEnrollQuery;

    @Value("${certificate.validity.start.date}")
    private String startDateString;

    @Value("${certificate.validity.end.date}")
    private String endDateString;

    public List<AutomationHubCertificateLightDto> searchAutoEnrollExpiring() {
        SearchCertificateRequestDto searchCertificateRequestDto = null;

        if (autoEnrollQuery != null && !autoEnrollQuery.isEmpty()) {
            String rawQuery = null;
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

            // Parse start and end dates from properties
            LocalDate startDate = LocalDate.parse(startDateString);
            LocalDate endDate = LocalDate.parse(endDateString);

            // Format the dates as strings
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String startDateFormatted = startDate.format(dateFormatter);
            String endDateFormatted = endDate.format(dateFormatter);

            rawQuery = autoEnrollQuery;

            // Add date criteria to the rawQuery
            rawQuery += " AND VALIDITY_DATE >= '" + startDateFormatted + "'";
            rawQuery += " AND VALIDITY_DATE <= '" + endDateFormatted + "'";

            System.out.println("Voir la autoEnrollQueryCart: " + autoEnrollQuery);
            System.out.println("Voir la query: " + rawQuery);

            searchCertificateRequestDto = new SearchCertificateRequestDto(rawQuery);
        } else {
            List<ISearchCriterion> criterionList = new ArrayList<>();
            criterionList.add(new SearchTextCriterion(SearchTextFieldEnum.MODULE, Arrays.asList(
                    ModuleEnum.DISCOVERY.getValue(), ModuleEnum.CRMP.getValue(), ModuleEnum.INTUNE.getValue(),
                    ModuleEnum.INTUNEKPKS.getValue(), ModuleEnum.JAMF.getValue(), ModuleEnum.WCCE.getValue(), ModuleEnum.WEBRA.getValue()
            ), SearchCriterionTextOperatorEnum.NOT_IN));
            criterionList.add(new SearchTextCriterion(SearchTextFieldEnum.STATUS, CertificateStatusEnum.VALID.getValue(), SearchCriterionTextOperatorEnum.EQUAL));

            searchCertificateRequestDto = new SearchCertificateRequestDto(criterionList);
        }

        return this.searchCertificates(searchCertificateRequestDto);
    }
}
////////////////////////////////////////////////////////////////
@Component
public class CertificateService {

    @Value("${ipki.query.autoEnrollCertExpiring}")
    private String autoEnrollQuery;

    @Value("${certificate.validity.start.date}")
    private String startDateString;

    @Value("${certificate.validity.end.date}")
    private String endDateString;

    public List<AutomationHubCertificateLightDto> searchAutoEnrollExpiring() {
        SearchCertificateRequestDto searchCertificateRequestDto = null;

        if (autoEnrollQuery != null && !autoEnrollQuery.isEmpty()) {
            // Parse les dates depuis application.properties
            LocalDate startDate = LocalDate.parse(startDateString);
            LocalDate endDate = LocalDate.parse(endDateString);

            // Formate les dates au format ISO 8601 (requis dans la requête)
            DateTimeFormatter isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00'Z'");
            String startFormatted = startDate.format(isoFormatter);
            String endFormatted = endDate.format(isoFormatter);

            // Construit la requête finale
            String rawQuery = autoEnrollQuery +
                    " and valid.until >= \"" + startFormatted + "\"" +
                    " and valid.until <= \"" + endFormatted + "\"";

            System.out.println("🟦 Requête générée : " + rawQuery);

            searchCertificateRequestDto = new SearchCertificateRequestDto(rawQuery);

        } else {
            // Fallback avec critères manuels
            List<ISearchCriterion> criterionList = new ArrayList<>();
            criterionList.add(new SearchTextCriterion(
                    SearchTextFieldEnum.MODULE,
                    Arrays.asList("discovery", "crmp", "intune", "intunepkcs", "jamf", "wcce", "webra"),
                    SearchCriterionTextOperatorEnum.NOT_IN
            ));
            criterionList.add(new SearchTextCriterion(
                    SearchTextFieldEnum.STATUS,
                    CertificateStatusEnum.VALID.getValue(),
                    SearchCriterionTextOperatorEnum.EQUAL
            ));

            searchCertificateRequestDto = new SearchCertificateRequestDto(criterionList);
        }

        return this.searchCertificates(searchCertificateRequestDto);
    }
}











