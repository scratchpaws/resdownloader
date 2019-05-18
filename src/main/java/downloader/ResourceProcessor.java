package downloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.logging.Logger;

public class ResourceProcessor
        implements Closeable, AutoCloseable {

    static final String RESOURCES_PATH_NAME = "resources";
    private static final Logger log = Logger.getLogger("RES");
    private static final String STATE_FILE_NAME = "state.json";
    private static final String TEMP_FILE_NAME = "temp.dat";

    private final Path baseLocation;
    private final Path stateFilePath;
    private final Path tmpFile;
    private final StateData stateData;
    private final HttpCookieClient httpClient;
    private final int tries;
    private final MessageDigest md5;
    private final ErrorImagesGenerator errorImagesGenerator = new ErrorImagesGenerator();

    private ResourceProcessor(Path baseLocation, int tries, int timeout) {

        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException err) {
            throw new RuntimeException("Your JDK not supported MD5 hashes");
        }

        this.baseLocation = baseLocation;
        this.stateFilePath = baseLocation.resolve(STATE_FILE_NAME);
        this.tmpFile = baseLocation.resolve(TEMP_FILE_NAME);
        this.httpClient = new HttpCookieClient(timeout, true);
        this.tries = tries;

        this.stateData = new StateData();
        this.stateData.setConverted(new HashMap<>());
        this.stateData.setFailed(new ArrayList<>());
        this.stateData.setUrlFileHashes(new HashMap<>());
        this.stateData.setErrCodesImages(new ArrayList<>());

        createDirectoriesSilent(baseLocation);

        if (Files.exists(stateFilePath)) {
            try (BufferedReader bufferedReader = Files.newBufferedReader(stateFilePath, StandardCharsets.UTF_8)) {
                ObjectMapper objectMapper = new ObjectMapper();
                StateData loaded = objectMapper.readValue(bufferedReader, StateData.class);
                if (loaded.getFailed() != null && !loaded.getFailed().isEmpty())
                    this.stateData.setFailed(loaded.getFailed());
                if (loaded.getConverted() != null && !loaded.getConverted().isEmpty())
                    this.stateData.setConverted(loaded.getConverted());
                if (loaded.getUrlFileHashes() != null && !loaded.getUrlFileHashes().isEmpty())
                    this.stateData.setUrlFileHashes(loaded.getUrlFileHashes());
                if (loaded.getErrCodesImages() != null && !loaded.getErrCodesImages().isEmpty())
                    this.stateData.setErrCodesImages(loaded.getErrCodesImages());
                log.info("State file loaded successfully");
            } catch (IOException warn) {
                log.warning("Unable to load state file; " + warn.getMessage());
            }
        }
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
            return stateData.getConverted().get(url);
        }

        if (stateData.getFailed().contains(url)) {
            return null;
        }

        URI remote;
        try {
            remote = new URI(url);
        } catch (URISyntaxException err) {
            log.warning("Unable to parse url " + url + ": " + err.getMessage());
            stateData.getFailed().add(url);
            return null;
        }
        String subPath = (remote.getHost() != null ? remote.getHost() : "")
                + (remote.getPath() != null ? remote.getPath() : "")
                .replaceAll("[^a-zA-Z0-9а-яА-Я%_.\\-\\\\/]", "_");
        Path local = baseLocation.resolve(subPath);

        int retCode = -1;
        for (int i = 1; i <= tries; i++) {
            retCode = httpClient.download(remote, tmpFile, local);
            if (retCode >= HttpURLConnection.HTTP_OK) {
                break;
            }
        }

        if (retCode != HttpURLConnection.HTTP_OK) {
            String errCodeFileName = "err" + (retCode > 0 ? retCode : "NO RESP") + ".png";
            String errCodeEscaped = RESOURCES_PATH_NAME + "/" + errCodeFileName;
            if (stateData.getErrCodesImages().contains(retCode)) {
                stateData.getConverted().put(url, errCodeEscaped);
                return errCodeEscaped;
            }
            String renderText = "ERR " + (retCode > 0 ? retCode : "NO RESP");
            try {
                log.warning("Generating error message " + errCodeEscaped);
                errorImagesGenerator.generateImageFromText(renderText, baseLocation.resolve(errCodeFileName));
                stateData.getConverted().put(url, errCodeEscaped);
                stateData.getErrCodesImages().add(retCode);
                return errCodeEscaped;
            } catch (Exception err) {
                log.severe("Unable to generate error message image: " + err.getMessage());
                stateData.getFailed().add(url);
                return null;
            }
        } else {
            String md5sum = generateMD5Hash(tmpFile);
            if (md5sum != null && stateData.getUrlFileHashes().containsKey(md5sum)) {
                String alreadyExistsEscaped = stateData.getUrlFileHashes().get(md5sum);
                log.info("File already present in another link: " + alreadyExistsEscaped);
                stateData.getConverted().put(url, alreadyExistsEscaped);
                return alreadyExistsEscaped;
            }
            try {
                createDirectoriesSilent(local.getParent());
            } catch (RuntimeException err) {
                log.severe("Unable to create directory "
                        + local.getParent() + ": " + err.getMessage());
                stateData.getFailed().add(url);
                return null;
            }
            try {
                Files.move(tmpFile, local,
                        StandardCopyOption.REPLACE_EXISTING);
                String escaped = RESOURCES_PATH_NAME + "/" + subPath;
                stateData.getConverted().put(url, escaped);
                if (md5sum != null)
                    stateData.getUrlFileHashes().put(md5sum, escaped);
                return escaped;
            } catch (IOException err) {
                log.severe("Unable to move file to end destination "
                        + local + ": " + err.getMessage());
                stateData.getFailed().add(url);
                return null;
            }
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

    @Nullable
    private String generateMD5Hash(Path inputFile) {
        md5.reset();
        try (InputStream bufIo = Files.newInputStream(inputFile)) {
            int readied;
            byte[] buffer = new byte[4096];
            while ((readied = bufIo.read(buffer, 0, buffer.length)) > 0) {
                md5.update(buffer, 0, readied);
            }
            return Base64.getEncoder().encodeToString(md5.digest());
        } catch (IOException err) {
            log.warning("Unable to calc MD5 sum for temp file: " + err.getMessage());
            return null;
        }
    }
}
