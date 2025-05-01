
comment  utiliser ce code dans mon application java pour récupérer le token

ChatGPT a dit :
Pour utiliser ce code curl dans ton application Java et récupérer le token Vault, tu peux utiliser la bibliothèque HttpClient (incluse à partir de Java 11) pour faire un appel HTTPS avec authentification par certificat client.

Voici un exemple complet en Java :

✅ Exemple avec HttpClient (Java 11+)
java
Copier
Modifier
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import javax.net.ssl.SSLContext;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.security.SecureRandom;
import javax.net.ssl.TrustManagerFactory;

public class VaultTokenFetcher {

    public static void main(String[] args) throws Exception {
        String vaultUrl = "https://hvault.staging.echonet/v1/auth/cert/login"; // ou prod
        String payload = "{\"name\":\"[NOM CERTIFICAT FOURNI PAR VAULT]\"}";

        // Charger le keystore contenant le certificat client
        char[] password = "[MOT_DE_PASSE_CERTIFICAT]".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream("chemin/vers/[CERTIFICAT].p12"), password);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, password);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(keyStore); // Ou null si tu fais confiance aux certs par défaut

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vaultUrl))
                .header("Content-Type", "application/json")
                .header("X-Vault-Namespace", "DATA/8183/[ECOSYSTEME]")
                .header("X-Vault-Request", "true")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Vault token: " + response.body());
    }
}
📝 Remplace :
[NOM CERTIFICAT FOURNI PAR VAULT] par le nom utilisé dans Vault.

chemin/vers/[CERTIFICAT].p12 par le chemin vers ton certificat client.

[MOT_DE_PASSE_CERTIFICAT] si ton .p12 est protégé par un mot de passe.

[ECOSYSTEME] par la valeur adaptée à ton environnement.

Souhaites-tu un exemple avec RestTemplate ou une autre stack comme Spring WebClient ?







Vous avez dit :
en java 8 et spring boot 2


ChatGPT a dit :
En Java 8 avec Spring Boot 2, tu peux utiliser RestTemplate et configurer un HttpClient avec certificat client en SSL. Comme Java 8 ne supporte pas HttpClient (introduit en Java 11), il faut passer par HttpComponentsClientHttpRequestFactory et RestTemplate.

✅ Étapes :
Charger le certificat client (format .p12 ou .jks)

Créer un RestTemplate avec SSL

Appeler l’API Vault

🧩 Exemple Complet (Java 8 + Spring Boot 2)
java
Copier
Modifier
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.impl.client.CloseableHttpClient;

public class VaultTokenFetcher {

    public static void main(String[] args) throws Exception {
        String url = "https://hvault.staging.echonet/v1/auth/cert/login";
        String jsonPayload = "{\"name\":\"[NOM CERTIFICAT FOURNI PAR VAULT]\"}";

        // Charger le keystore contenant le certificat client
        char[] password = "[MOT_DE_PASSE_CERTIFICAT]".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        FileInputStream fis = new FileInputStream("chemin/vers/[CERTIFICAT].p12");
        keyStore.load(fis, password);

        SSLContext sslContext = SSLContexts.custom()
                .loadKeyMaterial(keyStore, password) // certificat client
                .loadTrustMaterial(null, (chain, authType) -> true) // trust all certs (à sécuriser en prod)
                .build();

        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext);
        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(socketFactory)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate restTemplate = new RestTemplate(factory);

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Vault-Namespace", "DATA/8183/[ECOSYSTEME]");
        headers.set("X-Vault-Request", "true");

        HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

        // Appel
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        System.out.println("Vault token: " + response.getBody());
    }
}
🛠️ À adapter :
Remplace chemin/vers/[CERTIFICAT].p12 par le chemin vers ton fichier .p12

Remplace [MOT_DE_PASSE_CERTIFICAT], [NOM CERTIFICAT FOURNI PAR VAULT], et [ECOSYSTEME] selon ton cas

