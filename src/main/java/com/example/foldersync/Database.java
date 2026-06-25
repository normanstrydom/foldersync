package com.example.foldersync;

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
                    + "last_modified INTEGER\n"
                    + ");");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_path ON files_copied(path);");
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

    public void upsertRecord(String relPath, String checksum, long lastModified) {
        try (PreparedStatement p = conn.prepareStatement(
                "INSERT INTO files_copied(path, checksum, last_modified) VALUES(?,?,?)"
                        + " ON CONFLICT(path) DO UPDATE SET checksum=excluded.checksum, last_modified=excluded.last_modified")) {
            p.setString(1, relPath);
            p.setString(2, checksum);
            p.setLong(3, lastModified);
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
