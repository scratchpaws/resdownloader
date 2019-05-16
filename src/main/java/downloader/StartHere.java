package downloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class StartHere {

    private static final Logger log = Logger.getLogger("MAIN");

    public static void main(String... args) {
        new LogConfigurer().configure();

        CmdLineParser cmdLineParser = new CmdLineParser();
        ParsedCmdline parsedCmdline = cmdLineParser.parse(args);
        cmdLineParser.checkErrors(parsedCmdline);

        log.info("Starting...");

        for (Path inputHtmlPath : parsedCmdline.getInputFiles()) {
            log.info("Processing " + inputHtmlPath);

            UniversalDetector detector = new UniversalDetector();
            detector.reset();

            try (PushbackInputStream pbInputStream = new PushbackInputStream(
                    new BufferedInputStream(
                            Files.newInputStream(inputHtmlPath), 4096),
                    4096)) {
                byte[] buff = new byte[4096];
                int cnt = pbInputStream.read(buff, 0, buff.length);
                if (cnt <= 0) {
                    log.warning("File " + inputHtmlPath + " is empty, skip");
                    continue;
                }
                detector.handleData(buff);
                detector.dataEnd();

                pbInputStream.unread(buff);

                String detectedCharset = detector.getDetectedCharset();
                if (detectedCharset == null || detectedCharset.isEmpty())
                    detectedCharset = StandardCharsets.UTF_8.displayName();
                log.info("Detected charset: " + detectedCharset);

                Document document = Jsoup.parse(pbInputStream,
                        detectedCharset,
                        inputHtmlPath.toAbsolutePath().toString());
            } catch (IOException err) {
                log.severe("Unable to read file " + inputHtmlPath + ": " + err.getMessage());
            }
        }
    }
}
