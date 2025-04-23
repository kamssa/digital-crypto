🔐 1. Générer une autorité de certification (CA) locale (si nécessaire)
Si tu n’as pas encore d’AC, commence par en générer une :

bash
Copier
Modifier
openssl genrsa -out my-ca.key 4096
openssl req -x509 -new -nodes -key my-ca.key -sha256 -days 3650 -out my-ca.crt \
  -subj "/C=FR/ST=Ile-de-France/L=Paris/O=MonOrg/OU=Dev/CN=MonCA"
👤 2. Générer un certificat client
bash
Copier
Modifier
openssl genrsa -out client.key 2048

openssl req -new -key client.key -out client.csr \
  -subj "/C=FR/ST=Ile-de-France/L=Paris/O=MonOrg/OU=Dev/CN=client1"

openssl x509 -req -in client.csr -CA my-ca.crt -CAkey my-ca.key -CAcreateserial \
  -out client.crt -days 365 -sha256
Tu obtiens client.key, client.crt (et éventuellement my-ca.crt).

🔧 3. Activer et configurer l’authentification client cert sur Vault
Sur Vault :

bash
Copier
Modifier
vault auth enable cert
Puis configure un utilisateur certifié :

bash
Copier
Modifier
vault write auth/cert/certs/client1 \
  display_name=client1 \
  policies=default \
  certificate=@client.crt
⚠️ Le champ CN du certificat doit correspondre à client1 ici, car Vault le lie à l’identité.

🧪 4. Tester l’authentification avec curl ou vault login
Avec curl
bash
Copier
Modifier
curl --cert client.crt --key client.key https://vault.example.com:8200/v1/auth/cert/login
Avec vault login
bash
Copier
Modifier
VAULT_ADDR=https://vault.example.com:8200
vault login -method=cert client_cert=client.crt client_key=client.key
🛡️ 5. (Facultatif) Vérifie que Vault a bien enregistré la connexion
bash
Copier
Modifier
vault token lookup
Tu veux que je t’aide à automatiser ça dans un script Bash ou à l’intégrer côté Spring Boot aussi ?








Vous avez dit :
Tu veux que je t’aide à automatiser ça dans un script Bash ou à l’intégrer côté Spring Boot aussi ?


ChatGPT a dit :
Top, merci pour la relance ! Dis-moi ce que tu préfères :

Un script Bash complet pour générer la CA, le certificat client, configurer Vault et tester la connexion ?

Une intégration côté Spring Boot avec un appel REST sécurisé vers Vault avec le certificat client ?

Tu peux aussi me dire si tu veux les deux ! 😊

Si tu veux un aperçu rapide de chaque option, voilà :
✅ Script Bash rapide (extrait)
bash
Copier
Modifier
#!/bin/bash

# Génération de la CA
openssl genrsa -out my-ca.key 4096
openssl req -x509 -new -nodes -key my-ca.key -sha256 -days 3650 -out my-ca.crt \
  -subj "/C=FR/ST=IDF/L=Paris/O=Demo/OU=Dev/CN=MyLocalCA"

# Génération du client
openssl genrsa -out client.key 2048
openssl req -new -key client.key -out client.csr \
  -subj "/C=FR/ST=IDF/L=Paris/O=Demo/OU=Dev/CN=client1"
openssl x509 -req -in client.csr -CA my-ca.crt -CAkey my-ca.key -CAcreateserial \
  -out client.crt -days 365 -sha256

# Enable & config Vault cert auth
vault auth enable cert
vault write auth/cert/certs/client1 display_name=client1 policies=default certificate=@client.crt

# Test login
curl --cert client.crt --key client.key https://vault.example.com:8200/v1/auth/cert/login
☕ Spring Boot côté client
Avec RestTemplate ou WebClient en configurant le certificat client dans un KeyStore :

java
Copier
Modifier
SSLContext sslContext = SSLContextBuilder.create()
    .loadKeyMaterial(keyStore, "keystore-password".toCharArray())
    .loadTrustMaterial(trustStore, new TrustSelfSignedStrategy())
    .build();

HttpClient httpClient = HttpClientBuilder.create()
    .setSSLContext(sslContext)
    .build();

RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
Tu veux que je te prépare le script complet ou l’intégration Spring Boot (ou les deux) ?







