package downloader;

import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

class CmdLineParser {

    private Options options;
    private static final Logger log = Logger.getLogger("CMDLINE");
    private static final DirectoryStream.Filter<Path> onlySupporter =
            path -> Files.isDirectory(path)
                    || FilenameUtils.getExtension(path.getFileName().toString())
                    .toLowerCase().startsWith("htm");

    CmdLineParser() {

        options = new Options();

        Option help = Option.builder("h")
                .longOpt("help")
                .desc("Display this help")
                .build();

        Option wait = Option.builder("w")
                .longOpt("wait")
                .hasArg()
                .argName("sec.")
                .desc("Set waiting timeout. Default - 60 sec.")
                .build();

        Option tries = Option.builder("t")
                .longOpt("tries")
                .hasArg()
                .argName("count")
                .desc("Set tries count before give up and skip download. Default - 3")
                .build();

        options.addOption(help);
        options.addOption(wait);
        options.addOption(tries);
    }

    ParsedCmdline parse(String[] args) {

        ParsedCmdline parsedCmdline = new ParsedCmdline();
        parsedCmdline.setShowHelp(false);

        try {
            DefaultParser parser = new DefaultParser();
            CommandLine commandLine = parser.parse(options, args);

            boolean help = commandLine.hasOption("h");
            parsedCmdline.setShowHelp(help);

            if (help)
                return parsedCmdline;

            List<String> rawInputFiles = commandLine.getArgList();
            if (rawInputFiles == null || rawInputFiles.isEmpty())
                throw new ParseException("Input html files required");

            List<Path> inputFiles = new ArrayList<>(rawInputFiles.size());
            for (String rawInputFile : rawInputFiles) {
                Path inputFile = Paths.get(rawInputFile);
                if (Files.notExists(inputFile))
                    throw new ParseException("Input file not found: " + rawInputFile);
                if (Files.isDirectory(inputFile)) {
                    dirScanner(inputFile, inputFiles);
                } else if (Files.isRegularFile(inputFile)) {
                    if (!FilenameUtils.getExtension(rawInputFile).toLowerCase().startsWith("htm"))
                        throw new ParseException("Input file is not supported: " + rawInputFile);
                    inputFiles.add(inputFile);
                } else {
                    throw new ParseException("Input file is not a regular file: " + rawInputFile);
                }
            }
            parsedCmdline.setInputFiles(inputFiles);

            String rawTries = commandLine.getOptionValue("t", "3");
            int tries;
            try {
                tries = Integer.parseInt(rawTries);
            } catch (NumberFormatException nfe) {
                throw new ParseException("Unable to parse tries count: " + rawTries);
            }

            if (tries < 1)
                throw new ParseException("Tries count cannot be less that 1");

            parsedCmdline.setTries(tries);

            String rawTimeout = commandLine.getOptionValue("w", "60");
            int timeout;
            try {
                timeout = Integer.parseInt(rawTimeout);
            } catch (NumberFormatException nfe) {
                throw new ParseException("Unable to parse timeout in seconds: " + rawTimeout);
            }

            if (timeout < 1)
                throw new ParseException("Timeout cannot be less that 1 second");

            timeout *= 1000;
            parsedCmdline.setTimeout(timeout);
        } catch (ParseException err) {
            parsedCmdline.setParseException(err);
        }

        return parsedCmdline;
    }

    private void displayHelp() {
        HelpFormatter helpFormatter = new HelpFormatter();
        PrintWriter errWriter = new PrintWriter(System.err, true);
        String jarName = "";
        try {
            jarName = Paths.get(CmdLineParser.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                    .getFileName()
                    .toString();
        } catch (URISyntaxException ignore) {
        }

        StringWriter sout = new StringWriter();
        PrintWriter inMemWriter = new PrintWriter(sout, true);
        helpFormatter.setSyntaxPrefix("");
        helpFormatter.printUsage(inMemWriter, 80, "usage: java -jar " + jarName, options);
        inMemWriter.print(" FILE.HTML|DIRECTORY...");
        inMemWriter.flush();
        String usageString = sout.toString().replaceAll("[\n\r]", "");
        helpFormatter.printHelp(errWriter,
                80,
                usageString,
                "Options:", options,
                2,
                2,
                "",
                false);
    }

    void checkErrors(ParsedCmdline parsedCmdline) {
        if (parsedCmdline.getParseException() != null) {
            log.severe(parsedCmdline.getParseException().getMessage());
            System.exit(2);
        }

        if (parsedCmdline.isShowHelp()) {
            displayHelp();
            System.exit(0);
        }
    }

    private void dirScanner(Path inputFile, List<Path> capacitor) throws ParseException {
        if (Files.isDirectory(inputFile)) {
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(inputFile, onlySupporter)) {
                for (Path path : dirStream) {
                    if (Files.isDirectory(path)) {
                        dirScanner(path, capacitor);
                    } else {
                        capacitor.add(path);
                    }
                }
            } catch (IOException err) {
                throw new ParseException("Unable to enumerate directory " + inputFile + ": " + err.getMessage());
            }
        }
    }
}
