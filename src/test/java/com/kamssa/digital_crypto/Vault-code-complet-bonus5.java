@Service
public class SecurePostgresService {

    private final VaultDataSourceManager manager;

    public SecurePostgresService(VaultDataSourceManager manager) {
        this.manager = manager;
    }

    public void testConnection() {
        try (Connection conn = manager.getDataSource().getConnection()) {
            if (!conn.isClosed()) {
                System.out.println("✅ Connexion PostgreSQL réussie via Vault !");
            }
        } catch (SQLException ex) {
            System.err.println("❌ Connexion échouée, tentative de rafraîchissement...");
            try {
                manager.refreshDataSource();
                try (Connection retryConn = manager.getDataSource().getConnection()) {
                    if (!retryConn.isClosed()) {
                        System.out.println("✅ Connexion réussie après rafraîchissement !");
                    }
                }
            } catch (Exception retryEx) {
                throw new RuntimeException("Échec de la connexion même après rafraîchissement du token", retryEx);
            }
        }
    }
}
/////////////////////////////////////
@Service
public class VaultConnectionValidator {

    private final VaultDataSourceManager vaultDataSourceManager;

    public VaultConnectionValidator(VaultDataSourceManager vaultDataSourceManager) {
        this.vaultDataSourceManager = vaultDataSourceManager;
    }

    public void validateConnection() {
        try (Connection conn = vaultDataSourceManager.getDataSource().getConnection()) {
            if (!conn.isClosed()) {
                System.out.println("✅ Connexion PostgreSQL réussie via Vault !");
            }
        } catch (SQLException e) {
            System.err.println("❌ Connexion échouée, tentative de refresh...");

            try {
                vaultDataSourceManager.refreshDataSource();

                try (Connection retryConn = vaultDataSourceManager.getDataSource().getConnection()) {
                    if (!retryConn.isClosed()) {
                        System.out.println("✅ Connexion réussie après refresh !");
                    }
                }
            } catch (Exception retryEx) {
                throw new RuntimeException("Connexion échouée après tentative de renouvellement", retryEx);
            }
        }
    }
}
@Component
public class VaultDataSourceManager {

    private final VaultClient vaultClient;
    private final VaultTokenHolder tokenHolder;
    private final VaultProperties vaultProperties;
    private final ObjectMapper mapper;

    private volatile HikariDataSource currentDataSource;

    public VaultDataSourceManager(VaultClient vaultClient,
                                  VaultTokenHolder tokenHolder,
                                  VaultProperties vaultProperties,
                                  ObjectMapper mapper) {
        this.vaultClient = vaultClient;
        this.tokenHolder = tokenHolder;
        this.vaultProperties = vaultProperties;
        this.mapper = mapper;
        this.currentDataSource = buildNewDataSource(); // init
    }

    public DataSource getDataSource() {
        return currentDataSource;
    }

    public synchronized void refreshDataSource() {
        if (currentDataSource != null) {
            currentDataSource.close();
        }
        this.currentDataSource = buildNewDataSource();
    }

    private HikariDataSource buildNewDataSource() {
        try {
            Credentials creds = fetchCredentialsFromVault();

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://your-host:5432/your-db"); // à adapter
            config.setUsername(creds.getUsername());
            config.setPassword(creds.getPassword());
            config.setMaximumPoolSize(5);
            config.setPoolName("VaultDataSource");

            return new HikariDataSource(config);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de construire la DataSource Vault", ex);
        }
    }

    private Credentials fetchCredentialsFromVault() throws Exception {
        String credsUrl = vaultProperties.getAddress() + "/v1/database/creds/mon-role";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", tokenHolder.getToken());
        headers.set("X-Vault-Namespace", vaultProperties.getNamespace());
        headers.set("X-Vault-Request", "true");

        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    credsUrl, HttpMethod.GET, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Vault credentials fetch failed: " + response.getStatusCode());
            }

            JsonNode node = mapper.readTree(response.getBody());
            String username = node.path("data").path("username").asText();
            String password = node.path("data").path("password").asText();

            return new Credentials(username, password);
        } catch (HttpClientErrorException.Forbidden e) {
            // 🔁 token peut être expiré
            System.out.println("⚠️ Token Vault expiré, tentative de refresh");
            String newToken = vaultClient.authenticate();
            tokenHolder.updateToken(newToken);
            return fetchCredentialsFromVault(); // retry après refresh
        }
    }

    private static class Credentials {
        private final String username;
        private final String password;

        public Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }
}