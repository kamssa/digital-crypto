🧱 Étapes
Récupérer les credentials Vault → ✅ déjà fait

Créer dynamiquement un DataSource

Fournir ce DataSource à Spring pour la connexion à la BDD

1. Classe VaultCredentialCache (inchangée si déjà mise en place)
Tu peux réutiliser celle avec @Scheduled qu'on vient de faire, ou une version simple qui lit une fois.

2. Classe DatabaseConfig.java – Création du DataSource
java
Copier
Modifier
@Configuration
public class DatabaseConfig {

    private final VaultCredentialCache vaultCredentialCache;

    public DatabaseConfig(VaultCredentialCache vaultCredentialCache) {
        this.vaultCredentialCache = vaultCredentialCache;
    }

    @Bean
    public DataSource dataSource() {
        DatabaseCredentials creds = vaultCredentialCache.getCredentials();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/your_db"); // adapte ici
        config.setUsername(creds.getUsername());
        config.setPassword(creds.getPassword());
        config.setDriverClassName("org.postgresql.Driver");

        return new HikariDataSource(config);
    }
}
⚠️ Assure-toi d’avoir la dépendance PostgreSQL dans ton pom.xml :

xml
Copier
Modifier
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.2</version>
</dependency>
3. Configuration minimale (si nécessaire)
Tu peux enlever tout spring.datasource.* de application.properties s'il existe.