package net.mcirai.contractboard.model;

import java.util.UUID;

public class Rating {

    private final int requestId;
    private final UUID raterId;
    private final UUID ratedId;
    private final int stars;
    private final String comment;
    private final long createdAt;

    public Rating(int requestId, UUID raterId, UUID ratedId, int stars, String comment, long createdAt) {
        this.requestId = requestId;
        this.raterId = raterId;
        this.ratedId = ratedId;
        this.stars = stars;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public int getRequestId() {
        return requestId;
    }

    public UUID getRaterId() {
        return raterId;
    }

    public UUID getRatedId() {
        return ratedId;
    }

    public int getStars() {
        return stars;
    }

    public String getComment() {
        return comment;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