Vous avez dit :
les deux


ChatGPT a dit :
Éléments mémorisés mis à jour
Parfait, on y va pour les deux ! 👇

🧰 1. Script Bash complet : création CA, certificat client et config Vault
Voici un script complet :

bash
Copier
Modifier
#!/bin/bash

# --- Configuration ---
CA_KEY="my-ca.key"
CA_CERT="my-ca.crt"
CLIENT_KEY="client.key"
CLIENT_CSR="client.csr"
CLIENT_CERT="client.crt"
VAULT_ADDR="https://vault.example.com:8200"
VAULT_ROLE="client1"
VAULT_POLICY="default"

# --- Génération CA ---
openssl genrsa -out $CA_KEY 4096
openssl req -x509 -new -nodes -key $CA_KEY -sha256 -days 3650 -out $CA_CERT \
  -subj "/C=FR/ST=IDF/L=Paris/O=Demo/OU=Dev/CN=MyLocalCA"

# --- Génération certificat client ---
openssl genrsa -out $CLIENT_KEY 2048
openssl req -new -key $CLIENT_KEY -out $CLIENT_CSR \
  -subj "/C=FR/ST=IDF/L=Paris/O=Demo/OU=Dev/CN=$VAULT_ROLE"
openssl x509 -req -in $CLIENT_CSR -CA $CA_CERT -CAkey $CA_KEY -CAcreateserial \
  -out $CLIENT_CERT -days 365 -sha256

# --- Config Vault ---
vault auth enable cert || echo "Auth method cert déjà activée"
vault write auth/cert/certs/$VAULT_ROLE \
  display_name=$VAULT_ROLE \
  policies=$VAULT_POLICY \
  certificate=@"$CLIENT_CERT"

# --- Test de connexion ---
curl --cert $CLIENT_CERT --key $CLIENT_KEY "$VAULT_ADDR/v1/auth/cert/login"
💡 Tu peux aussi ajouter un vault policy write default ./default-policy.hcl si tu veux injecter une politique personnalisée.

☕️ 2. Intégration Spring Boot avec RestTemplate (certificat client)
Étapes :
a. Convertir en PKCS12
Vault nécessite un KeyStore, donc :

bash
Copier
Modifier
openssl pkcs12 -export \
  -inkey client.key \
  -in client.crt \
  -certfile my-ca.crt \
  -out client-keystore.p12 \
  -name client1
b. Ajouter le keystore dans src/main/resources
c. Configuration dans application.properties
properties
Copier
Modifier
vault.url=https://vault.example.com:8200
vault.keystore=classpath:client-keystore.p12
vault.keystore-password=changeit
d. Code Java (RestTemplate avec cert client)
java
Copier
Modifier
@Configuration
public class VaultClientConfig {

    @Value("${vault.keystore}")
    private Resource keystore;

    @Value("${vault.keystore-password}")
    private String keystorePassword;

    @Bean
    public RestTemplate restTemplate() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(keystore.getInputStream(), keystorePassword.toCharArray());

        SSLContext sslContext = SSLContexts.custom()
                .loadKeyMaterial(keyStore, keystorePassword.toCharArray())
                .build();

        HttpClient client = HttpClients.custom()
                .setSSLContext(sslContext)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(client);

        return new RestTemplate(factory);
    }
}
e. Utilisation
java
Copier
Modifier
@Component
public class VaultLoginClient {

    @Value("${vault.url}")
    private String vaultUrl;

    private final RestTemplate restTemplate;

    public VaultLoginClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void loginWithCert() {
        String url = vaultUrl + "/v1/auth/cert/login";
        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
        System.out.println("Token reçu : " + response.getBody());
    }
}
Tu veux que je te prépare un repo de départ avec tout ça (script + projet Spring Boot minimal), ou tu préfères copier/adapter côté projet existant ?







Vous avez dit :
script + projet Spring Boot minima


ChatGPT a dit :
​Parfait, voici un projet complet prêt à l'emploi, comprenant :​

Un script Bash pour générer une autorité de certification (CA), un certificat client, configurer Vault et créer un keystore PKCS12.

Un projet Spring Boot minimal avec un RestTemplate configuré pour l'authentification via certificat client.​

