💻 Code complet
1. VaultService.java
java
Copier
Modifier
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class VaultService {

    // Simuler une récupération depuis Vault (via REST, Vault Java SDK, etc.)
    public Map<String, String> getPostgresCredentials() {
        // Appel réel à Vault à faire ici
        return Map.of(
            "username", "vault-username",
            "password", "vault-password"
        );
    }
}
2. CredentialsCache.java
java
Copier
Modifier
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class CredentialsCache {

    private final AtomicReference<Map<String, String>> cache = new AtomicReference<>();

    public Map<String, String> getCredentials(VaultService vaultService) {
        if (cache.get() == null) {
            synchronized (this) {
                if (cache.get() == null) {
                    Map<String, String> creds = vaultService.getPostgresCredentials();
                    cache.set(creds);
                }
            }
        }
        return cache.get();
    }

    // Méthode pour forcer le refresh, ex: appelé par un scheduler
    public void refresh(VaultService vaultService) {
        cache.set(vaultService.getPostgresCredentials());
    }
}
3. DataSourceConfig.java
java
Copier
Modifier
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Autowired
    private VaultService vaultService;

    @Autowired
    private CredentialsCache credentialsCache;

    @Bean
    public DataSource dataSource() {
        Map<String, String> credentials = credentialsCache.getCredentials(vaultService);

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl("jdbc:postgresql://localhost:5432/ma_base");
        dataSource.setUsername(credentials.get("username"));
        dataSource.setPassword(credentials.get("password"));
        return dataSource;
    }
}
4. Application.java
java
Copier
Modifier
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
🧪 Bonus : Détection de changement des credentials
Si Vault change les identifiants (ex: rotation automatique), tu pourrais :

Utiliser un TTL et scheduler pour recharger régulièrement.

Vérifier périodiquement dans CredentialsCache si les credentials ont changé (via un hash par exemple).

Utiliser une intégration avec Vault Agent ou Spring Cloud Vault pour automatiser.

Tu veux que je t'ajoute un exemple de récupération réelle depuis Vault (via REST ou Spring Cloud Vault) ou un petit script Bash côté serveur pour injecter ces secrets au démarrage ?







Vous avez dit :
Utiliser un TTL et scheduler pour recharger régulièrement.


ChatGPT a dit :
Parfait ! Voici comment tu peux utiliser un TTL (Time-To-Live) avec un scheduler pour recharger les credentials depuis Vault régulièrement, mais uniquement si les identifiants ont changé.

🛠️ Mise à jour de l’implémentation avec TTL + Scheduler
1. CredentialsCache avec TTL
java
Copier
Modifier
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class CredentialsCache {

    private final AtomicReference<Map<String, String>> cache = new AtomicReference<>();
    private Instant lastLoadTime = Instant.EPOCH;
    private final long ttlSeconds = 300; // TTL = 5 minutes

    public Map<String, String> getCredentials(VaultService vaultService) {
        if (shouldReload()) {
            synchronized (this) {
                if (shouldReload()) {
                    Map<String, String> newCreds = vaultService.getPostgresCredentials();
                    cache.set(newCreds);
                    lastLoadTime = Instant.now();
                }
            }
        }
        return cache.get();
    }

    private boolean shouldReload() {
        return cache.get() == null || Instant.now().isAfter(lastLoadTime.plusSeconds(ttlSeconds));
    }

    public void forceRefresh(VaultService vaultService) {
        synchronized (this) {
            cache.set(vaultService.getPostgresCredentials());
            lastLoadTime = Instant.now();
        }
    }
}
2. VaultRefresher.java – Scheduler Spring pour refresh
java
Copier
Modifier
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VaultRefresher {

    private final VaultService vaultService;
    private final CredentialsCache credentialsCache;

    public VaultRefresher(VaultService vaultService, CredentialsCache credentialsCache) {
        this.vaultService = vaultService;
        this.credentialsCache = credentialsCache;
    }

    // Tous les 5 minutes
    @Scheduled(fixedRate = 300_000)
    public void refreshCredentials() {
        System.out.println("Refreshing credentials from Vault...");
        credentialsCache.forceRefresh(vaultService);
    }
}
3. Activer le scheduling dans ton Application.java
java
Copier
Modifier
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
✅ Résultat
Les credentials sont récupérés à la première demande.

