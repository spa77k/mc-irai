package net.mcirai.contractboard.storage;

import net.mcirai.contractboard.model.VaultItem;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VaultRepository {

    private final Database database;

    public VaultRepository(Database database) {
        this.database = database;
    }

    public VaultItem insert(UUID ownerUuid, String ownerName, String itemData, int amount, String reason,
                             long createdAt) throws SQLException {
        String sql = """
            INSERT INTO vault_items (owner_uuid, owner_name, item_data, amount, reason, created_at, warn_stage)
            VALUES (?, ?, ?, ?, ?, ?, 0)
            """;
        try (PreparedStatement statement = database.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, ownerUuid.toString());
            statement.setString(2, ownerName);
            statement.setString(3, itemData);
            statement.setInt(4, amount);
            statement.setString(5, reason);
            statement.setLong(6, createdAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return new VaultItem(keys.getInt(1), ownerUuid, ownerName, itemData, amount, reason, createdAt, 0);
            }
        }
    }

    public List<VaultItem> findByOwner(UUID ownerUuid) throws SQLException {
        String sql = "SELECT * FROM vault_items WHERE owner_uuid = ? ORDER BY created_at ASC";
        List<VaultItem> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public int countByOwner(UUID ownerUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM vault_items WHERE owner_uuid = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void updateRemainder(int id, String itemData, int amount) throws SQLException {
        String sql = "UPDATE vault_items SET item_data = ?, amount = ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, itemData);
            statement.setInt(2, amount);
            statement.setInt(3, id);
            statement.executeUpdate();
        }
    }

    public void deleteById(int id) throws SQLException {
        String sql = "DELETE FROM vault_items WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    /** 保管期限が近い(warn_stage未満の段階に入った)アイテムを取り出す。 */
    public List<VaultItem> findNeedingWarning(long createdBefore, int stage) throws SQLException {
        String sql = "SELECT * FROM vault_items WHERE created_at <= ? AND warn_stage < ?";
        List<VaultItem> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setLong(1, createdBefore);
            statement.setInt(2, stage);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public void markWarned(int id, int stage) throws SQLException {
        String sql = "UPDATE vault_items SET warn_stage = ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, stage);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    public List<VaultItem> findExpired(long createdBefore) throws SQLException {
        String sql = "SELECT * FROM vault_items WHERE created_at <= ?";
        List<VaultItem> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setLong(1, createdBefore);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    private VaultItem map(ResultSet rs) throws SQLException {
        return new VaultItem(
                rs.getInt("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                rs.getString("item_data"),
                rs.getInt("amount"),
                rs.getString("reason"),
                rs.getLong("created_at"),
                rs.getInt("warn_stage"));
    }
}
