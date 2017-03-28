package gcum.gcumfisher.connection;

import android.content.res.Resources;
import android.support.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import gcum.gcumfisher.R;

class SSL {
    @NonNull
    static SSLSocketFactory getSSLSocketFactory(@NonNull Resources resources, @NonNull String url) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, IOException, CertificateException {
        final CertificateFactory cf = CertificateFactory.getInstance("X.509");

        Certificate ca;
        try (InputStream caInput = new BufferedInputStream(resources.openRawResource(url.contains("parisestunparking") ? R.raw.certificate_peup : R.raw.certificate))) {
            ca = cf.generateCertificate(caInput);
        }

        final String keyStoreType = KeyStore.getDefaultType();
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", ca);

        final String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
        tmf.init(keyStore);

        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);

        return context.getSocketFactory();
    }
}
