package net.mcirai.contractboard.storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
                    rated INTEGER NOT NULL DEFAULT 0,
                    accepted_at INTEGER,
                    delivered_at INTEGER,
                    reminder_sent INTEGER NOT NULL DEFAULT 0
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
            statement.execute("""
                CREATE TABLE IF NOT EXISTS delivery_box_items (
                    request_id INTEGER NOT NULL,
                    slot INTEGER NOT NULL,
                    item_data TEXT NOT NULL,
                    PRIMARY KEY (request_id, slot)
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS vault_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    owner_name TEXT NOT NULL,
                    item_data TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    warn_stage INTEGER NOT NULL DEFAULT 0
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    message TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_vault_items_owner "
                    + "ON vault_items(owner_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_notifications_owner "
                    + "ON notifications(owner_uuid)");
        }
        ensureColumn("requests", "accepted_at", "INTEGER");
        ensureColumn("requests", "delivered_at", "INTEGER");
        ensureColumn("requests", "reminder_sent", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("requests", "closed_at", "INTEGER");
        ensureColumn("requests", "min_stars", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("requests", "item_delivery", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("vault_items", "warn_stage", "INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureColumn(String table, String column, String definition) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
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
