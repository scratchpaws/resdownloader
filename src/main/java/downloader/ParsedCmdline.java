package downloader;

import org.apache.commons.cli.ParseException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class ParsedCmdline {

    private boolean showHelp = false;
    private int tries = 3;
    private int timeout = 60;
    private Path parentDir = Paths.get("./");
    private List<Path> inputFiles = Collections.emptyList();
    private ParseException parseException;

    public boolean isShowHelp() {
        return showHelp;
    }

    public void setShowHelp(boolean showHelp) {
        this.showHelp = showHelp;
    }

    public Path getParentDir() {
        return parentDir;
    }

    public void setParentDir(Path parentDir) {
        this.parentDir = parentDir;
    }

    public List<Path> getInputFiles() {
        return inputFiles;
    }

    public void setInputFiles(List<Path> inputFiles) {
        this.inputFiles = inputFiles;
    }

    public ParseException getParseException() {
        return parseException;
    }

    public void setParseException(ParseException parseException) {
        this.parseException = parseException;
    }

    public int getTries() {
        return tries;
    }

    public void setTries(int tries) {
        this.tries = tries;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

}
