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
/////////// solution 2////////////////
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.util.Map;

public class PostgresConnectionManager {

    private final int maxAttempts;
    private final CredentialsService credentialsService; // à injecter
    private final VaultService vaultService;             // à injecter

    public PostgresConnectionManager(int maxAttempts, CredentialsService credentialsService, VaultService vaultService) {
        this.maxAttempts = maxAttempts;
        this.credentialsService = credentialsService;
        this.vaultService = vaultService;
    }

    public DataSource createDataSource() {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            try {
                Map<String, String> credentials = credentialsService.getCredentials(vaultService);

                if (credentials == null || credentials.isEmpty()) {
                    throw new RuntimeException("Credentials sont vides ou nuls");
                }

                DriverManagerDataSource dataSource = new DriverManagerDataSource();
                dataSource.setDriverClassName("org.postgresql.Driver");
                dataSource.setUrl("jdbc:postgresql://localhost:5432/votre_nom_de_base");
                dataSource.setUsername(credentials.get("username"));
                dataSource.setPassword(credentials.get("password"));

                // Test immédiat de la connexion
                dataSource.getConnection().close();

                System.out.println("[SUCCESS] Connexion réussie à la tentative " + (attempt + 1));
                return dataSource;

            } catch (Exception ex) {
                attempt++;
                lastException = ex;
                System.err.println("[FAIL] Tentative " + attempt + " échouée : " + ex.getMessage());

                credentialsService.refreshCredentials(); // On force le refresh des credentials

                try {
                    Thread.sleep(1000); // Petite pause avant retry
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        throw new RuntimeException("Impossible de se connecter à PostgreSQL après " + maxAttempts + " tentatives.", lastException);
    }
}
Et ensuite ton @Configuration devient ultra simple :
java
Copier
Modifier
@Bean
public DataSource dataSource(PostgresConnectionManager connectionManager) {
    return connectionManager.createDataSource();
}
//////////////////////////////cache //////////////////////////
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class CredentialsService {

    private final CredentialsCache credentialsCache; // Ton cache existant
    private final VaultService vaultService;         // Ton service Vault existant

    // Optionnel : un cache interne thread-safe
    private final AtomicReference<Map<String, String>> currentCredentials = new AtomicReference<>();

    public CredentialsService(CredentialsCache credentialsCache, VaultService vaultService) {
        this.credentialsCache = credentialsCache;
        this.vaultService = vaultService;
    }

    public Map<String, String> getCredentials(VaultService vaultService) {
        Map<String, String> credentials = currentCredentials.get();
        if (credentials == null || credentials.isEmpty()) {
            credentials = credentialsCache.getCredentials(vaultService);
            currentCredentials.set(credentials);
        }
        return credentials;
    }

    public void refreshCredentials() {
        System.out.println("[INFO] Rafraîchissement des credentials en cours...");
        credentialsCache.refresh(vaultService); // <--- À toi d'implémenter ça proprement
        Map<String, String> freshCredentials = credentialsCache.getCredentials(vaultService);
        currentCredentials.set(freshCredentials);
        System.out.println("[INFO] Credentials rafraîchis avec succès.");
    }
}
👉 Côté CredentialsCache, il doit donc exposer deux méthodes :
java
Copier
Modifier
Map<String, String> getCredentials(VaultService vaultService);
void refresh(VaultService vaultService);
Exemple rapide :

java
Copier
Modifier
public class CredentialsCache {

    private Map<String, String> cachedCredentials;

    public Map<String, String> getCredentials(VaultService vaultService) {
        if (cachedCredentials == null) {
            refresh(vaultService);
        }
        return cachedCredentials;
    }

    public void refresh(VaultService vaultService) {
        // Ici tu vas contacter Vault et recharger le secret
        this.cachedCredentials = vaultService.retrieveCredentials();
    }
}

