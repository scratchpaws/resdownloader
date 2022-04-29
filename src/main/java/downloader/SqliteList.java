package downloader;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;

public class SqliteList
        extends SqliteCollection {

    private static final String CREATE_LIST_TABLE_QUERY = "create table `%%` (`value` text not null)";
    private static final String CHECK_EXISTS_TEMPLATE = "select count(1) from `%%` where `value` = ?";
    private static final String INSERT_TEMPLATE = "insert into `%%` (`value`) values (?)";

    private final String countQuery;
    private final String insertQuery;

    public SqliteList(@NotNull final Connection connection,
                      @NotNull final String objectName) {
        super(connection, objectName);
        createTable(CREATE_LIST_TABLE_QUERY);
        countQuery = CHECK_EXISTS_TEMPLATE.replace("%%", objectName);
        insertQuery = INSERT_TEMPLATE.replace("%%", objectName);
    }

    public boolean contains(@NotNull final String value) {
        return super.checkExists(countQuery, value);
    }

    public boolean contains(final int value) {
        return contains(String.valueOf(value));
    }

    public void add(@NotNull final String value) {
        if (!contains(value)) {
            try (PreparedStatement stat = super.sqlite.prepareStatement(insertQuery)) {
                stat.setString(1, value);
                stat.executeUpdate();
            } catch (SQLException err) {
                log.error("Unable to add value \"" + value + "\" to table \"" + objectName + "\": " + err.getMessage());
                throw new RuntimeException(err);
            }
        }
    }

    public void add(final int value) {
        if (!contains(value)) {
            add(String.valueOf(value));
        }
    }

    public void addAll(@NotNull final Collection<? extends String> collection) {
        collection.forEach(this::add);
    }
}
