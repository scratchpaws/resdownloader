package downloader;

import org.apache.http.HttpEntity;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
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
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

public class HttpCookieClient
        implements AutoCloseable, Closeable {

    private HttpClientContext clientContext;
    private CloseableHttpClient httpClient;
    private static final Logger log = Logger.getLogger("HTTP CLIENT");

    public HttpCookieClient(int timeout, boolean ignoreSsl) {

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
                throw new RuntimeException("unable to create api client", err);
            }
        } else {
            httpClient = HttpClients.createDefault();
        }
    }

    public int download(URI inputUrl, Path outputFile) throws IOException {
        HttpGet getRequest = new HttpGet(inputUrl);
        log.info("Querying " + inputUrl);
        try (CloseableHttpResponse httpResponse = httpClient.execute(getRequest, clientContext)) {
            int code = httpResponse.getStatusLine().getStatusCode();
            if (code == 200) {
                log.info("HTTP OK");
                HttpEntity httpEntity = httpResponse.getEntity();
                if (httpEntity == null)
                    throw new IOException("Empty response, url: " + inputUrl);
                if (Files.exists(outputFile)) {
                    long fileSize = Files.size(outputFile);
                    long contentLength = httpEntity.getContentLength();
                    if (fileSize == contentLength) {
                        log.info("File already exists, size match");
                        return code;
                    }
                }
                log.info("Writing to file");
                try (OutputStream bufOut = Files.newOutputStream(outputFile)) {
                    httpEntity.writeTo(bufOut);
                }
                log.info("Wrote OK");
                return code;
            }
            log.warning("Response code is " + code + ": " + httpResponse.getStatusLine().getReasonPhrase());
            return code;
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
