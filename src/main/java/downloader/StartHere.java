package downloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StartHere {

    private static final Logger log = LogManager.getLogger(StartHere.class);

    private static final Pattern cssLink = Pattern.compile("src: url\\(resources.*\\);",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String cssLinkBegin = "src: url(";
    private static final String cssLinkEnd = ");";

    public static void main(String... args) {
        CmdLineParser cmdLineParser = new CmdLineParser();
        ParsedCmdline parsedCmdline = cmdLineParser.parse(args);
        cmdLineParser.checkErrors(parsedCmdline);

        log.info("Starting...");

        final InputHtmlFilesReader inputHtmlFilesReader = new InputHtmlFilesReader(parsedCmdline.getInputFiles());
        final long totalFilesCount = inputHtmlFilesReader.size();
        long filesCounter = 0L;

        for (Document document : inputHtmlFilesReader) {
            filesCounter++;
            log.info("Processing {}", document.location());
            Document.OutputSettings os = document.outputSettings();
            os.prettyPrint(false);

            try (ResourceProcessor resourceProcessor = ResourceProcessor.forDocument(document,
                    parsedCmdline.getTries(),
                    parsedCmdline.getTimeout(),
                    parsedCmdline.isReverseMode(),
                    parsedCmdline.getExternalHost(),
                    parsedCmdline.getExternalPort(),
                    parsedCmdline.getExternalUserName(),
                    parsedCmdline.getExternalPassword(),
                    parsedCmdline.getExternalKeyFile())) {

                final Elements imagesLinks = document.getElementsByTag("img");
                final Elements scriptLinks = document.getElementsByTag("script");
                final Elements stylesLinks = document.getElementsByTag("link");
                final Elements innerStylesBodies = document.getElementsByTag("style");
                final long totalUrlsCount = imagesLinks.size() + scriptLinks.size() + stylesLinks.size() + innerStylesBodies.size();
                long urlsCounter = 0L;

                for (Element image : imagesLinks) {
                    urlsCounter++;
                    log.info(String.format("File %d of %d: processing link %d of %d", filesCounter, totalFilesCount, urlsCounter, totalUrlsCount));
                    String src = image.attr("src");
                    String replaced = resourceProcessor.replaceUrl(src, parsedCmdline.isReverseMode());
                    if (replaced == null)
                        continue;
                    image.attr("src", replaced);
                }

                for (Element script : scriptLinks) {
                    urlsCounter++;
                    log.info(String.format("File %d of %d: processing link %d of %d", filesCounter, totalFilesCount, urlsCounter, totalUrlsCount));
                    String src = script.attr("src");
                    if (!src.isEmpty()) {
                        String replaced = resourceProcessor.replaceUrl(src, parsedCmdline.isReverseMode());
                        if (replaced == null)
                            continue;
                        script.attr("src", replaced);
                    }
                }

                for (Element style : stylesLinks) {
                    urlsCounter++;
                    log.info(String.format("File %d of %d: processing link %d of %d", filesCounter, totalFilesCount, urlsCounter, totalUrlsCount));
                    String rel = style.attr("rel");
                    String href = style.attr("href");
                    if (rel.equals("stylesheet") && !href.isEmpty()) {
                        String replaced = resourceProcessor.replaceUrl(href, parsedCmdline.isReverseMode());
                        if (replaced == null)
                            continue;
                        style.attr("href", replaced);
                    }
                }

                for (Element innerStyle : innerStylesBodies) {
                    urlsCounter++;
                    log.info(String.format("File %d of %d: processing link %d of %d", filesCounter, totalFilesCount, urlsCounter, totalUrlsCount));
                    String css = innerStyle.html();
                    StringBuilder modifier = new StringBuilder(css);
                    if (!parsedCmdline.isReverseMode()) {
                        LinkExtractor linkExtractor = LinkExtractor.builder()
                                .linkTypes(EnumSet.of(LinkType.URL, LinkType.WWW, LinkType.EMAIL))
                                .build();
                        for (LinkSpan linkSpan : linkExtractor.extractLinks(css)) {
                            String url = css.substring(linkSpan.getBeginIndex(), linkSpan.getEndIndex());
                            String local = resourceProcessor.replaceUrl(url, parsedCmdline.isReverseMode());
                            if (local == null)
                                continue;
                            int begin = modifier.indexOf(url);
                            if (begin >= 0) {
                                modifier.replace(begin, begin + url.length(), local);
                            }
                        }

                    } else {
                        Matcher cssUrlMatcher = cssLink.matcher(css);
                        while (cssUrlMatcher.find()) {
                            String found = cssUrlMatcher.group();
                            String url = found.substring(cssLinkBegin.length(), found.lastIndexOf(cssLinkEnd));
                            String revert = resourceProcessor.replaceUrl(url, parsedCmdline.isReverseMode());
                            if (revert == null)
                                continue;
                            int begin = modifier.indexOf(url);
                            if (begin >= 0) {
                                modifier.replace(begin, begin + url.length(), revert);
                            }
                        }
                    }
                    innerStyle.html(modifier.toString());
                }

                Path newFileName = parsedCmdline.isReverseMode()
                        ? NamesUtils.getOrigPath(Paths.get(document.location()))
                        : NamesUtils.getDownloadPath(Paths.get(document.location()));

                log.info("Save modified html file to {}", newFileName);
                try (BufferedWriter bufferedWriter = Files.newBufferedWriter(newFileName, document.charset())) {
                    bufferedWriter.append(document.outerHtml());
                    log.info("Success");
                } catch (IOException err) {
                    log.error("Unable to save output file to {}: {}", newFileName, err.getMessage());
                }
            }
        }
    }
}
