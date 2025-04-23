✅ 1. Exemple de configuration Vault côté serveur
Ces commandes se font sur le serveur Vault (ou via API)

bash
Copier
Modifier
# Activer l'authentification par certificat
vault auth enable cert

# Associer un certificat à une policy
vault write auth/cert/certs/my-client-cert \
    display_name="myapp" \
    policies="default" \
    certificate=@client-cert.pem
✅ 2. Config côté Java / Spring avec VaultTemplate
A. Créer un RestTemplate avec certificat client
java
Copier
Modifier
@Bean
public VaultTemplate vaultTemplate() throws Exception {
    SSLContext sslContext = buildSslContextWithClientCert();

    ClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(
        HttpClients.custom()
            .setSSLContext(sslContext)
            .build()
    );

    VaultEndpoint endpoint = VaultEndpoint.from(URI.create("https://vault.example.com:8200"));

    return new VaultTemplate(endpoint, requestFactory, new SimpleSessionManager());
}

private SSLContext buildSslContextWithClientCert() throws Exception {
    // Charger le keystore avec le certificat client (.p12 ou .jks)
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(new FileInputStream("client-cert.p12"), "keystore-password".toCharArray());

    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(keyStore, "keystore-password".toCharArray());

    // Charger le truststore si nécessaire (optionnel)
    KeyStore trustStore = KeyStore.getInstance("JKS");
    trustStore.load(new FileInputStream("truststore.jks"), "truststore-password".toCharArray());

    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
    tmf.init(trustStore);

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

    return sslContext;
}
Tu peux aussi tout faire avec des .pem si tu veux éviter les .p12

B. Authentifier l’app avec le cert
Une fois connecté avec le VaultTemplate, tu peux obtenir le token avec :

java
Copier
Modifier
VaultResponse response = vaultTemplate().write("auth/cert/login", null);
String clientToken = response.getAuth().getClientToken();
System.out.println("🔐 Vault client token: " + clientToken);
Et ensuite tu peux utiliser ce token dans un TokenAuthentication classique si tu veux :

java
Copier
Modifier
VaultTemplate template = new VaultTemplate(vaultEndpoint, requestFactory,
    new TokenAuthentication(clientToken));
🧠 Résumé
C’est plus sécurisé que token statique ou userpass

Utilise le TLS mutualisé (serveur ↔️ client cert)

Peut être combiné avec d’autres méthodes (ex: approle)
////////////////////////////////////// parti 2/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
⚙️ Exemple 3 : Auth avec certificat client (TLS) 🔐
Activer cert dans Vault :

bash
Copier
Modifier
vault auth enable cert
Config Spring :

properties
Copier
Modifier
spring.cloud.vault.authentication=cert
spring.cloud.vault.uri=https://vault.example.com:8200

# Chemins vers le certificat et la clé privée
spring.cloud.vault.ssl.key-store=classpath:client-cert.p12
spring.cloud.vault.ssl.key-store-password=changeit
spring.cloud.vault.ssl.key-store-type=PKCS12

spring.cloud.vault.ssl.trust-store=classpath:truststore.jks
spring.cloud.vault.ssl.trust-store-password=changeit
spring.cloud.vault.ssl.trust-store-type=JKS
✅ Tu n’as pas besoin de token ici — l’app se présente avec son certificat TLS, et Vault l’identifie directement.

Bonus : Lire un secret dans un bean
java
Copier
Modifier
@Component
@RefreshScope
public class MyVaultBean {

    @Value("${db.username}")
    private String username;

    @Value("${db.password}")
    private String password;
}
/////////////////////////////////// partie 3//////////////////////////
🧱 Étapes
➤ 1. Construire un VaultTemplate avec TLS client cert
java
Copier
Modifier
import org.springframework.vault.authentication.ClientCertificateAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.ClientOptions;

import java.io.File;
import java.net.URI;

public class VaultCertAuthExample {

