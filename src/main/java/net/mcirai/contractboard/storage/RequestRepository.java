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
                           double reward, long createdAt, long expiresAt) throws SQLException {
        String sql = """
            INSERT INTO requests (requester_id, requester_name, title, description, reward,
                created_at, expires_at, status, rated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
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
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                int id = keys.getInt(1);
                return new Request(id, requesterId, requesterName, title, description, reward,
                        createdAt, expiresAt, RequestStatus.OPEN, null, null, false);
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

    public void updateStatus(int id, RequestStatus status) throws SQLException {
        String sql = "UPDATE requests SET status = ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    public void assignWorker(int id, UUID workerId, String workerName) throws SQLException {
        String sql = "UPDATE requests SET worker_id = ?, worker_name = ?, status = ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, workerId.toString());
            statement.setString(2, workerName);
            statement.setString(3, RequestStatus.ACCEPTED.name());
            statement.setInt(4, id);
            statement.executeUpdate();
        }
    }

    public void clearWorker(int id) throws SQLException {
        String sql = "UPDATE requests SET worker_id = NULL, worker_name = NULL, status = ? WHERE id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, RequestStatus.OPEN.name());
            statement.setInt(2, id);
            statement.executeUpdate();
        }
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
                rs.getInt("rated") == 1
        );
    }
}
