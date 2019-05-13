package downloader;

import org.apache.commons.cli.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CmdLineParser {

    private Options options;

    public CmdLineParser() {

        options = new Options();

        Option help = Option.builder("h")
                .longOpt("help")
                .desc("Display this help")
                .build();

        Option base = Option.builder("p")
                .longOpt("parent")
                .hasArg()
                .argName("dir")
                .desc("Set parent path or resources")
                .build();

        options.addOption(help);
        options.addOption(base);
    }

    public ParsedCmdline parse(String[] args) {

        ParsedCmdline parsedCmdline = new ParsedCmdline();
        parsedCmdline.setShowHelp(false);

        try {
            DefaultParser parser = new DefaultParser();
            CommandLine commandLine = parser.parse(options, args);

            boolean help = commandLine.hasOption("h");
            parsedCmdline.setShowHelp(help);

            String rawParentPath = commandLine.getOptionValue("p", "./");
            Path parentPath = Paths.get(rawParentPath);
            parsedCmdline.setParentDir(parentPath);

            if (Files.notExists(parentPath))
                throw new ParseException("Parent path not exists: " + rawParentPath);
            if (!Files.isDirectory(parentPath))
                throw new ParseException("Parent path is not a directory: " + rawParentPath);

            List<String> rawInputFiles = commandLine.getArgList();
            if (rawInputFiles == null || rawInputFiles.isEmpty())
                throw new ParseException("Input html files required");

            List<Path> inputFiles = new ArrayList<>(rawInputFiles.size());
            for (String rawInputFile : rawInputFiles) {
                Path inputFile = Paths.get(rawInputFile);

            }
        } catch (ParseException err) {
            parsedCmdline.setParseException(err);
        }

        return parsedCmdline;
    }
}
