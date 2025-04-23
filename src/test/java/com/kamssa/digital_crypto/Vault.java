1. VaultConfig.java – Configuration en Java pur
java
Copier
Modifier
@Configuration
public class VaultConfig {

    @Bean
    public VaultTemplate vaultTemplate() {
        VaultEndpoint endpoint = VaultEndpoint.create("localhost", 8200); // Vault local
        ClientAuthentication clientAuth = new TokenAuthentication("s.XXXXXXX"); // Ton token Vault

        return new VaultTemplate(endpoint, clientAuth);
    }

    @Bean
    public DatabaseCredentials databaseCredentials(VaultTemplate vaultTemplate) {
        VaultResponse response = vaultTemplate.read("secret/myapp");
        Map<String, Object> data = response.getData();

        String username = (String) data.get("db.username");
        String password = (String) data.get("db.password");

        return new DatabaseCredentials(username, password);
    }
}
2. DatabaseCredentials.java – Classe pour encapsuler les credentials
java
Copier
Modifier
public class DatabaseCredentials {
    private final String username;
    private final String password;

    public DatabaseCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // Getters
    public String getUsername() { return username; }
    public String getPassword() { return password; }
}
3. Utilisation dans un contrôleur
java
Copier
Modifier
@RestController
public class VaultDemoController {

    private final DatabaseCredentials credentials;

    public VaultDemoController(DatabaseCredentials credentials) {
        this.credentials = credentials;
    }

    @GetMapping("/credentials")
    public Map<String, String> getCredentials() {
        Map<String, String> map = new HashMap<>();
        map.put("username", credentials.getUsername());
        map.put("password", credentials.getPassword());
        return map;
    }
}