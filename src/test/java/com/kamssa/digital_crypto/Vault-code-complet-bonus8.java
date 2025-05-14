
🗂️ Arborescence du projet
swift
Copier
Modifier
src/main/java/com/example/securevault/
├── SecureVaultApplication.java
├── config/
│   └── VaultProperties.java
├── model/
│   └── Credentials.java
├── service/
│   ├── VaultAuthService.java
│   ├── VaultCredentialFetcher.java
│   ├── VaultCredentialsCache.java
│   ├── VaultDataSourceProvider.java
│   └── SecurePostgresService.java
└── controller/
    └── VaultTestController.java
🧩 1. VaultProperties (config via application.yml ou .properties)
java
Copier
Modifier
@Configuration
@ConfigurationProperties(prefix = "vault")
public class VaultProperties {

    private String address;
    private String namespace;
    private String role;
    private String keystorePath;
    private String keystorePassword;

    // getters & setters
}
📄 2. application.yml (ou .properties)
yaml
Copier
Modifier
vault:
  address: https://vault.example.com
  namespace: my/namespace
  role: my-postgres-role
  keystorePath: classpath:certs/client.p12
  keystorePassword: changeit
🧠 3. Credentials.java
java
Copier
Modifier
public class Credentials {
    private final String username;
    private final String password;

    public Credentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // getters
}
🔐 4. VaultAuthService.java (authentification par certificat client)
java
Copier
Modifier
@Service
public class VaultAuthService {

    private final VaultProperties properties;

    public VaultAuthService(VaultProperties properties) {
        this.properties = properties;
    }

    public String authenticateWithCert() {
        try (InputStream is = new ClassPathResource(properties.getKeystorePath()).getInputStream()) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(is, properties.getKeystorePassword().toCharArray());

            SSLContext sslContext = SSLContextBuilder.create()
                    .loadKeyMaterial(ks, properties.getKeystorePassword().toCharArray())
                    .build();

            CloseableHttpClient client = HttpClients.custom()
                    .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext))
                    .build();

            RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Vault-Namespace", properties.getNamespace());
            headers.set("X-Vault-Request", "true");
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = "{\"name\":\"" + properties.getRole() + "\"}";
            ResponseEntity<String> response = restTemplate.postForEntity(
                    properties.getAddress() + "/v1/auth/cert/login",
                    new HttpEntity<>(body, headers),
                    String.class
            );

            return new ObjectMapper()
                    .readTree(response.getBody())
                    .path("auth").path("client_token").asText();

        } catch (Exception e) {
            throw new RuntimeException("Erreur d'authentification Vault", e);
        }
    }
}
🔄 5. VaultCredentialFetcher.java
java
Copier
Modifier
@Service
public class VaultCredentialFetcher {

    private final VaultAuthService authService;
    private final VaultProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private String currentToken;

    public VaultCredentialFetcher(VaultAuthService authService, VaultProperties properties) {
        this.authService = authService;
        this.properties = properties;
        this.currentToken = authService.authenticateWithCert();
    }

    public Credentials fetch() {
        try {
            return fetchWithToken();
        } catch (HttpClientErrorException.Forbidden e) {
            this.currentToken = authService.authenticateWithCert();
            return fetchWithToken();
        }
    }

    private Credentials fetchWithToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", currentToken);
        headers.set("X-Vault-Namespace", properties.getNamespace());
        headers.set("X-Vault-Request", "true");

        ResponseEntity<String> response = restTemplate.exchange(
                properties.getAddress() + "/v1/database/creds/" + properties.getRole(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        JsonNode data = mapper.readTree(response.getBody()).path("data");
        return new Credentials(data.path("username").asText(), data.path("password").asText());
    }
}
🧠 6. VaultCredentialsCache.java
java
Copier
Modifier
@Component
public class VaultCredentialsCache {
    private final AtomicReference<Credentials> cache = new AtomicReference<>();

    public Credentials get() {
        return cache.get();
    }

    public void update(Credentials credentials) {
        cache.set(credentials);
    }

    public void clear() {
        cache.set(null);
    }
}
🛠 7. VaultDataSourceProvider.java
java
Copier
Modifier
@Component
public class VaultDataSourceProvider {

    private final VaultCredentialsCache cache;

    public VaultDataSourceProvider(VaultCredentialsCache cache) {
        this.cache = cache;
    }

    public DataSource build() {
        Credentials creds = cache.get();
        if (creds == null) throw new IllegalStateException("Aucun credentials disponibles");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://host:5432/db");
        config.setUsername(creds.getUsername());
        config.setPassword(creds.getPassword());
        config.setMaximumPoolSize(2);

        return new HikariDataSource(config);
    }
}
🔁 8. SecurePostgresService.java
java
Copier
Modifier
@Service
public class SecurePostgresService {

