package downloader;

import org.apache.commons.cli.ParseException;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

class ParsedCmdline {

    private boolean showHelp = false;
    private boolean reverseMode = false;
    private int tries = 3;
    private int timeout = 60000;
    private List<Path> inputFiles = Collections.emptyList();
    private ParseException parseException;

    boolean isShowHelp() {
        return showHelp;
    }

    void setShowHelp(boolean showHelp) {
        this.showHelp = showHelp;
    }

    List<Path> getInputFiles() {
        return inputFiles;
    }

    void setInputFiles(List<Path> inputFiles) {
        this.inputFiles = inputFiles;
    }

    ParseException getParseException() {
        return parseException;
    }

    void setParseException(ParseException parseException) {
        this.parseException = parseException;
    }

    int getTries() {
        return tries;
    }

    void setTries(int tries) {
        this.tries = tries;
    }

    int getTimeout() {
        return timeout;
    }

    void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    boolean isReverseMode() {
        return reverseMode;
    }

    void setReverseMode(boolean reverseMode) {
        this.reverseMode = reverseMode;
    }
}
