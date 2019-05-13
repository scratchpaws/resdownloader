package downloader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.LogManager;

public class LogConfigurer {

    public void configure() {

        Properties config = new Properties();
        config.setProperty("java.util.logging.SimpleFormatter.format",
                "<%1$tF %<tT.%<tL> <%4$s> <%3$s> %5$s %6$s%n");
        config.setProperty("handlers",
                "java.util.logging.ConsoleHandler");
        config.setProperty(".level",
                "INFO");
        config.setProperty("java.util.logging.ConsoleHandler.level",
                "INFO");
        config.setProperty("java.util.logging.ConsoleHandler.formatter",
                "java.util.logging.SimpleFormatter");

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            config.store(buffer, "Generated configuration");
            buffer.flush();

            try (ByteArrayInputStream loader_from_buffer = new ByteArrayInputStream(buffer.toByteArray())) {

                LogManager.getLogManager().readConfiguration(loader_from_buffer);
            } catch (IOException load_err) {
                System.err.println("ERROR: unable to load logger configuration data: " + load_err.getMessage());
            }
        } catch (IOException save_err) {
            System.err.println("ERROR: unable generate logger configuration data: " + save_err.getMessage());
        }
    }
}