    private final VaultCredentialsCache cache;
    private final VaultCredentialFetcher fetcher;
    private final VaultDataSourceProvider provider;

    public SecurePostgresService(VaultCredentialsCache cache,
                                 VaultCredentialFetcher fetcher,
                                 VaultDataSourceProvider provider) {
        this.cache = cache;
        this.fetcher = fetcher;
        this.provider = provider;
        refreshCredentials(); // initial fetch
    }

    public void testConnection() {
        try (Connection conn = provider.build().getConnection()) {
            if (!conn.isClosed()) {
                System.out.println("✅ Connexion PostgreSQL réussie !");
            }
        } catch (SQLException e) {
            System.out.println("⚠️ Connexion échouée, rafraîchissement des credentials...");
            refreshCredentials();
            try (Connection retry = provider.build().getConnection()) {
                if (!retry.isClosed()) {
                    System.out.println("✅ Connexion réussie après refresh !");
                }
            } catch (SQLException ex) {
                throw new RuntimeException("❌ Connexion échouée même après refresh", ex);
            }
        }
    }

    private void refreshCredentials() {
        Credentials newCreds = fetcher.fetch();
        cache.update(newCreds);
    }
}
✅ 9. VaultTestController.java (optionnel)
java
Copier
Modifier
@RestController
@RequestMapping("/vault")
public class VaultTestController {

    private final SecurePostgresService service;

    public VaultTestController(SecurePostgresService service) {
        this.service = service;
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        service.testConnection();
        return ResponseEntity.ok("Test terminé.");
    }
}
Tu peux maintenant :

Centraliser l’authentification cert,

Rafraîchir seulement en cas d’erreur,

Garder des composants testables, injectables, réutilisables.

Souhaites-tu que je t’aide à générer un squelette de projet complet (ex: pom.xml, structure ZIP) ?
///////////////////////////////////////////////////
✅ Étapes à suivre
1. Injecter SecurePostgresService dans ton service métier
java
Copier
Modifier
@Service
public class MonServiceMetier {

    private final SecurePostgresService securePostgresService;

    public MonServiceMetier(SecurePostgresService securePostgresService) {
        this.securePostgresService = securePostgresService;
    }

    public void lireDonnees() {
        try (Connection conn = securePostgresService.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM ma_table");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String valeur = rs.getString("colonne");
                System.out.println("Valeur = " + valeur);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lors de la lecture des données", e);
        }
    }
}
2. Ajouter une méthode getConnection() dans SecurePostgresService
Ton SecurePostgresService expose actuellement uniquement testConnection(). Il faut lui ajouter un accès direct à la Connection :

java
Copier
Modifier
public Connection getConnection() throws SQLException {
    try {
        return provider.build().getConnection();
    } catch (SQLException e) {
        System.out.println("⚠️ Connexion échouée, rafraîchissement des credentials...");
        refreshCredentials();
        return provider.build().getConnection();
    }
}
3. Utilisation dans un contrôleur ou autre composant
java
Copier
Modifier
@RestController
@RequestMapping("/data")
public class DonneesController {

    private final MonServiceMetier metier;

    public DonneesController(MonServiceMetier metier) {
        this.metier = metier;
    }

    @GetMapping("/read")
    public ResponseEntity<String> lire() {
        metier.lireDonnees();
        return ResponseEntity.ok("Lecture terminée");
    }
}
🧩 Résumé
Tu peux accéder aux données PostgreSQL comme d’habitude avec JDBC, en passant par une Connection obtenue via SecurePostgresService.

🛡 Bonus recommandé : Gestion transactionnelle
Si tu fais des écritures complexes, il serait utile d’ajouter un wrapper autour de la Connection pour supporter les transactions manuelles (ou envisager un JdbcTemplate si tu veux aller plus loin).

Souhaites-tu que je t’aide à :

Ajouter un service pour exécuter des requêtes génériques ?

Mettre en place un JdbcTemplate sécurisé basé sur les credentials Vault ?

Ajouter des tests unitaires avec H2 ou PostgreSQL mocké ?

Je peux aussi t’ajouter un exemple complet de lecture/écriture dans ma_table.







Vous avez dit :
Ajouter un service pour exécuter des requêtes génériques ?


ChatGPT a dit :
Parfait ! Voici comment ajouter un service générique d'exécution de requêtes SQL (lecture/écriture) en utilisant ton système de connexion sécurisée (SecurePostgresService).

✅ Objectif
Créer un GenericQueryService qui permet :

d’exécuter des requêtes de lecture (SELECT) avec un ResultSetExtractor ou RowMapper.

d’exécuter des requêtes d'écriture (INSERT, UPDATE, DELETE) avec paramètres.

📁 Fichier : GenericQueryService.java
java
Copier
Modifier
package com.example.securevault.service;

import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
public class GenericQueryService {

