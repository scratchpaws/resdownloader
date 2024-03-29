package downloader;

import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static downloader.NamesUtils.RESOURCES_PATH_NAME;
import static downloader.NamesUtils.STATE_FILE_NAME;

class CmdLineParser {

    private static final Logger log = LogManager.getLogger(CmdLineParser.class);
    private static final DirectoryStream.Filter<Path> onlySupporter =
            path -> Files.isDirectory(path)
                    || FilenameUtils.getExtension(path.getFileName().toString())
                    .toLowerCase().startsWith("htm");
    private final Options options;

    private final Option helpOption = Option.builder("h")
            .longOpt("help")
            .desc("Display this help")
            .build();

    private final Option waitOption = Option.builder("w")
            .longOpt("wait")
            .hasArg()
            .argName("sec.")
            .desc("Set waiting timeout. Default - 60 sec.")
            .build();

    private final Option triesOption = Option.builder("t")
            .longOpt("tries")
            .hasArg()
            .argName("count")
            .desc("Set tries count before give up and skip download. Default - 3")
            .build();

    private final Option reverseOption = Option.builder("r")
            .longOpt("reverse")
            .desc("Reverse mode - convert back to original URLs")
            .build();

    private final Option externalHostOption = Option.builder("e")
            .longOpt("external")
            .hasArg()
            .argName("hostname or IP")
            .desc("remote host name for SSH connection")
            .build();

    private final Option externalUserNameOption = Option.builder("u")
            .longOpt("user")
            .hasArg()
            .argName("username")
            .desc("remote host username")
            .build();

    private final Option externalPasswordOption = Option.builder("p")
            .longOpt("password")
            .hasArg()
            .argName("password")
            .desc("remote host password")
            .build();

    private final Option externalKeyFileOption = Option.builder("k")
            .longOpt("key")
            .hasArg()
            .argName("path")
            .desc("remote host SSH keyfile")
            .build();

    CmdLineParser() {
        options = new Options();
        options.addOption(helpOption);
        options.addOption(waitOption);
        options.addOption(triesOption);
        options.addOption(reverseOption);
        options.addOption(externalHostOption);
        options.addOption(externalUserNameOption);
        options.addOption(externalPasswordOption);
        options.addOption(externalKeyFileOption);
    }

    ParsedCmdline parse(String[] args) {

        ParsedCmdline parsedCmdline = new ParsedCmdline();
        parsedCmdline.setShowHelp(false);

        try {
            DefaultParser parser = new DefaultParser();
            CommandLine commandLine = parser.parse(options, args);

            boolean help = commandLine.hasOption(this.helpOption.getOpt());
            parsedCmdline.setShowHelp(help);

            if (help)
                return parsedCmdline;

            boolean reverseMode = commandLine.hasOption(this.reverseOption.getOpt());
            parsedCmdline.setReverseMode(reverseMode);

            List<String> rawInputFiles = commandLine.getArgList();
            if (rawInputFiles == null || rawInputFiles.isEmpty())
                throw new ParseException("Input html files required");

            List<Path> inputFiles = new ArrayList<>(rawInputFiles.size());
            for (String rawInputFile : rawInputFiles) {
                Path inputFile = Paths.get(rawInputFile);
                if (Files.notExists(inputFile))
                    throw new ParseException("Input file not found: " + rawInputFile);
                if (Files.isDirectory(inputFile)) {
                    dirScanner(inputFile, inputFiles, reverseMode);
                } else if (Files.isRegularFile(inputFile)) {
                    if (!FilenameUtils.getExtension(rawInputFile).toLowerCase().startsWith("htm"))
                        throw new ParseException("Input file is not supported: " + rawInputFile);
                    if (alreadyNotConverted(inputFile, reverseMode))
                        inputFiles.add(inputFile);
                } else {
                    throw new ParseException("Input file is not a regular file: " + rawInputFile);
                }
            }
            Collections.sort(inputFiles);
            parsedCmdline.setInputFiles(inputFiles);

            String rawTries = commandLine.getOptionValue(this.triesOption.getOpt(), "3");
            int tries;
            try {
                tries = Integer.parseInt(rawTries);
            } catch (NumberFormatException nfe) {
                throw new ParseException("Unable to parse tries count: " + rawTries);
            }

            if (tries < 1)
                throw new ParseException("Tries count cannot be less that 1");

            parsedCmdline.setTries(tries);

            String rawTimeout = commandLine.getOptionValue(this.waitOption.getOpt(), "60");
            int timeout;
            try {
                timeout = Integer.parseInt(rawTimeout);
            } catch (NumberFormatException nfe) {
                throw new ParseException("Unable to parse timeout in seconds: " + rawTimeout);
            }

            if (timeout < 1)
                throw new ParseException("Timeout cannot be less that 1 second");

            parsedCmdline.setTimeout(timeout);

            String remoteHostName = commandLine.getOptionValue(this.externalHostOption.getOpt());
            if (remoteHostName != null) {
                String[] remoteHostParts = remoteHostName.split(":");
                String externalHostName = remoteHostParts[0];
                int externalPort = 22;
                if (remoteHostParts.length >= 2) {
                    try {
                        externalPort = Integer.parseInt(remoteHostParts[1]);
                    } catch (NumberFormatException nfe) {
                        throw new ParseException("Unable to parse port number in remote hostname: " + remoteHostParts[1]);
                    }
                }
                parsedCmdline.setExternalHost(externalHostName);
                parsedCmdline.setExternalPort(externalPort);
            }

            String remoteUserName = commandLine.getOptionValue(this.externalUserNameOption.getOpt());
            parsedCmdline.setExternalUserName(remoteUserName);

            String remotePassword = commandLine.getOptionValue(this.externalPasswordOption.getOpt());
            parsedCmdline.setExternalPassword(remotePassword);

            String remoteKeyFile = commandLine.getOptionValue(this.externalKeyFileOption.getOpt());
            if (remoteKeyFile != null) {
                Path remoteKeyPath = Paths.get(remoteKeyFile);
                if (Files.isRegularFile(remoteKeyPath)) {
                    parsedCmdline.setExternalKeyFile(remoteKeyPath);
                } else {
                    throw new ParseException("Unable to find SSH keyfile: " + remoteKeyFile);
                }
            }
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
            log.error(parsedCmdline.getParseException().getMessage());
            System.exit(2);
        }

        if (parsedCmdline.isShowHelp()) {
            displayHelp();
            System.exit(0);
        }
    }

