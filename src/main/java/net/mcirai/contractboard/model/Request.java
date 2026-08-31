package net.mcirai.contractboard.model;

import java.util.UUID;

public class Request {

    private final int id;
    private final UUID requesterId;
    private final String requesterName;
    private String title;
    private String description;
    private double reward;
    private final long createdAt;
    private final long expiresAt;
    private RequestStatus status;
    private UUID workerId;
    private String workerName;
    private boolean rated;
    private long acceptedAt;
    private long deliveredAt;
    private boolean reminderSent;
    private final int minStars;
    private final boolean itemDelivery;

    public Request(int id, UUID requesterId, String requesterName, String title, String description,
                    double reward, long createdAt, long expiresAt, RequestStatus status,
                    UUID workerId, String workerName, boolean rated,
                    long acceptedAt, long deliveredAt, boolean reminderSent, int minStars,
                    boolean itemDelivery) {
        this.id = id;
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.title = title;
        this.description = description;
        this.reward = reward;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.workerId = workerId;
        this.workerName = workerName;
        this.rated = rated;
        this.acceptedAt = acceptedAt;
        this.deliveredAt = deliveredAt;
        this.reminderSent = reminderSent;
        this.minStars = minStars;
        this.itemDelivery = itemDelivery;
    }

    public int getId() {
        return id;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public double getReward() {
        return reward;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public void setWorkerId(UUID workerId) {
        this.workerId = workerId;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public boolean isRated() {
        return rated;
    }

    public void setRated(boolean rated) {
        this.rated = rated;
    }

    public boolean isExpired() {
        return status == RequestStatus.OPEN && System.currentTimeMillis() >= expiresAt;
    }

    public long getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(long acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public long getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(long deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public boolean isReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(boolean reminderSent) {
        this.reminderSent = reminderSent;
    }

    public int getMinStars() {
        return minStars;
    }

    public boolean isItemDelivery() {
        return itemDelivery;
    }

    /** 納品ボックスが固定される状態か。納品報告後は受注者が中身を出し入れできない。 */
    public boolean isBoxLocked() {
        return status != RequestStatus.ACCEPTED;
    }
}
