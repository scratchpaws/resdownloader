package downloader;

import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class ResourceProcessor
        implements Closeable, AutoCloseable {

    private static final HashMap<Path, ResourceProcessor> INSTANCES = new HashMap<>();
    private static final String RESOURCES_PATH_NAME = "resources";
    private static final Logger log = Logger.getLogger("RES");

    private final Path baseLocation;
    private final HashMap<String, String> converted = new HashMap<>();
    private final List<String> failed = new ArrayList<>();
    private final HttpCookieClient httpClient;
    private final int tries;

    private ResourceProcessor(Path baseLocation, int tries, int timeout) {
        this.baseLocation = baseLocation;
        this.httpClient = new HttpCookieClient(timeout, true);
        this.tries = tries;
    }

    public static ResourceProcessor forDocument(Document document, int tries, int timeout) {
        Path documentPath = Paths.get(document.location());
        Path baseLocation = documentPath.resolveSibling(RESOURCES_PATH_NAME);

        if (INSTANCES.containsKey(baseLocation)) {
            return INSTANCES.get(baseLocation);
        } else {
            ResourceProcessor rp = new ResourceProcessor(baseLocation, tries, timeout);
            INSTANCES.put(baseLocation, rp);
            return rp;
        }
    }

    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (IOException err) {
            throw new RuntimeException("Unable to close http client: " + err.getMessage(), err);
        }
    }

    @Nullable
    public String replaceToLocal(String url) {

        if (converted.containsKey(url)) {
            log.info("Url already downloaded");
            return converted.get(url);
        }

        if (failed.contains(url)) {
            log.info("Url already failed to download");
            return null;
        }

        URI remote = fromStringSilent(url);
        String subPath = (remote.getHost() != null ? remote.getHost() : "")
                + (remote.getPath() != null ? remote.getPath() : "")
                .replaceAll("[^a-zA-Z0-9а-яА-Я%_.\\-\\\\/]", "_");
        Path local = baseLocation.resolve(subPath);
        createDirectoriesSilent(local.getParent());


        boolean success = false;
        for (int i = 1; i <= tries; i++) {
            try {
                int retCode = httpClient.download(remote, local);
                if (retCode == 200) {
                    success = true;
                    break;
                }
            } catch (IOException err) {
                log.warning("Unable to download file: " + err.getMessage());
            }
        }

        if (success) {
            String escaped = StringEscapeUtils.escapeHtml4(RESOURCES_PATH_NAME + "/" + subPath);
            converted.put(url, escaped);
            return escaped;
        } else {
            failed.add(url);
        }

        return null;
    }

    @NotNull
    @Contract("null -> fail")
    private URI fromStringSilent(String url) {
        if (url == null)
            throw new RuntimeException("Unable to parse null url");
        try {
            return new URI(url);
        } catch (URISyntaxException err) {
            throw new RuntimeException("Unable to parse url " + url + ": " + err.getMessage(), err);
        }
    }

    private void createDirectoriesSilent(Path dir) {
        try {
            if (!Files.exists(dir))
                Files.createDirectories(dir);
        } catch (IOException err) {
            throw new RuntimeException("Unable to create directory " + dir + " for save file.");
        }
    }
}
