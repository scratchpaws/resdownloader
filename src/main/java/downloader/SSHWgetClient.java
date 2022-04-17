package downloader;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class SSHWgetClient
        implements Closeable, AutoCloseable {

    private final Session session;
    private final int timeout;
    private final boolean ignoreSSL;
    private static final Logger log = LogManager.getLogger(SSHWgetClient.class.getSimpleName());

    public SSHWgetClient(@NotNull final String hostname,
                         final int port,
                         @NotNull final String user,
                         @Nullable final String password,
                         @Nullable final Path keyFile,
                         final int timeout,
                         final boolean ignoreSsl) throws Exception {

        this.timeout = timeout;
        this.ignoreSSL = ignoreSsl;

        log.info("Using SSH client for downloading, host: {}@{}:{}", user, hostname, port);

        JSch jSch = new JSch();
        if (keyFile != null) {
            jSch.addIdentity(keyFile.toString(), password);
        }
        session = jSch.getSession(user, hostname, port);
        session.setConfig("StrictHostKeyChecking", "no");
        if (keyFile == null && password != null) {
            session.setPassword(password);
        }
        session.connect();

        log.info("Connected");
    }

    int download(URI inputUrl, Path tempFile, Path outputFile) {
        log.info("Querying " + inputUrl);
        ChannelExec channelExec = null;
        try {
            channelExec = (ChannelExec) session.openChannel("exec");
            ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
            ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
            channelExec.setErrStream(stdErr);
            channelExec.setOutputStream(stdOut);
            StringBuilder commandBuilder = new StringBuilder()
                    .append("/bin/wget -O - ")
                    .append("--timeout=")
                    .append(timeout)
                    .append(" ");
            if (ignoreSSL) {
                commandBuilder.append("--no-check-certificate ");
            }
            commandBuilder.append("'")
                    .append(inputUrl.toString())
                    .append("'");
            channelExec.setCommand(commandBuilder.toString());
            channelExec.connect();
            while (channelExec.isConnected()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException err) {
                    throw new JSchException("interrupt signal");
                }
            }
            String errOutput = stdErr.toString();
            log.info(errOutput);
            int retCode = channelExec.getExitStatus();
            if (retCode == 0 && errOutput.contains("200 OK")) {
                log.info("HTTP OK");
                if (Files.exists(outputFile)) {
                    long fileSize = Files.size(outputFile);
                    long contentLength = stdOut.size();
                    if (fileSize == contentLength) {
                        log.info("File already exists, size match");
                        return 200;
                    }
                }
                log.info("Writing to file");
                try (OutputStream bufOut = Files.newOutputStream(tempFile)) {
                    stdOut.writeTo(bufOut);
                }
                log.info("Wrote OK");
                return 200;
            } else {
                if (errOutput.contains("404: Not Found")) {
                    return 404;
                } else if (errOutput.contains("403: Forbidden")) {
                    return 403;
                } else {
                    return -1;
                }
            }
        } catch (IOException err) {
            log.warn("Unable to download file: " + err.getMessage());
            return -1;
        } catch (JSchException err) {
            log.error("Connection error: " + err.getMessage());
            throw new RuntimeException(err);
        } finally {
            if (channelExec != null) {
                try {
                    channelExec.disconnect();
                } catch (Exception ignore) {}
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            session.disconnect();
            log.info("Disconnected from " + session.getUserName() + "@" + session.getHost() + ":" + session.getPort());
        } catch (Exception err) {
            throw new IOException(err);
        }
    }
}
