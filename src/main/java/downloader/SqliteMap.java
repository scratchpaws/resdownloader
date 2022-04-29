package downloader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

public class SqliteMap
        extends SqliteCollection {

    private static final String CREATE_MAP_TABLE_QUERY = "create table `%%` (`name` text not null primary key, `value` text not null)";
    private static final String CHECK_EXISTS_BY_KEY_TEMPLATE = "select count(1) from `%%` where `name` = ?";
    private static final String CHECK_EXISTS_BY_VALUE_TEMPLATE = "select count(1) from `%%` where `value` = ?";
    private static final String INSERT_QUERY_TEMPLATE = "insert into `%%` (`name`, `value`) values (?, ?)";
    private static final String UPDATE_QUERY_TEMPLATE = "update `%%` set `value` = ? where `name` = ?";
    private static final String GET_QUERY_TEMPLATE = "select `value` from `%%` where `name` = ?";
    private static final String GET_BY_VALUE_QUERY_TEMPLATE = "select `name` from `%%` where `value` = ?";

    private final String countByKeyQuery;
    private final String countByValueQuery;
    private final String insertQuery;
    private final String updateQuery;
    private final String getQuery;
    private final String getByValueQuery;

    public SqliteMap(@NotNull final Connection connection,
                     @NotNull final String objectName) {
        super(connection, objectName);
        createTable(CREATE_MAP_TABLE_QUERY);
        countByKeyQuery = CHECK_EXISTS_BY_KEY_TEMPLATE.replace("%%", objectName);
        countByValueQuery = CHECK_EXISTS_BY_VALUE_TEMPLATE.replace("%%", objectName);
        insertQuery = INSERT_QUERY_TEMPLATE.replace("%%", objectName);
        updateQuery = UPDATE_QUERY_TEMPLATE.replace("%%", objectName);
        getQuery = GET_QUERY_TEMPLATE.replace("%%", objectName);
        getByValueQuery = GET_BY_VALUE_QUERY_TEMPLATE.replace("%%", objectName);
    }

    public boolean containsKey(@NotNull final String key) {
        return super.checkExists(countByKeyQuery, key);
    }

    public boolean containsValue(@NotNull final String value) {
        return super.checkExists(countByValueQuery, value);
    }

    public void put(@NotNull final String key, @NotNull final String value) {
        if (containsKey(key)) {
            try (PreparedStatement stat = super.sqlite.prepareStatement(updateQuery)) {
                stat.setString(1, value);
                stat.setString(2, key);
                stat.executeUpdate();
            } catch (SQLException err) {
                log.error("Unable to update value \"" + value + "\" by key \"" + key + "\" in table \"" + objectName + "\": " + err.getMessage());
                throw new RuntimeException(err);
            }
        } else {
            try (PreparedStatement stat = super.sqlite.prepareStatement(insertQuery)) {
                stat.setString(1, key);
                stat.setString(2, value);
                stat.executeUpdate();
            } catch (SQLException err) {
                log.error("Unable to add value \"" + key + "\",\"" + value + "\" to table \"" + objectName + "\": " + err.getMessage());
                throw new RuntimeException(err);
            }
        }
    }

    public void putAll(@NotNull final Map<? extends String, ? extends String> map) {
        map.forEach(this::put);
    }

    @Nullable
    public String get(@NotNull final String key) {
        return super.getValue(getQuery, key);
    }

    @Nullable
    public String getByValue(@NotNull final String value) {
        return super.getValue(getByValueQuery, value);
    }
}
