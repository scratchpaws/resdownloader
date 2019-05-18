package downloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

public class ResourceProcessor
        implements Closeable, AutoCloseable {

    private static final String RESOURCES_PATH_NAME = "resources";
    private static final Logger log = Logger.getLogger("RES");
    private static final String STATE_FILE_NAME = "state.json";

    private final Path baseLocation;
    private final Path stateFilePath;
    private final StateData stateData;
    private final HttpCookieClient httpClient;
    private final int tries;

    private ResourceProcessor(Path baseLocation, int tries, int timeout) {
        this.baseLocation = baseLocation;
        this.stateFilePath = baseLocation.resolve(STATE_FILE_NAME);
        this.httpClient = new HttpCookieClient(timeout, true);
        this.tries = tries;
        createDirectoriesSilent(baseLocation);
        StateData tmp = new StateData();
        tmp.setConverted(new HashMap<>());
        tmp.setFailed(new ArrayList<>());
        if (Files.exists(stateFilePath)) {
            try (BufferedReader bufferedReader = Files.newBufferedReader(stateFilePath, StandardCharsets.UTF_8)) {
                ObjectMapper objectMapper = new ObjectMapper();
                tmp = objectMapper.readValue(bufferedReader, StateData.class);
                log.info("State file loaded successfully");
            } catch (IOException warn) {
                log.warning("Unable to load state file; " + warn.getMessage());
            }
        }
        this.stateData = tmp;
    }

    static ResourceProcessor forDocument(Document document, int tries, int timeout) {
        Path documentPath = Paths.get(document.location());
        Path baseLocation = documentPath.resolveSibling(RESOURCES_PATH_NAME);

        return new ResourceProcessor(baseLocation, tries, timeout);
    }

    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (IOException err) {
            log.severe("Unable to close http client: " + err.getMessage());
        }
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(stateFilePath, StandardCharsets.UTF_8)) {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(bufferedWriter, stateData);
            log.info("State file saved successfully");
        } catch (IOException warn) {
            log.warning("Unable to save state file: " + warn.getMessage());
        }
    }

    @Nullable
    String replaceToLocal(String url) {
        if (stateData.getConverted().containsKey(url)) {
            log.info("Url already downloaded");
            return stateData.getConverted().get(url);
        }

        if (stateData.getFailed().contains(url)) {
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
            String escaped = //StringEscapeUtils.escapeHtml4(
                    RESOURCES_PATH_NAME + "/" + subPath;//);
            stateData.getConverted().put(url, escaped);
            return escaped;
        } else {
            stateData.getFailed().add(url);
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

    Path getBaseLocation() {
        return baseLocation;
    }
}
