package net.mcirai.contractboard.storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private final Plugin plugin;
    private Connection connection;

    public Database(Plugin plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, "irai.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    requester_id TEXT NOT NULL,
                    requester_name TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    reward REAL NOT NULL,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    worker_id TEXT,
                    worker_name TEXT,
                    rated INTEGER NOT NULL DEFAULT 0
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS ratings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    request_id INTEGER NOT NULL,
                    rater_id TEXT NOT NULL,
                    rated_id TEXT NOT NULL,
                    stars INTEGER NOT NULL,
                    comment TEXT,
                    created_at INTEGER NOT NULL
                )
                """);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("データベースのクローズに失敗しました: " + e.getMessage());
            }
        }
    }
}