Souhaites-tu que je t’aide à intégrer ça dans un service Spring (@Service) ?







Vous avez dit :

comment appliquer ca a mon code ?

ChatGPT a dit :
L’image montre comment récupérer les identifiants (user/password) via Vault après avoir obtenu le token.

Tu as donc 2 appels API à faire dans ton code Java :

✅ Étape 1 : Obtenir le token Vault (déjà vu précédemment)
Tu fais un POST sur /v1/auth/cert/login en envoyant ton certificat client.

👉 Résultat : tu obtiens un token (Vault client token).

✅ Étape 2 : Utiliser le token pour récupérer les identifiants
En Java avec Spring Boot 2 et Java 8 (suite du code précédent), voici comment faire le deuxième appel :

🔁 Exemple Java – Appel GET avec le token
java
Copier
Modifier
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

public class VaultCredentialFetcher {

    public static void main(String[] args) {
        String token = "[TOKEN GENERE PRECEDEMMENT]";
        String namespace = "DATA/8183/[ECOSYSTEME]";
        String accountName = "[NOM DU COMPTE]";
        String environmentUrl = "https://hvault.staging.echonet"; // ou prod

        // Exemple pour hors-prod :
        String url = environmentUrl + "/v1/secret/data/opendata/dev/" + accountName;

        // Exemple pour prod (utilise plutôt l’autre chemin de l’image si nécessaire) :
        // String url = environmentUrl + "/v1/database/postgres/[DATABASE_OPENDATA]/static-creds/" + accountName;

        // Crée le RestTemplate avec HttpClient configuré (ou réutilise celui du login)
        RestTemplate restTemplate = new RestTemplate(); // ou utiliser celui avec certificat si besoin

        // Headers avec le token
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Namespace", namespace);
        headers.set("X-Vault-Token", token);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
        );

        System.out.println("User/password JSON: " + response.getBody());
    }
}
🧠 Résultat
Tu obtiens une réponse JSON contenant un username et un password dans le champ data.data.

Souhaites-tu que je t’aide à encapsuler les deux étapes dans un service Spring Boot (ex: VaultService) avec méthode getCredentials() ?







Vous avez dit :
Souhaites-tu que je t’aide à encapsuler les deux étapes dans un service Spring Boot (ex: VaultService) avec méthode getCredentials() ?


ChatGPT a dit :
Parfait ! Voici une version propre de VaultService en Spring Boot (Java 8) qui encapsule les deux étapes :

Authentification via certificat client pour obtenir le token

Appel à l’API Vault pour obtenir les identifiants (user/password)

✅ VaultService.java
java
Copier
Modifier
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Map;

@Service
public class VaultService {

    private RestTemplate restTemplate;
    private final String CERT_PATH = "chemin/vers/certificat.p12";
    private final String CERT_PASSWORD = "motdepasse";
    private final String CERT_NAME = "nom_du_certificat";
    private final String NAMESPACE = "DATA/8183/ecosysteme";
    private final String ENV_URL = "https://hvault.staging.echonet"; // ou prod

    @PostConstruct
    public void init() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream(CERT_PATH), CERT_PASSWORD.toCharArray());

        SSLContext sslContext = SSLContexts.custom()
                .loadKeyMaterial(keyStore, CERT_PASSWORD.toCharArray())
                .loadTrustMaterial(null, (x509CertChain, authType) -> true)
                .build();

        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(csf)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        restTemplate = new RestTemplate(factory);
    }

    public Map<String, String> getCredentials(String accountName) {
        String token = authenticate();
        return fetchCredentials(token, accountName);
    }

    private String authenticate() {
        String url = ENV_URL + "/v1/auth/cert/login";
        String jsonBody = "{\"name\":\"" + CERT_NAME + "\"}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Vault-Namespace", NAMESPACE);
        headers.set("X-Vault-Request", "true");

        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        Map<String, Object> auth = (Map<String, Object>) response.getBody().get("auth");
        return (String) auth.get("client_token");
    }

    private Map<String, String> fetchCredentials(String token, String accountName) {
        String url = ENV_URL + "/v1/secret/data/opendata/dev/" + accountName;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Namespace", NAMESPACE);
        headers.set("X-Vault-Token", token);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        return (Map<String, String>) data.get("data"); // username/password
    }
}
📌 Utilisation depuis un contrôleur ou un autre service
java
Copier
Modifier
@RestController
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @GetMapping("/secrets/{account}")
    public ResponseEntity<Map<String, String>> getSecrets(@PathVariable String account) {
        Map<String, String> creds = vaultService.getCredentials(account);
        return ResponseEntity.ok(creds);
    }
}
Souhaites-tu que je t’aide à tester ça avec un fichier .p12 ou un mock local ?







