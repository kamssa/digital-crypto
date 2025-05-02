
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