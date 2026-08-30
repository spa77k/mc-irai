package net.mcirai.contractboard.session;

import org.bukkit.conversations.Conversation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final Map<UUID, CreateRequestSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Conversation> conversations = new ConcurrentHashMap<>();

    public void start(UUID playerId) {
        sessions.put(playerId, new CreateRequestSession());
    }

    public CreateRequestSession get(UUID playerId) {
        return sessions.get(playerId);
    }

    public boolean has(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void attach(UUID playerId, Conversation conversation) {
        conversations.put(playerId, conversation);
    }

    public void end(UUID playerId) {
        sessions.remove(playerId);
        Conversation conversation = conversations.remove(playerId);
        if (conversation != null) {
            conversation.abandon();
        }
    }
}
