package za.co.felixsol.util.foldersync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public class Database implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Database.class);
    private final Connection conn;

    private Database(Connection conn) {
        this.conn = conn;
    }

    public static Database forDestination(Path destDir) {
        try {
            Files.createDirectories(destDir);
            Path dbPath = destDir.resolve("foldersync.db");
            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            Connection c = DriverManager.getConnection(url);
            Database db = new Database(c);
            db.init();
            return db;
        } catch (Exception e) {
            throw new RuntimeException("Unable to open database", e);
        }
    }

    private void init() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS files_copied (\n"
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                    + "path TEXT NOT NULL,\n"
                    + "checksum TEXT,\n"
                    + "last_modified INTEGER,\n"
                    + "last_modified_text TEXT\n"
                    + ");");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_path ON files_copied(path);");
            // Ensure column exists for older DBs (SQLite allows ALTER TABLE ADD COLUMN)
            try (ResultSet rs = s.executeQuery("PRAGMA table_info(files_copied);")) {
                boolean hasLastModifiedText = false;
                while (rs.next()) {
                    String col = rs.getString("name");
                    if ("last_modified_text".equalsIgnoreCase(col)) {
                        hasLastModifiedText = true;
                        break;
                    }
                }
                if (!hasLastModifiedText) {
                    try (Statement s2 = conn.createStatement()) {
                        s2.execute("ALTER TABLE files_copied ADD COLUMN last_modified_text TEXT;");
                    }
                }
            }
        }
    }

    public boolean hasRecord(String relPath) {
        try (PreparedStatement p = conn.prepareStatement("SELECT 1 FROM files_copied WHERE path = ? LIMIT 1")) {
            p.setString(1, relPath);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOG.warn("DB check failed", e);
            return false;
        }
    }

    public static class FileRecord {
        public final String checksum;
        public final long lastModified;
        public final String lastModifiedText;

        public FileRecord(String checksum, long lastModified, String lastModifiedText) {
            this.checksum = checksum;
            this.lastModified = lastModified;
            this.lastModifiedText = lastModifiedText;
        }
    }

    public FileRecord getRecord(String relPath) {
        try (PreparedStatement p = conn.prepareStatement("SELECT checksum, last_modified, last_modified_text FROM files_copied WHERE path = ? LIMIT 1")) {
            p.setString(1, relPath);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    String checksum = rs.getString("checksum");
                    long lastModified = rs.getLong("last_modified");
                    String lastModifiedText = rs.getString("last_modified_text");
                    return new FileRecord(checksum, lastModified, lastModifiedText);
                }
            }
        } catch (SQLException e) {
            LOG.warn("DB read failed", e);
        }
        return null;
    }

    public void upsertRecord(String relPath, String checksum, long lastModified, String lastModifiedText) {
        String sql = "INSERT INTO files_copied(path, checksum, last_modified, last_modified_text) VALUES(?,?,?,?)"
                + " ON CONFLICT(path) DO UPDATE SET checksum=excluded.checksum, last_modified=excluded.last_modified, last_modified_text=excluded.last_modified_text";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, relPath);
            p.setString(2, checksum);
            p.setLong(3, lastModified);
            p.setString(4, lastModifiedText);
            p.executeUpdate();
        } catch (SQLException e) {
            LOG.warn("DB upsert failed", e);
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            LOG.warn("Error closing DB", e);
        }
    }
}
