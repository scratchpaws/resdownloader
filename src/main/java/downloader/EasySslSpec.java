package downloader;

import org.apache.http.conn.ssl.TrustStrategy;

import java.security.cert.X509Certificate;

public class EasySslSpec
        implements TrustStrategy {

    @Override
    public boolean isTrusted(X509Certificate[] x509Certificates, String s) {
        return true;
    }
}
