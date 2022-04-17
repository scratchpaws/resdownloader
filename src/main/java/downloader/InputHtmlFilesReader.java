package downloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class InputHtmlFilesReader
        implements Iterable<Document> {

    private static final Logger log = LogManager.getLogger(InputHtmlFilesReader.class);
    private final List<Path> inputFiles;

    @Contract(pure = true)
    InputHtmlFilesReader(@NotNull List<Path> inputFiles) {
        this.inputFiles = inputFiles;
    }

    public int size() {
        return inputFiles.size();
    }

    @NotNull
    @Override
    public Iterator<Document> iterator() {
        return new DocIterator(inputFiles.iterator());
    }

    public static class DocIterator
            implements Iterator<Document> {

        private final Iterator<Path> filesIterator;
        private final UniversalDetector detector = new UniversalDetector();
        private Document nextDocument = null;

        DocIterator(Iterator<Path> filesIterator) {
            this.filesIterator = filesIterator;
        }

        @Override
        public boolean hasNext() {
            nextDocument = null;
            if (filesIterator.hasNext()) {
                Path nextFile = filesIterator.next();
                detector.reset();

                try (PushbackInputStream pbInputStream = new PushbackInputStream(
                        new BufferedInputStream(
                                Files.newInputStream(nextFile), 4096),
                        4096)) {
                    byte[] buff = new byte[4096];
                    int cnt = pbInputStream.read(buff, 0, buff.length);
                    if (cnt <= 0) {
                        log.warn("File {} is empty, skip", nextFile);
                        return false;
                    }
                    detector.handleData(buff);
                    detector.dataEnd();

                    pbInputStream.unread(buff);

                    String detectedCharset = detector.getDetectedCharset();
                    if (detectedCharset == null || detectedCharset.isEmpty() || detectedCharset.equals("US-ASCII"))
                        detectedCharset = StandardCharsets.UTF_8.displayName();
                    log.info("File: \"{}\", detected charset: {}", nextFile.getFileName(), detectedCharset);

                    nextDocument = Jsoup.parse(pbInputStream,
                            detectedCharset,
                            nextFile.toAbsolutePath().toString());
                    return true;
                } catch (IOException err) {
                    log.error("Unable to read file \"{}\": {}", nextFile, err.getMessage());
                }
            }
            return false;
        }

        @Override
        public Document next() throws NoSuchElementException {
            if (nextDocument == null)
                throw new NoSuchElementException();
            return nextDocument;
        }
    }
}