🗂️ Structure du projet
css
Copier
Modifier
vault-client-auth/
├── certs/
│   ├── generate-certs.sh
│   ├── my-ca.crt
│   ├── my-ca.key
│   ├── client.crt
│   ├── client.key
│   └── client-keystore.p12
├── springboot-client/
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/example/vaultclient/
│           │       ├── VaultClientApplication.java
│           │       ├── config/
│           │       │   └── VaultClientConfig.java
│           │       └── service/
│           │           └── VaultLoginService.java
│           └── resources/
│               ├── application.properties
│               └── client-keystore.p12
🛠️ 1. Script Bash : certs/generate-certs.sh
bash
Copier
Modifier
#!/bin/bash

set -e

# Répertoires
CERTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$CERTS_DIR"

# Fichiers
CA_KEY="my-ca.key"
CA_CERT="my-ca.crt"
CLIENT_KEY="client.key"
CLIENT_CSR="client.csr"
CLIENT_CERT="client.crt"
CLIENT_P12="client-keystore.p12"
VAULT_ROLE="client1"
VAULT_POLICY="default"
VAULT_ADDR="https://vault.example.com:8200"

# Génération de la CA
openssl genrsa -out $CA_KEY 4096
openssl req -x509 -new -nodes -key $CA_KEY -sha256 -days 3650 -out $CA_CERT \
  -subj "/C=FR/ST=IDF/L=Paris/O=Demo/OU=Dev/CN=MyLocalCA"

# Génération du certificat client
openssl genrsa -out $CLIENT_KEY 2048
openssl req -new -key $CLIENT_KEY -out $CLIENT_CSR \
  -subj "/C=FR/ST=IDF/L=Paris/O=Demo/OU=Dev/CN=$VAULT_ROLE"
openssl x509 -req -in $CLIENT_CSR -CA $CA_CERT -CAkey $CA_KEY -CAcreateserial \
  -out $CLIENT_CERT -days 365 -sha256

# Création du keystore PKCS12
openssl pkcs12 -export \
  -inkey $CLIENT_KEY \
  -in $CLIENT_CERT \
  -certfile $CA_CERT \
  -out $CLIENT_P12 \
  -name $VAULT_ROLE \
  -passout pass:changeit

# Configuration de Vault
vault auth enable cert || echo "Méthode d'authentification cert déjà activée"
vault write auth/cert/certs/$VAULT_ROLE \
  display_name=$VAULT_ROLE \
  policies=$VAULT_POLICY \
  certificate=@"$CLIENT_CERT"

# Test de connexion
curl --cert $CLIENT_CERT --key $CLIENT_KEY "$VAULT_ADDR/v1/auth/cert/login"
💡 Assurez-vous que le binaire vault est installé et que Vault est accessible à l'adresse spécifiée.​

☕ 2. Projet Spring Boot minimal
📄 pom.xml
xml
Copier
Modifier
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>vault-client</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <name>vault-client</name>
  <description>Client Vault avec authentification par certificat</description>
  <properties>
    <java.version>17</java.version>
    <spring.boot.version>3.4.3</spring.boot.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.apache.httpcomponents.client5</groupId>
      <artifactId>httpclient5</artifactId>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
📄 application.properties
properties
Copier
Modifier
vault.url=https://vault.example.com:8200
vault.keystore=classpath:client-keystore.p12
vault.keystore-password=changeit
📄 VaultClientConfig.java
java
Copier
Modifier
package com.example.vaultclient.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustSelfSignedStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyStore;

@Configuration
public class VaultClientConfig {

    @Value("${vault.keystore}")
    private Resource keystore;

    @Value("${vault.keystore-password}")
    private String keystorePassword;

    @Bean
    public RestTemplate restTemplate() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(keystore.getInputStream(), keystorePassword.toCharArray());

        SSLContext sslContext = SSLContextBuilder.create()
                .loadKeyMaterial(keyStore, keystorePassword.toCharArray())
                .loadTrustMaterial(new TrustSelfSignedStrategy())
                .build();

        HttpClient httpClient = HttpClients.custom()
                .setSSLContext(sslContext)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(factory);
    }
}
📄 VaultLoginService.java
java
Copier
Modifier
package com.example.vaultclient.service;

import org
::contentReference[oaicite:12]{index=12}
 






