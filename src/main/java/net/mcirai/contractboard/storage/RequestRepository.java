package net.mcirai.contractboard.storage;

import net.mcirai.contractboard.model.Request;
import net.mcirai.contractboard.model.RequestStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RequestRepository {

    private final Database database;

    public RequestRepository(Database database) {
        this.database = database;
    }

    public Request insert(UUID requesterId, String requesterName, String title, String description,
                           double reward, long createdAt, long expiresAt, int minStars,
                           boolean itemDelivery) throws SQLException {
        String sql = """
            INSERT INTO requests (requester_id, requester_name, title, description, reward,
                created_at, expires_at, status, rated, min_stars, item_delivery)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
            """;
        Connection connection = database.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, requesterId.toString());
            statement.setString(2, requesterName);
            statement.setString(3, title);
            statement.setString(4, description);
            statement.setDouble(5, reward);
            statement.setLong(6, createdAt);
            statement.setLong(7, expiresAt);
            statement.setString(8, RequestStatus.OPEN.name());
            statement.setInt(9, minStars);
            statement.setInt(10, itemDelivery ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                int id = keys.getInt(1);
                return new Request(id, requesterId, requesterName, title, description, reward,
                        createdAt, expiresAt, RequestStatus.OPEN, null, null, false, 0, 0, false, minStars,
                        itemDelivery);
            }
        }
    }

    public List<Request> findByStatus(RequestStatus status) throws SQLException {
        String sql = "SELECT * FROM requests WHERE status = ? ORDER BY created_at DESC";
        List<Request> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, status.name());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public List<Request> findByRequester(UUID requesterId) throws SQLException {
        String sql = "SELECT * FROM requests WHERE requester_id = ? ORDER BY created_at DESC";
        List<Request> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, requesterId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public List<Request> findByWorker(UUID workerId) throws SQLException {
        String sql = "SELECT * FROM requests WHERE worker_id = ? ORDER BY created_at DESC";
        List<Request> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, workerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public Request findById(int id) throws SQLException {
        String sql = "SELECT * FROM requests WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    public List<Request> findExpiredOpen(long now) throws SQLException {
        String sql = "SELECT * FROM requests WHERE status = ? AND expires_at <= ?";
        List<Request> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.OPEN.name());
            statement.setLong(2, now);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public void assignWorker(int id, UUID workerId, String workerName, long acceptedAt) throws SQLException {
        String sql = """
            UPDATE requests SET worker_id = ?, worker_name = ?, status = ?,
                accepted_at = ?, delivered_at = NULL, reminder_sent = 0
            WHERE id = ?
            """;
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, workerId.toString());
            statement.setString(2, workerName);
            statement.setString(3, RequestStatus.ACCEPTED.name());
            statement.setLong(4, acceptedAt);
            statement.setInt(5, id);
            statement.executeUpdate();
        }
    }

    public void clearWorker(int id) throws SQLException {
        String sql = """
            UPDATE requests SET worker_id = NULL, worker_name = NULL, status = ?,
                accepted_at = NULL, delivered_at = NULL, reminder_sent = 0
            WHERE id = ?
            """;
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.OPEN.name());
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    public boolean markDelivered(int id, long deliveredAt) throws SQLException {
        String sql = """
            UPDATE requests SET status = ?, delivered_at = ?, reminder_sent = 0
            WHERE id = ? AND status = ?
            """;
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.DELIVERED.name());
            statement.setLong(2, deliveredAt);
            statement.setInt(3, id);
            statement.setString(4, RequestStatus.ACCEPTED.name());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean forceRevert(int id, long acceptedBefore) throws SQLException {
        String sql = """
            UPDATE requests SET worker_id = NULL, worker_name = NULL, status = ?,
                accepted_at = NULL, delivered_at = NULL, reminder_sent = 0
            WHERE id = ? AND status = ? AND accepted_at <= ?
            """;
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.OPEN.name());
            statement.setInt(2, id);
            statement.setString(3, RequestStatus.ACCEPTED.name());
            statement.setLong(4, acceptedBefore);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean markExpired(int id, long closedAt) throws SQLException {
        String sql = "UPDATE requests SET status = ?, closed_at = ? WHERE id = ? AND status = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.EXPIRED.name());
            statement.setLong(2, closedAt);
            statement.setInt(3, id);
            statement.setString(4, RequestStatus.OPEN.name());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean markCompleted(int id, long closedAt) throws SQLException {
        String sql = "UPDATE requests SET status = ?, closed_at = ? WHERE id = ? AND (status = ? OR status = ?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.COMPLETED.name());
            statement.setLong(2, closedAt);
            statement.setInt(3, id);
            statement.setString(4, RequestStatus.ACCEPTED.name());
            statement.setString(5, RequestStatus.DELIVERED.name());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean markWithdrawn(int id, long closedAt) throws SQLException {
        String sql = "UPDATE requests SET status = ?, closed_at = ? WHERE id = ? AND status = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.WITHDRAWN.name());
            statement.setLong(2, closedAt);
            statement.setInt(3, id);
            statement.setString(4, RequestStatus.OPEN.name());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean autoApprove(int id, long closedAt) throws SQLException {
        String sql = "UPDATE requests SET status = ?, closed_at = ? WHERE id = ? AND status = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.COMPLETED.name());
            statement.setLong(2, closedAt);
            statement.setInt(3, id);
            statement.setString(4, RequestStatus.DELIVERED.name());
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * 納品報告済みの依頼を受注中へ戻す(依頼者による差し戻し)。
     * accepted_at を現在時刻に振り直すことで、受注放置の強制差し戻しタイマーもやり直しになる。
     */
    public boolean revertToAccepted(int id, long acceptedAt) throws SQLException {
        String sql = """
            UPDATE requests SET status = ?, accepted_at = ?, delivered_at = NULL, reminder_sent = 0
            WHERE id = ? AND status = ?
            """;
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.ACCEPTED.name());
            statement.setLong(2, acceptedAt);
            statement.setInt(3, id);
            statement.setString(4, RequestStatus.DELIVERED.name());
            return statement.executeUpdate() > 0;
        }
    }

    /** 運営による強制終了。募集中・受注中・納品報告済みのいずれからでも取り下げ扱いで閉じる。 */
    public boolean adminCancel(int id, long closedAt) throws SQLException {
        String sql = """
            UPDATE requests SET status = ?, closed_at = ?
            WHERE id = ? AND status IN (?, ?, ?)
            """;
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.WITHDRAWN.name());
            statement.setLong(2, closedAt);
            statement.setInt(3, id);
            statement.setString(4, RequestStatus.OPEN.name());
            statement.setString(5, RequestStatus.ACCEPTED.name());
            statement.setString(6, RequestStatus.DELIVERED.name());
            return statement.executeUpdate() > 0;
        }
    }

    public int deleteClosedBefore(long closedBefore) throws SQLException {
        String sql = """
            DELETE FROM requests WHERE status IN (?, ?, ?) AND closed_at <= ?
            """;
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.COMPLETED.name());
            statement.setString(2, RequestStatus.WITHDRAWN.name());
            statement.setString(3, RequestStatus.EXPIRED.name());
            statement.setLong(4, closedBefore);
            return statement.executeUpdate();
        }
    }

    public void markReminderSent(int id) throws SQLException {
        String sql = "UPDATE requests SET reminder_sent = 1 WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    public List<Request> findStaleDelivered(long deliveredBefore) throws SQLException {
        String sql = "SELECT * FROM requests WHERE status = ? AND delivered_at <= ?";
        List<Request> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.DELIVERED.name());
            statement.setLong(2, deliveredBefore);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public List<Request> findDeliveredNeedingReminder(long deliveredBefore) throws SQLException {
        String sql = "SELECT * FROM requests WHERE status = ? AND reminder_sent = 0 AND delivered_at <= ?";
        List<Request> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.DELIVERED.name());
            statement.setLong(2, deliveredBefore);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public List<Request> findAcceptedNeedingReminder(long acceptedBefore) throws SQLException {
        String sql = "SELECT * FROM requests WHERE status = ? AND reminder_sent = 0 AND accepted_at <= ?";
        List<Request> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.ACCEPTED.name());
            statement.setLong(2, acceptedBefore);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public void markRated(int id) throws SQLException {
        String sql = "UPDATE requests SET rated = 1 WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    private Request map(ResultSet rs) throws SQLException {
        String workerIdStr = rs.getString("worker_id");
        return new Request(
                rs.getInt("id"),
                UUID.fromString(rs.getString("requester_id")),
                rs.getString("requester_name"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getDouble("reward"),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                RequestStatus.valueOf(rs.getString("status")),
                workerIdStr == null ? null : UUID.fromString(workerIdStr),
                rs.getString("worker_name"),
                rs.getInt("rated") == 1,
                rs.getLong("accepted_at"),
                rs.getLong("delivered_at"),
                rs.getInt("reminder_sent") == 1,
                rs.getInt("min_stars"),
                rs.getInt("item_delivery") == 1
        );
    }
}
