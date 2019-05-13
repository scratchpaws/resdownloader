package downloader;

import org.apache.commons.cli.ParseException;

import java.nio.file.Path;
import java.util.List;

public class ParsedCmdline {

    private boolean showHelp;
    private Path parentDir;
    private List<Path> inputFiles;
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
}
