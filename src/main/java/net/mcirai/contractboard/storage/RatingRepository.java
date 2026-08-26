package net.mcirai.contractboard.storage;

import net.mcirai.contractboard.model.Rating;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RatingRepository {

    private final Database database;

    public RatingRepository(Database database) {
        this.database = database;
    }

    public void insert(Rating rating) throws SQLException {
        String sql = """
            INSERT INTO ratings (request_id, rater_id, rated_id, stars, comment, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, rating.getRequestId());
            statement.setString(2, rating.getRaterId().toString());
            statement.setString(3, rating.getRatedId().toString());
            statement.setInt(4, rating.getStars());
            statement.setString(5, rating.getComment());
            statement.setLong(6, rating.getCreatedAt());
            statement.executeUpdate();
        }
    }

    public List<Rating> findByRated(UUID ratedId) throws SQLException {
        String sql = "SELECT * FROM ratings WHERE rated_id = ? ORDER BY created_at DESC";
        List<Rating> result = new ArrayList<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ratedId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public double averageStars(UUID ratedId) throws SQLException {
        String sql = "SELECT AVG(stars) AS avg_stars FROM ratings WHERE rated_id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ratedId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("avg_stars");
                }
            }
        }
        return 0.0;
    }

    public int countByRated(UUID ratedId) throws SQLException {
        String sql = "SELECT COUNT(*) AS cnt FROM ratings WHERE rated_id = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, ratedId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt");
                }
            }
        }
        return 0;
    }

    private Rating map(ResultSet rs) throws SQLException {
        return new Rating(
                rs.getInt("request_id"),
                UUID.fromString(rs.getString("rater_id")),
                UUID.fromString(rs.getString("rated_id")),
                rs.getInt("stars"),
                rs.getString("comment"),
                rs.getLong("created_at")
        );
    }
}