Ensuite, ils sont mis en cache pendant 5 minutes (ttlSeconds).

Un scheduler automatique (basé sur Spring @Scheduled) recharge les credentials toutes les 5 minutes aussi, même si personne ne fait d’appel, ce qui permet d’avoir les derniers identifiants avant expiration si rotation automatique côté Vault.

Tu veux que je t’ajoute un check pour éviter de rafraîchir si les credentials ne changent pas (par comparaison de hash par exemple) ?







Vous avez dit :
avec une connexion a vault par certificat 


ChatGPT a dit :
Parfait ! Voici comment intégrer une connexion à Vault via certificat client (TLS mutuel), avec récupération des credentials PostgreSQL, mise en cache avec TTL, et scheduler automatique.

🔐 Objectif
Se connecter à Vault via TLS mutualisé (certificat client).

Récupérer les credentials PostgreSQL depuis un chemin de Vault (ex : database/creds/my-role).

Mettre en cache les credentials avec TTL.

Rafraîchir automatiquement avec un scheduler.

🧱 Prérequis côté Vault
Le certificat client et clé privée doivent être disponibles (ex: /etc/vault/certs/client.crt, /etc/vault/certs/client.key).

Le certificat CA de Vault (ex: /etc/vault/certs/ca.crt).

Le rôle Vault (my-role) configuré pour générer des credentials PostgreSQL.

⚙️ Dépendance Maven
Ajoute dans ton pom.xml :

xml
Copier
Modifier
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
🔧 VaultService avec certificat TLS
java
Copier
Modifier
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.util.Map;

@Service
public class VaultService {

    private static final String VAULT_URL = "https://vault.example.com:8200/v1/database/creds/my-role";
    private static final ObjectMapper mapper = new ObjectMapper();

    public Map<String, String> getPostgresCredentials() {
        try (CloseableHttpClient client = createHttpClient()) {
            HttpGet request = new HttpGet(VAULT_URL);
            try (CloseableHttpResponse response = client.execute(request)) {
                JsonNode root = mapper.readTree(response.getEntity().getContent());
                JsonNode data = root.path("data");

                return Map.of(
                    "username", data.get("username").asText(),
                    "password", data.get("password").asText()
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Error retrieving credentials from Vault", e);
        }
    }

    private CloseableHttpClient createHttpClient() throws Exception {
        SSLContext sslContext = SSLContexts.custom()
            .loadKeyMaterial(
                new File("/etc/vault/certs/client.p12"), // PKCS12 contenant clé + cert client
                "changeit".toCharArray(),                // mot de passe du keystore
                "changeit".toCharArray()
            )
            .loadTrustMaterial(
                new File("/etc/vault/certs/ca.crt"),
                null
            )
            .build();

        return HttpClients.custom()
            .setSSLContext(sslContext)
            .build();
    }
}
💡 Si tu as des fichiers séparés .crt et .key, tu peux les convertir en .p12 :

bash
Copier
Modifier
openssl pkcs12 -export \
  -inkey client.key \
  -in client.crt \
  -certfile ca.crt \
  -out client.p12
🧩 Intégration avec le cache + scheduler
Réutilise les classes précédentes :

CredentialsCache (avec TTL)

VaultRefresher (avec @Scheduled)

DataSourceConfig (création de la DataSource PostgreSQL)

✅ Résultat final
La connexion à Vault se fait via certificat client (TLS).

Les credentials sont récupérés dynamiquement et mis en cache.

Le scheduler les rafraîchit automatiquement toutes les 5 minutes.

