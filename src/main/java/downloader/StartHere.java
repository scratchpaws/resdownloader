package downloader;

import java.util.logging.Logger;

public class StartHere {

    private static final Logger log = Logger.getLogger("MAIN");

    public static void main(String ...args) {
        new LogConfigurer().configure();

        log.info("Starting...");

        for (String item : args) {

        }
    }
}
