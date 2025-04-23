Voici un exemple simple d'une application Spring Boot qui utilise HashiCorp Vault pour récupérer un nom d'utilisateur et un mot de passe à la place d'utiliser le fichier application.properties classique.

🧰 Prérequis
Spring Boot 2+

Java 8+

Vault configuré (et tournant en local ou distant)

Le Spring Cloud Vault Config

🏗️ Structure du projet
Voici les points clés :

Vault est utilisé pour stocker db.username et db.password.

Spring Boot récupère automatiquement les secrets depuis Vault.

1. 📦 Dépendances Maven (pom.xml)
xml
Copier
Modifier
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-vault-config</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>Hoxton.SR12</version> <!-- ou autre version compatible -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
2. 🔐 Configuration Vault dans application.properties
properties
Copier
Modifier
spring.application.name=myapp
spring.cloud.vault.uri=http://localhost:8200
spring.cloud.vault.token=s.XXXXXXX   # Token d'accès à Vault
spring.cloud.vault.kv.enabled=true
spring.cloud.vault.kv.backend=secret
spring.cloud.vault.kv.application-name=myapp
3. 🔑 Stocker les secrets dans Vault
Exécute cette commande (si tu utilises le CLI de Vault) :

bash
Copier
Modifier
vault kv put secret/myapp db.username=admin db.password=supersecret
4. ✅ Utilisation dans le code
Exemple avec un @RestController :
java
Copier
Modifier
@RestController
public class VaultDemoController {

    @Value("${db.username}")
    private String username;

    @Value("${db.password}")
    private String password;

    @GetMapping("/credentials")
    public Map<String, String> getCredentials() {
        Map<String, String> creds = new HashMap<>();
        creds.put("username", username);
        creds.put("password", password);
        return creds;
    }
}
🔍 Résultat
L'application démarre sans que db.username et db.password soient présents dans application.properties. Ils sont injectés directement depuis Vault 🎉