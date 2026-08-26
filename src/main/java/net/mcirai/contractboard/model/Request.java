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

    public Request(int id, UUID requesterId, String requesterName, String title, String description,
                    double reward, long createdAt, long expiresAt, RequestStatus status,
                    UUID workerId, String workerName, boolean rated) {
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
}
