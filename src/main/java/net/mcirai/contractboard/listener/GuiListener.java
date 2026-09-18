package net.mcirai.contractboard.listener;

import net.mcirai.contractboard.RequestService;
import net.mcirai.contractboard.gui.ConfirmHolder;
import net.mcirai.contractboard.gui.CreateConfirmHolder;
import net.mcirai.contractboard.gui.DeliveryBoxHolder;
import net.mcirai.contractboard.gui.GuiManager;
import net.mcirai.contractboard.gui.MainMenuHolder;
import net.mcirai.contractboard.gui.MyRequestsHolder;
import net.mcirai.contractboard.gui.RatingHolder;
import net.mcirai.contractboard.gui.RequestDetailHolder;
import net.mcirai.contractboard.gui.RequestListHolder;
import net.mcirai.contractboard.gui.VaultHolder;
import net.mcirai.contractboard.model.Request;
import net.mcirai.contractboard.model.RequestStatus;
import net.mcirai.contractboard.session.CreateRequestConversation;
import net.mcirai.contractboard.session.CreateRequestSession;
import net.mcirai.contractboard.session.SessionManager;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GuiListener implements Listener {

    private final GuiManager guiManager;
    private final RequestService requestService;
    private final CreateRequestConversation createRequestConversation;
    private final SessionManager sessionManager;
    private final MessageUtil messages;
    private final Plugin plugin;

    public GuiListener(GuiManager guiManager, RequestService requestService,
                        CreateRequestConversation createRequestConversation, SessionManager sessionManager,
                        MessageUtil messages, Plugin plugin) {
        this.guiManager = guiManager;
        this.requestService = requestService;
        this.createRequestConversation = createRequestConversation;
        this.sessionManager = sessionManager;
        this.messages = messages;
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 納品ボックスだけは出し入れを許すため、先に分岐してイベントをキャンセルしない
        if (holder instanceof DeliveryBoxHolder boxHolder) {
            handleDeliveryBoxClick(event, player, boxHolder);
            return;
        }

        if (!(holder instanceof MainMenuHolder) && !(holder instanceof RequestListHolder)
                && !(holder instanceof RequestDetailHolder) && !(holder instanceof MyRequestsHolder)
                && !(holder instanceof RatingHolder) && !(holder instanceof VaultHolder)
                && !(holder instanceof ConfirmHolder)
                && !(holder instanceof CreateConfirmHolder)) {
            return;
        }
        event.setCancelled(true);
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
        } else if (holder instanceof VaultHolder vaultHolder) {
            handleVault(player, vaultHolder, slot);
        } else if (holder instanceof ConfirmHolder confirmHolder) {
            handleConfirm(player, confirmHolder, slot);
        } else if (holder instanceof CreateConfirmHolder) {
            handleCreateConfirm(player, event.getInventory(), slot, event.isRightClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof DeliveryBoxHolder boxHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!boxHolder.isEditable()) {
            event.setCancelled(true);
            return;
        }
        boolean touchesBox = event.getRawSlots().stream()
                .anyMatch(slot -> slot < event.getInventory().getSize());
        if (touchesBox) {
            scheduleBoxSync(player, boxHolder);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CreateConfirmHolder
                && event.getPlayer() instanceof Player player) {
            // 作成・破棄ではなく単に閉じただけなら入力内容は残し、戻り方を案内する
            CreateRequestSession session = sessionManager.get(player.getUniqueId());
            if (session != null && session.isAwaitingConfirm()) {
                messages.send(player, "create.confirm-closed");
            }
            return;
        }
        if (!(event.getInventory().getHolder() instanceof DeliveryBoxHolder boxHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player) || !boxHolder.isEditable()) {
            return;
        }
        syncBox(player, boxHolder, event.getInventory());
    }

    // ---------------------------------------------------------------
    // 納品ボックス
    // ---------------------------------------------------------------

    private void handleDeliveryBoxClick(InventoryClickEvent event, Player player, DeliveryBoxHolder holder) {
        if (!holder.isEditable()) {
            event.setCancelled(true);
            return;
        }
        // 出し入れは許可し、変更後の中身は次tickで検査・保存する
        scheduleBoxSync(player, holder);
    }

    private void scheduleBoxSync(Player player, DeliveryBoxHolder holder) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory open = player.getOpenInventory().getTopInventory();
            if (open.getHolder() != holder) {
                // 既に別の画面へ移っている場合はクローズ側の保存に任せる
                return;
            }
            syncBox(player, holder, open);
        });
    }

    /**
     * 納品ボックスの中身を検査してDBへ書き戻す。
     * 依頼が受注中でなくなっている場合は保存しない(既に保管庫へ移した中身を二重に復活させないため)。
     */
    private void syncBox(Player player, DeliveryBoxHolder holder, Inventory inventory) {
        Request request = requestService.findRequest(holder.getRequestId());
        if (request == null || request.getStatus() != RequestStatus.ACCEPTED
                || !player.getUniqueId().equals(request.getWorkerId())) {
            messages.send(player, "box.no-longer-editable");
            return;
        }
        removeBannedItems(player, inventory);
        requestService.saveBoxContents(holder.getRequestId(), inventory);
    }

    /** 持ち込み禁止アイテムはボックスから抜き、本人へ返す(返しきれない分は保管庫へ)。 */
    private void removeBannedItems(Player player, Inventory inventory) {
        List<ItemStack> banned = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (requestService.isMaterialBanned(item.getType())) {
                banned.add(item.clone());
                inventory.setItem(slot, null);
            }
        }
        if (banned.isEmpty()) {
            return;
        }
        for (ItemStack item : banned) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack remaining : leftover.values()) {
                requestService.depositToVault(player, remaining, "持ち込み不可アイテムの返却");
            }
        }
        messages.send(player, "box.banned-material");
    }

    // ---------------------------------------------------------------

    private void handleMainMenu(Player player, int slot) {
        if (slot == MainMenuHolder.SLOT_LIST) {
            guiManager.openRequestList(player, 0);
        } else if (slot == MainMenuHolder.SLOT_CREATE) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> createRequestConversation.start(player));
        } else if (slot == MainMenuHolder.SLOT_MY) {
            guiManager.openMyRequests(player);
        } else if (slot == MainMenuHolder.SLOT_VAULT) {
            guiManager.openVault(player, 0);
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
            return;
        }
        if (slot == RequestDetailHolder.SLOT_BOX) {
            openBox(player, holder.getRequestId());
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
            case GuiManager.ACTION_APPROVE -> guiManager.openRating(player, requestId);
            // 取り消せない操作は確認画面を挟む
            case GuiManager.ACTION_WITHDRAW, GuiManager.ACTION_GIVE_UP, GuiManager.ACTION_FORCE_REVERT ->
                    guiManager.openConfirm(player, requestId, action);
            case GuiManager.ACTION_DELIVER -> {
                requestService.markDelivered(player, requestId);
                guiManager.openMyRequests(player);
            }
            case GuiManager.ACTION_REVISION -> {
                requestService.requestRevision(player, requestId);
                guiManager.openMyRequests(player);
            }
            case GuiManager.ACTION_OPEN_BOX, GuiManager.ACTION_VIEW_BOX -> openBox(player, requestId);
            default -> {
            }
        }
    }

    /** 納品ボックスを開く。編集できるのは受注中の受注者本人だけ。 */
    private void openBox(Player player, int requestId) {
        Request request = requestService.findRequest(requestId);
        if (request == null || !request.isItemDelivery()) {
            return;
        }
        boolean isWorker = player.getUniqueId().equals(request.getWorkerId());
        boolean isRequester = player.getUniqueId().equals(request.getRequesterId());
        if (!isWorker && !isRequester) {
            return;
        }
        boolean editable = isWorker && request.getStatus() == RequestStatus.ACCEPTED;
        guiManager.openDeliveryBox(player, requestId, editable);
    }

    private void handleVault(Player player, VaultHolder holder, int slot) {
        if (slot == VaultHolder.SLOT_BACK) {
            guiManager.openMainMenu(player);
            return;
        }
        if (slot == VaultHolder.SLOT_PREV) {
            guiManager.openVault(player, holder.getPage() - 1);
            return;
        }
        if (slot == VaultHolder.SLOT_NEXT) {
            guiManager.openVault(player, holder.getPage() + 1);
            return;
        }
        Integer vaultId = holder.getSlotToVaultId().get(slot);
        if (vaultId != null) {
            requestService.receiveVaultItem(player, vaultId);
            guiManager.openVault(player, holder.getPage());
        }
    }

    private void handleConfirm(Player player, ConfirmHolder holder, int slot) {
        if (slot == ConfirmHolder.SLOT_CANCEL) {
            guiManager.openMyRequests(player);
            return;
        }
        if (slot != ConfirmHolder.SLOT_CONFIRM) {
            return;
        }
        int requestId = holder.getRequestId();
        switch (holder.getAction()) {
            case GuiManager.ACTION_WITHDRAW -> requestService.withdrawRequest(player, requestId);
            case GuiManager.ACTION_GIVE_UP -> requestService.giveUpRequest(player, requestId);
            case GuiManager.ACTION_FORCE_REVERT -> requestService.forceRevert(player, requestId);
            default -> {
            }
        }
        guiManager.openMyRequests(player);
    }

    private void handleCreateConfirm(Player player, Inventory inventory, int slot, boolean rightClick) {
        CreateRequestSession session = sessionManager.get(player.getUniqueId());
        if (session == null || !session.isAwaitingConfirm()) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case CreateConfirmHolder.SLOT_ITEM_DELIVERY -> {
                session.setItemDelivery(!session.isItemDelivery());
                guiManager.renderCreateConfirm(inventory, player, session);
            }
            case CreateConfirmHolder.SLOT_MIN_STARS -> {
                int maxMinStars = plugin.getConfig().getInt("request.min-stars-max", 5);
                if (maxMinStars <= 0) {
                    return;
                }
                // 0〜上限を循環させる。左クリックで増やし、右クリックで減らす
                int range = maxMinStars + 1;
                int next = ((session.getMinStars() + (rightClick ? -1 : 1)) % range + range) % range;
                session.setMinStars(next);
                guiManager.renderCreateConfirm(inventory, player, session);
            }
            case CreateConfirmHolder.SLOT_CANCEL -> {
                sessionManager.end(player.getUniqueId());
                player.closeInventory();
                messages.send(player, "create.cancelled-input");
            }
            case CreateConfirmHolder.SLOT_RESTART -> {
                sessionManager.end(player.getUniqueId());
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> createRequestConversation.start(player));
            }
            case CreateConfirmHolder.SLOT_CREATE -> {
                boolean created = requestService.createRequest(player, session.getTitle(),
                        session.getDescription(), session.getReward(), session.getExpireHours(),
                        session.getMinStars(), session.isItemDelivery());
                if (created) {
                    // セッションを先に消しておくと、閉じたときの「入力内容は残っています」案内が出ない
                    sessionManager.end(player.getUniqueId());
                    player.closeInventory();
                } else {
                    // 所持金不足などで作成できなかった場合は、最新の所持金を反映して画面に留まる
                    guiManager.renderCreateConfirm(inventory, player, session);
                }
            }
            default -> {
            }
        }
    }

    private void handleRating(Player player, RatingHolder holder, int slot) {
        int[] starSlots = RatingHolder.STAR_SLOTS;
        for (int i = 0; i < starSlots.length; i++) {
            if (starSlots[i] == slot) {
                requestService.completeRequest(player, holder.getRequestId(), i + 1);
                guiManager.openMyRequests(player);
                return;
            }
        }
    }
}
