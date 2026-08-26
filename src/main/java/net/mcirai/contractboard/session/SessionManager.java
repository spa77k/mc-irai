package net.mcirai.contractboard.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final Map<UUID, CreateRequestSession> sessions = new ConcurrentHashMap<>();

    public void start(UUID playerId) {
        sessions.put(playerId, new CreateRequestSession());
    }

    public CreateRequestSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    public boolean has(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void end(UUID playerId) {
        sessions.remove(playerId);
    }
}
