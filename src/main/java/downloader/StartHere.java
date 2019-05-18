package downloader;

import org.apache.commons.io.FilenameUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.logging.Logger;

public class StartHere {

    private static final Logger log = Logger.getLogger("MAIN");

    public static void main(String... args) {
        new LogConfigurer().configure();

        CmdLineParser cmdLineParser = new CmdLineParser();
        ParsedCmdline parsedCmdline = cmdLineParser.parse(args);
        cmdLineParser.checkErrors(parsedCmdline);

        log.info("Starting...");

        InputHtmlFilesReader inputHtmlFilesReader = new InputHtmlFilesReader(parsedCmdline.getInputFiles());
        for (Document document : inputHtmlFilesReader) {
            log.info("Processing " + document.location());
            Document.OutputSettings os = document.outputSettings();
            os.prettyPrint(false);

            try (ResourceProcessor resourceProcessor = ResourceProcessor.forDocument(document,
                    parsedCmdline.getTries(),
                    parsedCmdline.getTimeout())) {

                Elements imagesLinks = document.getElementsByTag("img");
                for (Element image : imagesLinks) {
                    String src = image.attr("src");
                    String local = resourceProcessor.replaceToLocal(src);
                    if (local == null)
                        continue;
                    image.attr("src", local);
                }

                Elements scriptLinks = document.getElementsByTag("script");
                for (Element script : scriptLinks) {
                    String src = script.attr("src");
                    if (!src.isEmpty()) {
                        String local = resourceProcessor.replaceToLocal(src);
                        if (local == null)
                            continue;
                        script.attr("src", local);
                    }
                }

                Elements stylesLinks = document.getElementsByTag("link");
                for (Element style : stylesLinks) {
                    String rel = style.attr("rel");
                    String href = style.attr("href");
                    if (rel.equals("stylesheet") && !href.isEmpty()) {
                        String local = resourceProcessor.replaceToLocal(href);
                        if (local == null)
                            continue;
                        style.attr("href", local);
                    }
                }

                Elements innerStylesBodies = document.getElementsByTag("style");
                for (Element innerStyle : innerStylesBodies) {
                    String css = innerStyle.html();
                    StringBuilder modifier = new StringBuilder(css);
                    LinkExtractor linkExtractor = LinkExtractor.builder()
                            .linkTypes(EnumSet.of(LinkType.URL, LinkType.WWW, LinkType.EMAIL))
                            .build();
                    for (LinkSpan linkSpan : linkExtractor.extractLinks(css)) {
                        String url = css.substring(linkSpan.getBeginIndex(), linkSpan.getEndIndex());
                        String local = resourceProcessor.replaceToLocal(url);
                        if (local == null)
                            continue;
                        int begin = modifier.indexOf(url);
                        if (begin >= 0) {
                            modifier.replace(begin, begin + url.length(), local);
                        }
                    }
                    innerStyle.html(modifier.toString());
                }

                Path newFileName = resourceProcessor.getBaseLocation()
                        .getParent()
                        .resolve(FilenameUtils.getBaseName(document.location()) + "_dl.html");

                log.info("Save modified html file to " + newFileName);
                try {
                    Files.writeString(newFileName, document.outerHtml(), document.charset());
                    log.info("Success");
                } catch (IOException err) {
                    log.severe("Unable to save output file to " + newFileName);
                }
            }
        }
    }
}
