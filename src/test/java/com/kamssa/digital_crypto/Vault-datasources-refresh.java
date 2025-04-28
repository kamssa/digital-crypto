 Architecture
VaultCredentialCache → récupère et surveille les credentials

RefreshableDataSourceHolder → contient la DataSource courante

DataSourceRoutingConfig → fournit un DataSource unique à Spring

1. VaultCredentialCache (existant, inchangé)
2. RefreshableDataSourceHolder.java
java
Copier
Modifier
@Component
public class RefreshableDataSourceHolder {

    private final VaultCredentialCache credentialCache;
    private final AtomicReference<HikariDataSource> currentDataSource = new AtomicReference<>();

    private String lastChecksum = "";

    public RefreshableDataSourceHolder(VaultCredentialCache credentialCache) {
        this.credentialCache = credentialCache;
        refreshDataSource(); // Initial load
    }

    public DataSource getDataSource() {
        return currentDataSource.get();
    }

    @Scheduled(fixedDelay = 300_000) // Toutes les 5 minutes
    public synchronized void refreshDataSource() {
        DatabaseCredentials creds = credentialCache.getCredentials();
        String newChecksum = (creds.getUsername() + creds.getPassword()).hashCode() + "";

        if (!newChecksum.equals(lastChecksum)) {
            System.out.println("🔁 Rebuilding DataSource with new Vault credentials...");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/your_db"); // adapte ici
            config.setUsername(creds.getUsername());
            config.setPassword(creds.getPassword());
            config.setDriverClassName("org.postgresql.Driver");

            HikariDataSource newDs = new HikariDataSource(config);

            HikariDataSource old = currentDataSource.getAndSet(newDs);
            if (old != null) old.close();

            lastChecksum = newChecksum;
        }
    }
}
3. Configuration Spring : fournir le DataSource depuis le holder
java
Copier
Modifier
@Configuration
public class DataSourceConfig {

    private final RefreshableDataSourceHolder holder;

    public DataSourceConfig(RefreshableDataSourceHolder holder) {
        this.holder = holder;
    }

    @Primary
    @Bean
    public DataSource dataSource() {
        return new DelegatingDataSource(holder.getDataSource()) {
            @Override
            public Connection getConnection() throws SQLException {
                return holder.getDataSource().getConnection();
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return holder.getDataSource().getConnection(username, password);
            }
        };
    }
}
✅ Ce DelegatingDataSource appelle toujours la version à jour de getDataSource().

💡 Résultat
Les credentials sont récupérés depuis Vault

Le DataSource est reconstruit automatiquement si le secret change

Aucune interruption visible pour l’app (les pools sont recréés proprement)

Tu veux que je t’ajoute une version avec logs, ou une API pour forcer un refresh manuellement via un endpoint /refresh-datasource ?
@Bean
public DataSource dataSource() {
    int maxAttempts = 5;
    int attempt = 0;
    Exception lastException = null;

    while (attempt < maxAttempts) {
        try {
            Map<String, String> credentials = credentialsCache.getCredentials(vaultService);

            if (credentials == null || credentials.isEmpty()) {
                throw new RuntimeException("Credentials sont vides ou nuls");
            }

            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl("jdbc:postgresql://localhost:5432/votre_nom_de_base");
            dataSource.setUsername(credentials.get("username"));  // correction ici
            dataSource.setPassword(credentials.get("password"));  // correction ici

            // Tester la connexion immédiatement
            dataSource.getConnection().close(); // Si ça passe ici, connexion réussie

            System.out.println("Connexion réussie à la tentative " + (attempt + 1));
            return dataSource;

        } catch (Exception ex) {
            attempt++;
            lastException = ex;
            System.err.println("Tentative " + attempt + " échouée : " + ex.getMessage());

            // Important : rafraîchir le token ou credentials
            refreshCredentials();

            try {
                Thread.sleep(1000); // petite pause avant retry (optionnel)
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Si aucune tentative n'a marché, throw l'exception capturée
    throw new RuntimeException("Impossible de se connecter à PostgreSQL après " + maxAttempts + " tentatives.", lastException);
}