package downloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class SqliteHolder
    implements Closeable, AutoCloseable {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final Logger log = LogManager.getLogger(SqliteHolder.class.getSimpleName());
    private final Map<Path, Connection> connections = new HashMap<>();

    public SqliteState getConnection(@NotNull final Path sqliteLocation) {
        if (!connections.containsKey(sqliteLocation)) {
            try {
                String jdbcUrl = JDBC_PREFIX + sqliteLocation.toString().replace('\\', '/');
                Connection connection = DriverManager.getConnection(jdbcUrl);
                connections.put(sqliteLocation, connection);
            } catch (SQLException err) {
                throw new RuntimeException("Unable to open/create database \"" + sqliteLocation + "\": " + err.getMessage());
            }
        }
        return new SqliteState(connections.get(sqliteLocation));
    }

    @Override
    public void close() {
        boolean hasError = false;
        for (Map.Entry<Path, Connection> entry : connections.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception err) {
                log.error("Unable to close connection \"" + entry.getKey() + "\": " + err.getMessage());
                hasError = true;
            }
        }
        if (hasError) {
            throw new RuntimeException("Unable to close one or more connections");
        }
    }
}
