package net.mcirai.contractboard.listener;

import net.mcirai.contractboard.RequestService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final RequestService requestService;

    public PlayerJoinListener(RequestService requestService) {
        this.requestService = requestService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        requestService.deliverQueuedNotifications(event.getPlayer());
    }
}
