package downloader;

import org.apache.http.Header;
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
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HttpCookieClient
        implements AutoCloseable, Closeable {

    private static final Logger log = LogManager.getLogger(HttpCookieClient.class.getSimpleName());
    private final HttpClientContext clientContext;
    private final CloseableHttpClient httpClient;

    HttpCookieClient(int timeout, boolean ignoreSsl) {

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
                        .setDefaultHeaders(buildDefaultHeaders())
                        .setSSLSocketFactory(connectionFactory)
                        .build();
            } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException err) {
                throw new RuntimeException("unable to create api client", err);
            }
        } else {
            httpClient = HttpClients.custom()
                    .setDefaultHeaders(buildDefaultHeaders())
                    .build();
        }
    }

    int download(URI inputUrl, Path tempFile, Path outputFile) {
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
                try (OutputStream bufOut = Files.newOutputStream(tempFile)) {
                    httpEntity.writeTo(bufOut);
                }
                log.info("Wrote OK");
                return code;
            }
            log.warn("Response code is " + code + ": " + httpResponse.getStatusLine().getReasonPhrase());
            return code;
        } catch (IOException err) {
            log.warn("Unable to download file: " + err.getMessage());
            return -1;
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    @NotNull
    private Collection<Header> buildDefaultHeaders() {
        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.74 Safari/537.36"));
        headers.add(new BasicHeader("DNT", "1"));
        headers.add(new BasicHeader("Accept-Language","ru,en-US;q=0.9,en;q=0.8,ru-RU;q=0.7"));
        return headers;
    }
}
