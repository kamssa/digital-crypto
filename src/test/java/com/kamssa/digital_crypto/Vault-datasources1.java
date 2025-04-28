import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class DataSourceConfig {

    @Autowired
    private VaultService vaultService;

    @Autowired
    private CredentialCache credentialsCache;

    // Méthode principale pour configurer la DataSource
    @Bean
    public DataSource dataSource() {
        DataSource dataSource = null;
        int maxAttempts = 5; // Nombre maximum de tentatives
        int attempt = 0; // Compteur de tentatives

        while (dataSource == null && attempt < maxAttempts) {
            try {
                // Récupérer les credentials
                String credentials = credentialsCache.getCredentials(vaultService);
                
                if (credentials == null) {
                    throw new RuntimeException("Impossible de récupérer des credentials valides.");
                }

                dataSource = new DriverManagerDataSource();
                dataSource.setDriverClassName("org.postgresql.Driver");
                dataSource.setUrl("jdbc:postgresql://localhost:5432/votre_nom_de_base");
                dataSource.setUsername(credentials.getUsername()); // Adapter au format de vos credentials
                dataSource.setPassword(credentials.getPassword()); // Adapter au format de vos credentials

                return dataSource;

            } catch (Exception e) {
                attempt++;
                System.err.println("Erreur lors de la configuration de la DataSource: " + e.getMessage());

                // Rafraîchir les credentials
                System.out.println("Tentative " + attempt + " échouée. Rafraîchissement des credentials...");
                refreshCredentials();

                // Optionnel : Attendre quelques secondes avant de réessayer
                try {
                    Thread.sleep(2000); // Attendre 2 secondes
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        throw new RuntimeException("Echec de la création de la DataSource après " + maxAttempts + " tentatives.");
    }

    // Méthode pour rafraîchir les credentials
    private void refreshCredentials() {
        credentialsCache.setCredentials(vaultService.getPostgresCredentials());
    }
}
Explications des Modifications
MéthoderefreshCredentials :

Cette méthode encapsule la logique de rafraîchissement des informations d'identification, ce qui rend le code plus propre et plus lisible.
Elle appelle vaultService.getPostgresCredentials()et stocke les nouvelles références dans le CredentialCache.
Utilisation derefreshCredentials : Dans le bloc catch, au lieu d'appeler directement la méthode de récupération des identifiants, nous appelons la nouvelle méthode refreshCredentials(), ce qui améliore la clarté.

Avantages
Lisibilité : Le code est plus facile à lire et à maintenir, car la logique de rafraîchissement est séparée.

Réutilisabilité : Si vous avez besoin de rafraîchir les informations d'identification à d'autres endroits de votre code, vous pouvez facilement réutiliser la méthode refreshCredentials.

Cette approche permet une meilleure organisation du code tout en conservant l'objectivité de la logique de rafraîchissement des informations d'identification.



Photocopieuse
Régénérer

