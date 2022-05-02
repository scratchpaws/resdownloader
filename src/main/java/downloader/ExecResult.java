package downloader;

public class ExecResult {

    public final byte[] stdout;
    public final byte[] stderr;
    public final int exitStatus;

    public static final int EXIT_CONNECTION_CLOSED = Integer.MIN_VALUE;
    public static final ExecResult SSH_CLOSED_RESULT = new ExecResult(new byte[0], new byte[0], EXIT_CONNECTION_CLOSED);

    public ExecResult(final byte[] stdout,
                      final byte[] stderr,
                      final int exitStatus) {
        this.stderr = stderr;
        this.stdout = stdout;
        this.exitStatus = exitStatus;
    }

    public String getStdoutString() {
        return new String(stdout);
    }

    public String getStderrString() {
        return new String(stderr);
    }

    public boolean isConnectionClosed() {
        return exitStatus == EXIT_CONNECTION_CLOSED;
    }

    public boolean hasBadExitCode() {
        return exitStatus != 0 && exitStatus != EXIT_CONNECTION_CLOSED;
    }

    public boolean hasGoodExitCode() {
        return exitStatus == 0;
    }
}
