package downloader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;

public class CmdLineParser {

    private CommandLineParser parser = new DefaultParser();

    public CmdLineParser() {

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
    }
}
