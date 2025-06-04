import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;

public class MavenDependencyDownloader {

    public static void main(String[] args) {
        try {
            // Chemin vers votre certificat .crt
            String certPath = "chemin/vers/certificat_maven.crt";

            // Charger le certificat dans un SSLContext personnalisé
            SSLContext sslContext = createSSLContextWithCustomCert(certPath);

            // Appliquer le contexte SSL par défaut
            SSLContext.setDefault(sslContext);

            // URL du fichier à télécharger (exemple Maven Central)
            String urlStr = "https://repo.maven.apache.org/maven2/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar";

            // Ouvrir la connexion
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Vérifier la réponse
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream in = connection.getInputStream();

                // Enregistrer le fichier localement
                FileOutputStream fos = new FileOutputStream("commons-lang3-3.12.0.jar");
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.close();
                in.close();
                System.out.println("Fichier téléchargé avec succès !");
            } else {
                System.out.println("Erreur : " + connection.getResponseCode());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static SSLContext createSSLContextWithCustomCert(String certPath) throws Exception {
        // Charger le certificat
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream is = new FileInputStream(certPath)) {
            X509Certificate cert = (X509Certificate) cf.generateCertificate(is);

            // Créer un keystore en mémoire
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null); // Keystore vide
            ks.setCertificateEntry("myCert", cert);

            // Init TrustManagerFactory avec ce keystore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            // Créer SSLContext avec ce TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
            return sslContext;
        }
    }
}
