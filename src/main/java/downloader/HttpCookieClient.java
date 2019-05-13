package downloader;

import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.Closeable;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

public class HttpCookieClient
        implements AutoCloseable, Closeable {

    private HttpClientContext clientContext;
    private CloseableHttpClient httpClient;

    public HttpCookieClient(int timeout, boolean ignoreSsl) throws IOException {

        clientContext = HttpClientContext.create();

        Registry<CookieSpecProvider> registry = RegistryBuilder.<CookieSpecProvider>create()
                .register("easy", new EasyCookieSpecProvider()).build();
        clientContext.setCookieSpecRegistry(registry);

        RequestConfig requestConfig = RequestConfig.custom()
                .setCookieSpec("easy")
                .setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();
        clientContext.setRequestConfig(requestConfig);

        CookieStore cookieStore = new BasicCookieStore();
        clientContext.setCookieStore(cookieStore);

        if (ignoreSsl) {
            try {
                SSLContext sslContext = SSLContextBuilder
                        .create()
                        .loadTrustMaterial(new EasySslSpec())
                        .build();
                HostnameVerifier allowAllHosts = new NoopHostnameVerifier();
                SSLConnectionSocketFactory connectionFactory = new SSLConnectionSocketFactory(sslContext, allowAllHosts);
                httpClient = HttpClients.custom()
                        .setSSLSocketFactory(connectionFactory)
                        .build();
            } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException err) {
                throw new IOException("unable to create api client", err);
            }
        } else {
            httpClient = HttpClients.createDefault();
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
