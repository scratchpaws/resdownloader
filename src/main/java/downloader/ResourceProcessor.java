package downloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

import static downloader.NamesUtils.RESOURCES_PATH_NAME;
import static downloader.NamesUtils.STATE_FILE_NAME;

public class ResourceProcessor
        implements Closeable, AutoCloseable {

    private static final Logger log = LogManager.getLogger(ResourceProcessor.class);
    private static final String TEMP_FILE_NAME = "temp.dat";

    private final Path baseLocation;
    private final Path stateFilePath;
    private final Path tmpFile;
    private final StateData stateData;
    private final HashMap<String, String> reverseConversion = new HashMap<>();
    private final HttpCookieClient httpClient;
    private final SSHWgetClient sshWgetClient;
    private final int tries;
    private final MessageDigest md5;
    private final ErrorImagesGenerator errorImagesGenerator = new ErrorImagesGenerator();

    private ResourceProcessor(final Path baseLocation,
                              final int tries,
                              final int timeout,
                              final boolean reverseMode,
                              final String externalHost,
                              final int externalPort,
                              final String externalUserName,
                              final String externalPassword,
                              final Path externalKey) {

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

        if (!reverseMode) {
            createDirectoriesSilent(baseLocation);
            if (externalHost != null && externalUserName != null) {
                try {
                    sshWgetClient = new SSHWgetClient(externalHost, externalPort, externalUserName, externalPassword, externalKey,
                            timeout, true);
                } catch (Exception err) {
                    throw new RuntimeException("Unable to use external SSH downloader host \"" + externalHost + "\":" + err.getMessage(), err);
                }
            } else {
                sshWgetClient = null;
            }
        } else {
            sshWgetClient = null;
        }

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
                log.warn("Unable to load state file: {}", warn.getMessage());
            }
        }

        if (reverseMode) {
            stateData.getConverted().forEach((url, localName) -> reverseConversion.put(localName, url));
        }
    }

    static ResourceProcessor forDocument(final Document document,
                                         final int tries,
                                         final int timeout,
                                         final boolean reverseMode,
                                         final String externalHost,
                                         final int externalPort,
                                         final String externalUserName,
                                         final String externalPassword,
                                         final Path externalKey) {
        Path documentPath = Paths.get(document.location());
        Path baseLocation = documentPath.resolveSibling(RESOURCES_PATH_NAME);

        return new ResourceProcessor(baseLocation, tries, timeout, reverseMode,
                externalHost, externalPort, externalUserName, externalPassword, externalKey);
    }

    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (IOException err) {
            log.error("Unable to close http client: {}", err.getMessage());
        }
        try {
            if (sshWgetClient != null) {
                sshWgetClient.close();
            }
        } catch (IOException err) {
            log.error("Unable to close SSH client: {}", err.getMessage());
        }
        if (!reverseConversion.isEmpty())
            return;
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(stateFilePath, StandardCharsets.UTF_8)) {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(bufferedWriter, stateData);
            log.info("State file saved successfully");
        } catch (IOException warn) {
            log.warn("Unable to save state file: {}", warn.getMessage());
        }
    }

    @Nullable
    private String replaceToLocal(String remoteUrl) {
        if (stateData.getConverted().containsKey(remoteUrl)) {
            return stateData.getConverted().get(remoteUrl);
        }

        if (stateData.getFailed().contains(remoteUrl)) {
            return null;
        }

        URI remote;
        try {
            remote = new URI(remoteUrl);
        } catch (URISyntaxException err) {
            log.warn("Unable to parse url {}: {}", remoteUrl, err.getMessage());
            stateData.getFailed().add(remoteUrl);
            return null;
        }
        String subPath = (remote.getHost() != null ? remote.getHost() : "")
                + (remote.getPath() != null ? remote.getPath() : "")
                .replaceAll("[^a-zA-Z0-9а-яА-Я%_.\\-\\\\/]", "_");
        Path local = baseLocation.resolve(subPath);

        int retCode = -1;
        for (int i = 1; i <= tries; i++) {
            retCode = sshWgetClient != null
                    ? sshWgetClient.download(remote, tmpFile, local)
                    : httpClient.download(remote, tmpFile, local);
            if (retCode >= HttpURLConnection.HTTP_OK) {
                break;
            }
        }

        if (retCode != HttpURLConnection.HTTP_OK) {
            String errCodeFileName = "err" + (retCode > 0 ? retCode : "NO_RESP") + ".png";
            String errCodeEscaped = RESOURCES_PATH_NAME + "/" + errCodeFileName;
            if (stateData.getErrCodesImages().contains(retCode)) {
                stateData.getConverted().put(remoteUrl, errCodeEscaped);
                return errCodeEscaped;
            }
            String renderText = "ERR " + (retCode > 0 ? retCode : "NO RESP");
            try {
                log.warn("Generating error message for {}", errCodeEscaped);
                errorImagesGenerator.generateImageFromText(renderText, baseLocation.resolve(errCodeFileName));
                stateData.getConverted().put(remoteUrl, errCodeEscaped);
                stateData.getErrCodesImages().add(retCode);
                return errCodeEscaped;
            } catch (Exception err) {
                log.warn("Unable to generate error message image: {}", err.getMessage());
                stateData.getFailed().add(remoteUrl);
                return null;
            }
        } else {
            String md5sum = generateMD5Hash(tmpFile);
            if (md5sum != null && stateData.getUrlFileHashes().containsKey(md5sum)) {
                String alreadyExistsEscaped = stateData.getUrlFileHashes().get(md5sum);
                log.info("File already present in another link: {}", alreadyExistsEscaped);
                stateData.getConverted().put(remoteUrl, alreadyExistsEscaped);
                return alreadyExistsEscaped;
            }
            try {
                createDirectoriesSilent(local.getParent());
            } catch (RuntimeException err) {
                log.error("Unable to create directory {}: {}",
                        local.getParent(), err.getMessage());
                stateData.getFailed().add(remoteUrl);
                return null;
            }
            try {
                Files.move(tmpFile, local,
                        StandardCopyOption.REPLACE_EXISTING);
                String escaped = RESOURCES_PATH_NAME + "/" + subPath;
                stateData.getConverted().put(remoteUrl, escaped);
                if (md5sum != null)
                    stateData.getUrlFileHashes().put(md5sum, escaped);
                return escaped;
            } catch (IOException err) {
                log.error("Unable to move file to end destination {}: {}",
                        local, err.getMessage());
                stateData.getFailed().add(remoteUrl);
                return null;
            }
        }
    }

    @Nullable
    private String replaceToRevert(String localUrl) {
        if (localUrl.startsWith("resources/err"))
            return localUrl;
        return reverseConversion.getOrDefault(localUrl, localUrl);
    }

    @Nullable
    String replaceUrl(String url, boolean revertMode) {
        return revertMode ? replaceToRevert(url) : replaceToLocal(url);
    }

    private void createDirectoriesSilent(Path dir) {
        try {
            if (!Files.exists(dir))
                Files.createDirectories(dir);
        } catch (IOException err) {
            throw new RuntimeException("Unable to create directory " + dir + " for save file.");
        }
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
            log.warn("Unable to calc MD5 sum for temp file: {}", err.getMessage());
            return null;
        }
    }
}
