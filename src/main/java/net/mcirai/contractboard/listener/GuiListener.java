package net.mcirai.contractboard.listener;

import net.mcirai.contractboard.RequestService;
import net.mcirai.contractboard.gui.GuiManager;
import net.mcirai.contractboard.gui.MainMenuHolder;
import net.mcirai.contractboard.gui.MyRequestsHolder;
import net.mcirai.contractboard.gui.RatingHolder;
import net.mcirai.contractboard.gui.RequestDetailHolder;
import net.mcirai.contractboard.gui.RequestListHolder;
import net.mcirai.contractboard.session.SessionManager;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    private final GuiManager guiManager;
    private final RequestService requestService;
    private final SessionManager sessionManager;
    private final MessageUtil messages;

    public GuiListener(GuiManager guiManager, RequestService requestService,
                        SessionManager sessionManager, MessageUtil messages) {
        this.guiManager = guiManager;
        this.requestService = requestService;
        this.sessionManager = sessionManager;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MainMenuHolder) && !(holder instanceof RequestListHolder)
                && !(holder instanceof RequestDetailHolder) && !(holder instanceof MyRequestsHolder)
                && !(holder instanceof RatingHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        if (holder instanceof MainMenuHolder) {
            handleMainMenu(player, slot);
        } else if (holder instanceof RequestListHolder listHolder) {
            handleRequestList(player, listHolder, slot);
        } else if (holder instanceof RequestDetailHolder detailHolder) {
            handleRequestDetail(player, detailHolder, slot);
        } else if (holder instanceof MyRequestsHolder myHolder) {
            handleMyRequests(player, myHolder, slot);
        } else if (holder instanceof RatingHolder ratingHolder) {
            handleRating(player, ratingHolder, slot);
        }
    }

    private void handleMainMenu(Player player, int slot) {
        if (slot == MainMenuHolder.SLOT_LIST) {
            guiManager.openRequestList(player, 0);
        } else if (slot == MainMenuHolder.SLOT_CREATE) {
            player.closeInventory();
            sessionManager.start(player.getUniqueId());
            messages.send(player, "create.start");
            messages.send(player, "create.ask-title");
        } else if (slot == MainMenuHolder.SLOT_MY) {
            guiManager.openMyRequests(player);
        }
    }

    private void handleRequestList(Player player, RequestListHolder holder, int slot) {
        if (slot == RequestListHolder.SLOT_BACK) {
            guiManager.openMainMenu(player);
            return;
        }
        if (slot == RequestListHolder.SLOT_PREV) {
            guiManager.openRequestList(player, holder.getPage() - 1);
            return;
        }
        if (slot == RequestListHolder.SLOT_NEXT) {
            guiManager.openRequestList(player, holder.getPage() + 1);
            return;
        }
        Integer requestId = holder.getSlotToRequestId().get(slot);
        if (requestId != null) {
            guiManager.openRequestDetail(player, requestId, true);
        }
    }

    private void handleRequestDetail(Player player, RequestDetailHolder holder, int slot) {
        if (slot == RequestDetailHolder.SLOT_BACK) {
            if (holder.isFromList()) {
                guiManager.openRequestList(player, 0);
            } else {
                guiManager.openMainMenu(player);
            }
            return;
        }
        if (slot == RequestDetailHolder.SLOT_ACCEPT) {
            requestService.acceptRequest(player, holder.getRequestId());
            guiManager.openMainMenu(player);
        }
    }

    private void handleMyRequests(Player player, MyRequestsHolder holder, int slot) {
        if (slot == MyRequestsHolder.SLOT_BACK) {
            guiManager.openMainMenu(player);
            return;
        }
        int[] data = holder.getSlotActions().get(slot);
        if (data == null) {
            return;
        }
        int requestId = data[0];
        int action = data[1];
        switch (action) {
            case 1 -> {
                boolean success = requestService.completeRequest(player, requestId);
                if (success) {
                    guiManager.openRating(player, requestId);
                } else {
                    guiManager.openMyRequests(player);
                }
            }
            case 2 -> {
                requestService.withdrawRequest(player, requestId);
                guiManager.openMyRequests(player);
            }
            case 3 -> {
                requestService.giveUpRequest(player, requestId);
                guiManager.openMyRequests(player);
            }
            case 4 -> {
                requestService.markDelivered(player, requestId);
                guiManager.openMyRequests(player);
            }
            case 5 -> {
                requestService.forceRevert(player, requestId);
                guiManager.openMyRequests(player);
            }
            default -> {
            }
        }
    }

    private void handleRating(Player player, RatingHolder holder, int slot) {
        int[] starSlots = RatingHolder.STAR_SLOTS;
        for (int i = 0; i < starSlots.length; i++) {
            if (starSlots[i] == slot) {
                requestService.rate(player, holder.getRequestId(), i + 1, null);
                player.closeInventory();
                return;
            }
        }
    }
}