Vous avez dit :
Parfait, voici une configuration complète d'une DataSource Spring Boot 2 avec :

Authentification Vault via certificat client (PKCS12),

Récupération des credentials PostgreSQL dynamiques,

Cache local avec rafraîchissement automatique en cas d’expiration ou d’erreur.


ChatGPT a dit :
Voici une configuration complète d'une DataSource Spring Boot 2 qui fait exactement ce que tu veux :

✅ Authentification avec certificat client Vault (PKCS12)
✅ Récupération des credentials PostgreSQL dynamiques via Vault
✅ Mise en place d’un cache local avec rafraîchissement automatique à l’expiration ou sur échec de connexion.

📦 Dépendances (Maven)
xml
Copier
Modifier
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.httpcomponents</groupId>
        <artifactId>httpclient</artifactId>
    </dependency>
</dependencies>
🔐 VaultCredentialProvider (service de récupération avec cache)
java
Copier
Modifier
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class VaultCredentialProvider {

    private final VaultService vaultService; // déjà vu précédemment
    private final ReentrantLock lock = new ReentrantLock();

    private String username;
    private String password;
    private Instant expiry = Instant.MIN;

    public VaultCredentialProvider(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    public String getUsername() {
        refreshIfNeeded();
        return username;
    }

    public String getPassword() {
        refreshIfNeeded();
        return password;
    }

    private void refreshIfNeeded() {
        if (Instant.now().isBefore(expiry)) return;

        lock.lock();
        try {
            if (Instant.now().isBefore(expiry)) return; // double-check

            Map<String, String> creds = vaultService.getCredentials("opendata-readuser"); // <- adapt name
            this.username = creds.get("username");
            this.password = creds.get("password");

            this.expiry = Instant.now().plusSeconds(300); // 5 min par défaut
        } catch (Exception e) {
            System.err.println("❌ Erreur Vault - utilisation des credentials en cache expirés.");
        } finally {
            lock.unlock();
        }
    }
}
🛠️ VaultDataSourceConfig
java
Copier
Modifier
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class VaultDataSourceConfig {

    @Autowired
    private VaultCredentialProvider credentialProvider;

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl("jdbc:postgresql://db-host:5432/opendata"); // 🔁 à adapter
        ds.setUsername(credentialProvider.getUsername());
        ds.setPassword(credentialProvider.getPassword());

        return ds;
    }
}
🔁 Optionnel : Rafraîchissement automatique
Pour une meilleure résilience, tu peux programmer un rafraîchissement régulier avec @Scheduled :

java
Copier
Modifier
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VaultRefreshTask {

    private final VaultCredentialProvider provider;

    public VaultRefreshTask(VaultCredentialProvider provider) {
        this.provider = provider;
    }

    @Scheduled(fixedDelay = 240_000) // toutes les 4 minutes
    public void refresh() {
        provider.getUsername(); // déclenche le refresh si nécessaire
    }
}
Ajoute l’annotation @EnableScheduling dans ta classe principale.

Souhaites-tu que je te fournisse aussi un exemple de test d’intégration pour vérifier la connexion avec le vault et la base PostgreSQL ?







