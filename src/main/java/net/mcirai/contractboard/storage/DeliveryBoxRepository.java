package net.mcirai.contractboard.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class DeliveryBoxRepository {

    private final Database database;

    public DeliveryBoxRepository(Database database) {
        this.database = database;
    }

    /** スロット番号 -> シリアライズ済みアイテム。空スロットは含まれない。 */
    public Map<Integer, String> findByRequest(int requestId) throws SQLException {
        String sql = "SELECT slot, item_data FROM delivery_box_items WHERE request_id = ? ORDER BY slot ASC";
        Map<Integer, String> result = new LinkedHashMap<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("slot"), rs.getString("item_data"));
                }
            }
        }
        return result;
    }

    public boolean isEmpty(int requestId) throws SQLException {
        String sql = "SELECT 1 FROM delivery_box_items WHERE request_id = ? LIMIT 1";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                return !rs.next();
            }
        }
    }

    /**
     * 依頼の納品ボックスの中身を丸ごと置き換える。
     * 途中でサーバーが落ちても中身が半分だけ消えないよう、削除と挿入を1トランザクションにまとめる。
     */
    public void replaceAll(int requestId, Map<Integer, String> contents) throws SQLException {
        Connection connection = database.getConnection();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement delete =
                         connection.prepareStatement("DELETE FROM delivery_box_items WHERE request_id = ?")) {
                delete.setInt(1, requestId);
                delete.executeUpdate();
            }
            String insertSql = "INSERT INTO delivery_box_items (request_id, slot, item_data) VALUES (?, ?, ?)";
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                for (Map.Entry<Integer, String> entry : contents.entrySet()) {
                    insert.setInt(1, requestId);
                    insert.setInt(2, entry.getKey());
                    insert.setString(3, entry.getValue());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public void clear(int requestId) throws SQLException {
        String sql = "DELETE FROM delivery_box_items WHERE request_id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, requestId);
            statement.executeUpdate();
        }
    }
}