    public VaultTemplate vaultTemplate() throws Exception {
        // Vault endpoint
        VaultEndpoint vaultEndpoint = VaultEndpoint.from(URI.create("https://vault.example.com:8200"));

        // Keystore (client cert)
        File keyStoreFile = new File("/path/to/client-cert.p12");
        char[] keyStorePassword = "keystore-pass".toCharArray();

        // Truststore (CA de Vault)
        File trustStoreFile = new File("/path/to/truststore.jks");
        char[] trustStorePassword = "truststore-pass".toCharArray();

        SslConfiguration sslConfig = SslConfiguration.builder()
            .keyStore(keyStoreFile, keyStorePassword)
            .trustStore(trustStoreFile, trustStorePassword)
            .build();

        // Auth via Cert (Vault fera auth/cert/login automatiquement)
        ClientCertificateAuthentication clientCertAuth =
            new ClientCertificateAuthentication(vaultEndpoint, sslConfig);

        // VaultTemplate avec auth + SSL config
        return new VaultTemplate(vaultEndpoint, clientCertAuth);
    }
}
✅ Exemple d’usage
java
Copier
Modifier
VaultTemplate vault = new VaultCertAuthExample().vaultTemplate();

// Lecture d’un secret
VaultResponse resp = vault.read("secret/myapp");
System.out.println("🔐 username: " + resp.getData().get("username"));
💡 Bonus : utiliser ce VaultTemplate dans Spring
Tu peux l’enregistrer comme un bean manuellement :

java
Copier
Modifier
@Configuration
public class VaultManualConfig {

    @Bean
    public VaultTemplate vaultTemplate() throws Exception {
        return new VaultCertAuthExample().vaultTemplate();
    }
}
📦 Fichiers requis
client-cert.p12 : certificat client + clé privée (PKCS12)

truststore.jks : store contenant la CA Vault
//////////////////////////////////partie 4 /////////////////////////////
Yes, voici un script Bash tout simple 🔧 pour générer un fichier .p12 (PKCS12) à partir de :

un certificat client .crt

une clé privée .key

(optionnel) un certificat de CA .crt (si besoin de la chaîne complète)

📝 Script : generate-p12.sh
bash
Copier
Modifier
#!/bin/bash

# === CONFIG ===
CERT="client-cert.crt"
KEY="client-key.key"
CA="ca-cert.crt"      # Optionnel
P12_OUT="client-cert.p12"
P12_PASS="changeit"

# === CHECK FILES ===
if [[ ! -f "$CERT" || ! -f "$KEY" ]]; then
  echo "❌ Certificat ou clé manquants. Vérifie $CERT et $KEY"
  exit 1
fi

# === GEN .p12 ===
echo "🔐 Génération de $P12_OUT à partir de $CERT et $KEY..."

if [[ -f "$CA" ]]; then
  openssl pkcs12 -export \
    -inkey "$KEY" \
    -in "$CERT" \
    -certfile "$CA" \
    -out "$P12_OUT" \
    -passout pass:"$P12_PASS"
else
  openssl pkcs12 -export \
    -inkey "$KEY" \
    -in "$CERT" \
    -out "$P12_OUT" \
    -passout pass:"$P12_PASS"
fi

if [[ $? -eq 0 ]]; then
  echo "✅ Fichier $P12_OUT généré avec succès (mot de passe : $P12_PASS)"
else
  echo "❌ Erreur pendant la génération"
fi
🔧 Utilisation
Place dans un dossier :

client-cert.crt (certificat signé par Vault ou ta CA)

client-key.key (clé privée associée)

(optionnel) ca-cert.crt (la chaîne intermédiaire ou root)

Lance :

bash
Copier
Modifier
chmod +x generate-p12.sh
./generate-p12.sh
🧪 Vérifier le .p12
bash
Copier
Modifier
keytool -list -v -keystore client-cert.p12 -storetype PKCS12 -storepass changeit
