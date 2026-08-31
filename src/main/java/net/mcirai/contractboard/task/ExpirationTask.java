package net.mcirai.contractboard.task;

import net.mcirai.contractboard.RequestService;
import org.bukkit.scheduler.BukkitRunnable;

public class ExpirationTask extends BukkitRunnable {

    private final RequestService requestService;

    public ExpirationTask(RequestService requestService) {
        this.requestService = requestService;
    }

    @Override
    public void run() {
        requestService.processExpired();
        requestService.processAcceptedTimeouts();
        requestService.purgeOldRequests();
        requestService.processVaultRetention();
    }
}
