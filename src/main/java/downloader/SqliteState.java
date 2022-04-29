package downloader;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;

public class SqliteState {

    private final SqliteMap converted;
    private final SqliteMap urlFileHashes;
    private final SqliteList failed;
    private final SqliteList errCodesImages;

    public SqliteState(@NotNull final Connection sqlite) {
        this.converted = new SqliteMap(sqlite, "converted");
        this.urlFileHashes = new SqliteMap(sqlite, "file_hashes");
        this.failed = new SqliteList(sqlite, "fails");
        this.errCodesImages = new SqliteList(sqlite, "err_codes");
    }

    public SqliteMap getConverted() {
        return converted;
    }

    public SqliteMap getUrlFileHashes() {
        return urlFileHashes;
    }

    public SqliteList getFailed() {
        return failed;
    }

    public SqliteList getErrCodesImages() {
        return errCodesImages;
    }
}
