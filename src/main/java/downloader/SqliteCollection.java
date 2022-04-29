package downloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class SqliteCollection {

    protected Connection sqlite;
    protected String objectName;
    protected final Logger log;

    private static final String CHECK_EXISTS_TABLE_QUERY = "select count(1) from sqlite_schema where type = 'table' and name = ?";

    public SqliteCollection(@NotNull final Connection connection,
                            @NotNull final String objectName) {
        this.sqlite = connection;
        this.objectName = objectName;
        this.log = LogManager.getLogger(objectName);
    }

    private boolean checkExistsTable() {
        try (PreparedStatement stat = sqlite.prepareStatement(CHECK_EXISTS_TABLE_QUERY)) {
            stat.setString(1, objectName);
            try (ResultSet rs = stat.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException err) {
            log.error(String.format("Unable to check that table \"%s\" is exists: %s", objectName, err.getMessage()));
            throw new RuntimeException(err);
        }
    }

    protected void createTable(@NotNull final String queryTemplate) {
        if (!checkExistsTable()) {
            String query = queryTemplate.replace("%%", objectName);
            try (PreparedStatement stat = sqlite.prepareStatement(query)) {
                stat.executeUpdate();
            } catch (SQLException err) {
                log.error(String.format("Unable to create table \"%s\": %s", objectName, err.getMessage()));
                throw new RuntimeException(err);
            }
        }
    }

    protected boolean checkExists(@NotNull final String countQuery,
                                  @NotNull final String value) {
        try (PreparedStatement stat = sqlite.prepareStatement(countQuery)) {
            stat.setString(1, value);
            try (ResultSet rs = stat.executeQuery()) {
                return (rs.next() && rs.getInt(1) > 0);
            }
        } catch (SQLException err) {
            log.error("Unable to check value \"" + value + "\" in table \"" + objectName + "\": " + err.getMessage());
            throw new RuntimeException(err);
        }
    }

    @Nullable
    protected String getValue(@NotNull final String query,
                              @NotNull final String key) {
        try (PreparedStatement stat = sqlite.prepareStatement(query)) {
            stat.setString(1, key);
            try (ResultSet rs = stat.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException err) {
            log.error("Unable to get value by \"" + key + "\" in table \"" + objectName + "\": " + err.getMessage());
            throw new RuntimeException(err);
        }
        return null;
    }
}
