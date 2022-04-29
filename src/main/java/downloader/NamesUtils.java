package downloader;

import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;

class NamesUtils {

    static final String RESOURCES_PATH_NAME = "resources";
    static final String STATE_FILE_NAME = "state.json";
    static final String STATE_DB_NAME = "state.sqlite3";

    private static String getDownloadName(Path inputName) {
        return FilenameUtils.getBaseName(inputName.toString()) + "_dl.html";
    }

    static Path getDownloadPath(Path inputName) {
        return inputName.resolveSibling(getDownloadName(inputName));
    }

    static boolean isDownloadedName(Path inputName) {
        return FilenameUtils.getBaseName(inputName.toString()).endsWith("_dl");
    }

    private static String getOrigName(Path inputFile) {
        if (isDownloadedName(inputFile)) {
            String baseName = FilenameUtils.getBaseName(inputFile.toString());
            return baseName.substring(0, baseName.lastIndexOf("_dl")) + ".html";
        } else {
            return inputFile.toString();
        }
    }

    static Path getOrigPath(Path inputName) {
        return inputName.resolveSibling(getOrigName(inputName));
    }
}
