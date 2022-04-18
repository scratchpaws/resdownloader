package downloader;

public class ExecResult {

    public final byte[] stdout;
    public final byte[] stderr;
    public final int exitStatus;

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
}
