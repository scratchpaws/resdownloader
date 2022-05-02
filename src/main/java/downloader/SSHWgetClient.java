package downloader;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class SSHWgetClient
        implements Closeable, AutoCloseable {

    private final JSch jSch = new JSch();
    private Session session = null;
    private boolean closed = false;
    private final String hostname;
    private final int port;
    private final String user;
    private final String password;
    private final boolean hasKeyFile;
    private final String connectionString;
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

        this.hostname = hostname;
        this.port = port;
        this.user = user;
        this.password = password;
        this.hasKeyFile = keyFile != null;
        this.timeout = timeout;
        this.ignoreSSL = ignoreSsl;
        this.connectionString = String.format("%s@%s:%d", user, hostname, port);
        log.info("Using SSH client for downloading, host: {}}", connectionString);
        if (hasKeyFile) {
            jSch.addIdentity(keyFile.toString(), password);
        }
        connectReconnect();
    }

    private void connectReconnect() {
        if (session != null) {
            log.info("Disconnecting from {}", connectionString);
            try {
                session.disconnect();
                session = null;
            } catch (Exception err) {
                log.warn("Unable to disconnect from {}: {}", connectionString, err.getMessage());
            }
        }
        boolean connected = false;
        int failsCount = 0;
        while (!connected && !closed) {
            log.info("Connecting to {} (trying {})", connectionString, failsCount + 1);
            long delayBeforeReconnect = failsCount * 1_000L;
            try {
                if (delayBeforeReconnect > 0L)
                    Thread.sleep(delayBeforeReconnect);
            } catch (InterruptedException err) {
                log.error("Interrupted");
                try {
                    close();
                } catch (IOException ignore) {}
                return;
            }
            try {
                session = jSch.getSession(user, hostname, port);
                session.setConfig("StrictHostKeyChecking", "no");
                if (!hasKeyFile && password != null) {
                    session.setPassword(password);
                }
                session.connect();
                connected = true;
            } catch (JSchException err) {
                log.error("Unable to connect to {}: {}", connectionString, err.getMessage());
                failsCount++;
            }
        }
        log.info("Connected to {}", connectionString);
    }

    @Contract("_ -> new")
    private @NotNull ExecResult executeCommand(final @NotNull String command) {
        while (!closed) {
            ChannelExec exec = null;
            try {
                exec = (ChannelExec) session.openChannel("exec");
                ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
                ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
                exec.setOutputStream(stdOut);
                exec.setErrStream(stdErr);
                exec.setCommand(command);
                exec.connect();
                while (exec.isConnected()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException err) {
                        log.warn("interrupt signal");
                        return ExecResult.SSH_CLOSED_RESULT;
                    }
                }
                return new ExecResult(stdOut.toByteArray(), stdErr.toByteArray(), exec.getExitStatus());
            } catch (JSchException cerr) {
                log.error("Unable to execute command \"{}\" on {}: {}", command, connectionString, cerr.getMessage());
                connectReconnect();
            } finally {
                try {
                    if (exec != null) {
                        exec.disconnect();
                    }
                } catch (Exception ignore) {
                }
            }
        }
        return ExecResult.SSH_CLOSED_RESULT;
    }

    int download(URI inputUrl, Path tempFile, Path outputFile) {
        log.info("Querying " + inputUrl);
        try {
            String mktempCommand = "mktemp -p /tmp resdownloader_XXXXXXXXXXXXX";
            ExecResult mktemp = executeCommand(mktempCommand);
            String remoteTempPath = mktemp.getStdoutString().replaceAll("(\r\n|\r|\n)", "");
            log.info(mktempCommand);
            log.info(remoteTempPath);
            String mktempStderr = mktemp.getStderrString();
            if (mktempStderr.length() > 0) {
                log.warn(mktempStderr);
            }
            if (mktemp.hasBadExitCode()) {
                throw new RuntimeException("\"" + mktempCommand + "\" exited with non-zero code");
            } else if (mktemp.isConnectionClosed()) {
                throw new RuntimeException("SSH connection is closed");
            }

            StringBuilder commandBuilder = new StringBuilder()
                    .append("wget -O ")
                    .append(remoteTempPath)
                    .append(" --timeout=")
                    .append(timeout)
                    .append(" --tries=1 ");
            if (ignoreSSL) {
                commandBuilder.append("--no-check-certificate ");
            }
            commandBuilder.append("'")
                    .append(inputUrl.toString())
                    .append("'");
            String wgetExecComand = commandBuilder.toString();
            log.info(wgetExecComand);
            ExecResult wgetResult = executeCommand(commandBuilder.toString());
            if (wgetResult.isConnectionClosed()) {
                throw new RuntimeException("SSH connection is closed");
            }
            String wgetStderrOutput = wgetResult.getStderrString();
            log.info(wgetStderrOutput);

            String catCommand = "cat \"" + remoteTempPath + "\"";
            log.info(catCommand);
            ExecResult catTmp = executeCommand(catCommand);
            String catStderr = catTmp.getStderrString();
            if (catStderr.length() > 0) {
                log.warn(catStderr);
            }

            String rmCommand = "rm -f -- \"" + remoteTempPath + "\"";
            log.info(rmCommand);
            ExecResult rmTmp = executeCommand(rmCommand);
            String rmStderr = rmTmp.getStderrString();
            if (rmStderr.length() > 0) {
                log.warn(rmStderr);
            }

            if (catTmp.hasBadExitCode()) {
                throw new RuntimeException("cat exited with non-zero code");
            } else if (catTmp.isConnectionClosed()) {
                throw new RuntimeException("SSH connection is closed");
            }

            if (rmTmp.hasBadExitCode()) {
                log.warn("rm exited with non-zero code");
            } else if (rmTmp.isConnectionClosed()) {
                throw new RuntimeException("SSH connection is closed");
            }

            if (wgetResult.hasGoodExitCode() && wgetStderrOutput.contains("200 OK")) {
                log.info("HTTP OK");
                if (Files.exists(outputFile)) {
                    long fileSize = Files.size(outputFile);
                    long contentLength = catTmp.stdout.length;
                    if (fileSize == contentLength) {
                        log.info("File already exists, size match");
                        Files.copy(outputFile, tempFile, StandardCopyOption.REPLACE_EXISTING);
                        return 200;
                    }
                }
                log.info("Writing to file");
                try (OutputStream bufOut = Files.newOutputStream(tempFile)) {
                    bufOut.write(catTmp.stdout);
                }
                log.info("Wrote OK");
                return 200;
            } else {
                if (wgetStderrOutput.contains("404: Not Found")) {
                    return 404;
                } else if (wgetStderrOutput.contains("403: Forbidden")) {
                    return 403;
                } else if (wgetStderrOutput.contains("503: Service Temporarily Unavailable")) {
                    return 503;
                } else if (wgetStderrOutput.contains("451: Unavailable For Legal Reasons")) {
                    return 451;
                } else {
                    return -1;
                }
            }
        } catch (IOException err) {
            log.warn("Unable to download file: " + err.getMessage());
            return -1;
        } catch (Exception err) {
            log.error("Connection error: " + err.getMessage());
            throw new RuntimeException(err);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (session != null) {
                session.disconnect();
                log.info("Disconnected from " + session.getUserName() + "@" + session.getHost() + ":" + session.getPort());
            }
        } catch (Exception err) {
            throw new IOException(err);
        } finally {
            closed = true;
            session = null;
        }
    }
}