    private final SecurePostgresService securePostgresService;

    public GenericQueryService(SecurePostgresService securePostgresService) {
        this.securePostgresService = securePostgresService;
    }

    public <T> List<T> executeQuery(String sql, List<Object> params, Function<ResultSet, T> rowMapper) {
        try (Connection conn = securePostgresService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            ResultSet rs = stmt.executeQuery();
            List<T> results = new ArrayList<>();
            while (rs.next()) {
                results.add(rowMapper.apply(rs));
            }
            return results;

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lors de l'exécution de la requête", e);
        }
    }

    public int executeUpdate(String sql, List<Object> params) {
        try (Connection conn = securePostgresService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            return stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Erreur lors de l'exécution de la mise à jour", e);
        }
    }
}
🧪 Exemple d'utilisation
Service métier
java
Copier
Modifier
@Service
public class UtilisateurService {

    private final GenericQueryService queryService;

    public UtilisateurService(GenericQueryService queryService) {
        this.queryService = queryService;
    }

    public List<String> tousLesPrenoms() {
        String sql = "SELECT prenom FROM utilisateurs";
        return queryService.executeQuery(sql, List.of(), rs -> rs.getString("prenom"));
    }

    public void ajouterUtilisateur(String nom, String prenom) {
        String sql = "INSERT INTO utilisateurs (nom, prenom) VALUES (?, ?)";
        queryService.executeUpdate(sql, List.of(nom, prenom));
    }
}
Contrôleur simple
java
Copier
Modifier
@RestController
@RequestMapping("/utilisateurs")
public class UtilisateurController {

    private final UtilisateurService service;

    public UtilisateurController(UtilisateurService service) {
        this.service = service;
    }

    @GetMapping
    public List<String> tous() {
        return service.tousLesPrenoms();
    }

    @PostMapping
    public ResponseEntity<Void> ajouter(@RequestParam String nom, @RequestParam String prenom) {
        service.ajouterUtilisateur(nom, prenom);
        return ResponseEntity.ok().build();
    }
}
🧠 Avantages
🔄 Réutilisable pour n’importe quelle requête SQL.

🧼 Paramétré proprement avec PreparedStatement (pas d'injection SQL).

🔗 Connecté directement à ton système d'authentification Vault.

Souhaites-tu aussi :

Ajouter un support transactionnel ?

Retourner des DTO complexes ?

Utiliser JdbcTemplate ou JPA à la place ?

Je peux adapter selon ton besoin exact.







Vous avez dit :
Retourner des DTO complexes ?


ChatGPT a dit :
Parfait 👌 ! Retourner des DTO complexes depuis le service générique est très simple : il suffit de mapper le ResultSet vers ton propre type de retour, comme un POJO/DTO personnalisé.

🧱 1. Exemple de DTO
java
Copier
Modifier
public class UtilisateurDto {
    private final Long id;
    private final String nom;
    private final String prenom;

    public UtilisateurDto(Long id, String nom, String prenom) {
        this.id = id;
        this.nom = nom;
        this.prenom = prenom;
    }

    // getters
}
🧠 2. Utilisation avec GenericQueryService
Tu mappes chaque ligne du ResultSet vers un UtilisateurDto :

java
Copier
Modifier
public List<UtilisateurDto> listerTousLesUtilisateurs() {
    String sql = "SELECT id, nom, prenom FROM utilisateurs";

    return queryService.executeQuery(sql, List.of(), rs ->
        new UtilisateurDto(
            rs.getLong("id"),
            rs.getString("nom"),
            rs.getString("prenom")
        )
    );
}
✅ 3. Contrôleur REST
java
Copier
Modifier
@GetMapping
public List<UtilisateurDto> lister() {
    return service.listerTousLesUtilisateurs();
}
📌 Bonus : méthode générique avec Class<T> (optionnel)
Si tu veux aller encore plus générique, tu peux créer une version qui utilise une interface RowMapper<T> ou un Function<ResultSet, T> comme déjà fait.

Mais si tu as besoin d’un mapping automatique (par nom de colonne → propriété Java), je peux aussi te générer une version avec BeanPropertyRowMapper de Spring ou une librairie comme MapStruct.