    private void dirScanner(Path inputFile, List<Path> capacitor, boolean reverseMode) throws ParseException {
        if (Files.isDirectory(inputFile)) {
            if (inputFile.getFileName().toString().equals(RESOURCES_PATH_NAME)) {
                log.warn("Skipping directory \"{}\" as resources directory", inputFile);
                return;
            }
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(inputFile, onlySupporter)) {
                for (Path path : dirStream) {
                    if (Files.isDirectory(path)) {
                        dirScanner(path, capacitor, reverseMode);
                    } else {
                        if (alreadyNotConverted(path, reverseMode))
                            capacitor.add(path);
                    }
                }
            } catch (IOException err) {
                throw new ParseException("Unable to enumerate directory " + inputFile + ": " + err.getMessage());
            }
        }
    }

    private boolean alreadyNotConverted(Path inputFile, boolean reverseMode) {
        if (NamesUtils.isDownloadedName(inputFile)) {
            if (!reverseMode) {
                log.info("This file is already converted version: \"{}\", skipping",
                        inputFile.toString());
                return false;
            } else {
                Path origPath = NamesUtils.getOrigPath(inputFile);
                if (Files.exists(origPath)) {
                    log.info("This file already has reversed version: \"{}\" - \"{}\", skipping",
                            inputFile.toString(), origPath.getFileName().toString());
                    return false;
                }
                Path resourceDir = inputFile.resolveSibling(RESOURCES_PATH_NAME);
                if (!Files.exists(resourceDir) && !Files.isDirectory(resourceDir)) {
                    log.info("Resources directory for file \"{}\" - \"{}\" not found, skipping",
                            inputFile.toString(), resourceDir.toString());
                    return false;
                }
                Path stateFile = resourceDir.resolve(STATE_FILE_NAME);
                if (!Files.exists(stateFile) && !Files.isRegularFile(stateFile)) {
                    log.info("State file for file \"{}\" - \"{}\", not found into resource directory",
                            inputFile.toString(), stateFile.toString());
                    return false;
                }
            }
            return true;
        }

        if (!reverseMode) {
            Path alreadyConvertedPath = NamesUtils.getDownloadPath(inputFile);
            if (Files.exists(alreadyConvertedPath)) {
                log.info("This file already has converted version: \"{}\" - \"{}\", skipping",
                        inputFile.toString(), alreadyConvertedPath.getFileName().toString());
                return false;
            }
        } else {
            log.info("This file is original or reversed version: \"{}\", skipping",
                    inputFile.toString());
            return false;
        }
        return true;
    }
}
