package com.bnpparibas.certis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.annotation.PostConstruct;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

@Configuration
@Profile("dev") // ⚠️ active uniquement si spring.profiles.active=dev
public class SslBypassConfig {

    @Value("${ssl.bypass.enabled:false}")
    private boolean bypassEnabled;

    @Value("${ssl.bypass.cert-path:}")
    private String certPath;

    @PostConstruct
    public void init() throws Exception {
        if (!bypassEnabled) return;

        if (certPath != null && !certPath.isEmpty()) {
            trustCustomCert(certPath);
        } else {
            disableCertificateValidation(); // fallback : tout accepter
        }
    }

    private void trustCustomCert(String certPath) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        FileInputStream fis = new FileInputStream(certPath);
        X509Certificate caCert = (X509Certificate) cf.generateCertificate(fis);

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("custom-cert", caCert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        System.out.println("✅ Certificat custom chargé : " + certPath);
    }

    private void disableCertificateValidation() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        System.out.println("⚠️ Toutes les vérifications SSL sont désactivées !");
    }
}
/////////////////////////////////////////
# Active le contournement SSL
ssl.bypass.enabled=true

# Facultatif : spécifie le chemin vers le certificat à faire confiance
ssl.bypass.cert-path=C:/certs/artifactory.crt
////////////////////////////////////
echo | openssl s_client -connect artifactory.monentreprise.com:443 -showcerts \
  | openssl x509 -outform PEM > artifactory.crt
