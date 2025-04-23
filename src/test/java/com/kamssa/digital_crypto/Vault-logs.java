✅ Méthode recommandée : wrapper de VaultTemplate + logging HTTP
1. 🔄 Création d’un VaultTemplate personnalisé
java
Copier
Modifier
@Component
public class TracingVaultTemplate extends VaultTemplate {

    public TracingVaultTemplate(VaultEndpoint vaultEndpoint,
                                ClientHttpRequestFactory requestFactory,
                                SessionManager sessionManager) {
        super(vaultEndpoint, requestFactory, sessionManager);
    }

    @Override
    public VaultResponse read(String path) {
        System.out.println("📥 Vault READ -> Path: " + path);
        return super.read(path);
    }

    @Override
    public void write(String path, @Nullable Object body) {
        System.out.println("📤 Vault WRITE -> Path: " + path + ", Body: " + body);
        super.write(path, body);
    }

    @Override
    public void delete(String path) {
        System.out.println("❌ Vault DELETE -> Path: " + path);
        super.delete(path);
    }
}
Tu peux même ajouter une option pour afficher la stack trace (à activer si besoin pour debug).

2. 🔌 Forcer Spring à utiliser ton VaultTemplate
Dans ta @Configuration, expose ton bean :

java
Copier
Modifier
@Configuration
public class VaultConfig {

    @Bean
    public VaultTemplate vaultTemplate(VaultEndpoint endpoint,
                                       ClientHttpRequestFactory factory,
                                       SessionManager sessionManager) {
        return new TracingVaultTemplate(endpoint, factory, sessionManager);
    }
}
Spring utilisera ce bean partout (autowiring normal).

3. 🛠️ Intercepteur HTTP (optionnel mais puissant)
Tu peux aussi voir tous les appels HTTP bas niveau :

java
Copier
Modifier
@Bean
public ClientHttpRequestInterceptor vaultLoggingInterceptor() {
    return (request, body, execution) -> {
        System.out.println("🌐 Vault HTTP Request: " + request.getMethod() + " " + request.getURI());
        return execution.execute(request, body);
    };
}
Et tu l’ajoutes dans le RestTemplate que tu passes à VaultTemplate.

4. 📝 Bonus : logs en fichier
Tu peux diriger ces logs vers un fichier spécifique si tu préfères :

properties
Copier
Modifier
logging.level.org.springframework.vault=DEBUG
logging.file.name=logs/vault-access.log
🔁 Résultat
À chaque interaction avec Vault, tu auras dans la console/log :

pgsql
Copier
Modifier
📥 Vault READ -> Path: secret/myapp
📤 Vault WRITE -> Path: secret/myapp, Body: {db.username=myuser, db.password=pass123}
Tu veux que je t’ajoute :

Une timestamp ou mesure de temps par appel ?

Un wrapper plus complet avec metrics Prometheus ?

Un endpoint REST pour voir les derniers appels faits à Vault ?