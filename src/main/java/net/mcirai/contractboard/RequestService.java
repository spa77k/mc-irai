package net.mcirai.contractboard;

import net.mcirai.contractboard.economy.EconomyService;
import net.mcirai.contractboard.event.ContractCreatedEvent;
import net.mcirai.contractboard.gui.DeliveryBoxHolder;
import net.mcirai.contractboard.model.Notification;
import net.mcirai.contractboard.model.Rating;
import net.mcirai.contractboard.model.Request;
import net.mcirai.contractboard.model.RequestStatus;
import net.mcirai.contractboard.model.VaultItem;
import net.mcirai.contractboard.storage.DeliveryBoxRepository;
import net.mcirai.contractboard.storage.NotificationRepository;
import net.mcirai.contractboard.storage.RatingRepository;
import net.mcirai.contractboard.storage.RequestRepository;
import net.mcirai.contractboard.storage.VaultRepository;
import net.mcirai.contractboard.util.ItemSerialization;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestService {

    /** 納品ボックスのスロット数(チェスト1個分)。 */
    public static final int BOX_SIZE = 27;

    private final RequestRepository requestRepository;
    private final RatingRepository ratingRepository;
    private final DeliveryBoxRepository deliveryBoxRepository;
    private final VaultRepository vaultRepository;
    private final NotificationRepository notificationRepository;
    private final EconomyService economyService;
    private final MessageUtil messages;
    private final FileConfiguration config;
    private final Logger logger;

    public RequestService(RequestRepository requestRepository, RatingRepository ratingRepository,
                           DeliveryBoxRepository deliveryBoxRepository, VaultRepository vaultRepository,
                           NotificationRepository notificationRepository, EconomyService economyService,
                           MessageUtil messages, FileConfiguration config, Logger logger) {
        this.requestRepository = requestRepository;
        this.ratingRepository = ratingRepository;
        this.deliveryBoxRepository = deliveryBoxRepository;
        this.vaultRepository = vaultRepository;
        this.notificationRepository = notificationRepository;
        this.economyService = economyService;
        this.messages = messages;
        this.config = config;
        this.logger = logger;
    }

    // ---------------------------------------------------------------
    // 依頼のライフサイクル
    // ---------------------------------------------------------------

    public boolean createRequest(Player requester, String title, String description,
                                  double reward, int expireHours, int minStars, boolean itemDelivery) {
        if (!economyService.isReady()) {
            messages.send(requester, "economy-not-found");
            return false;
        }

        double feeRate = config.getDouble("request.fee-rate", 0.0);
        double fee = reward * feeRate;
        double total = reward + fee;

        if (!economyService.has(requester, total)) {
            messages.send(requester, "create.insufficient-funds", Map.of(
                    "amount", economyService.format(total),
                    "reward", economyService.format(reward),
                    "fee", economyService.format(fee)));
            return false;
        }
        if (!economyService.withdraw(requester, total)) {
            messages.send(requester, "create.insufficient-funds", Map.of(
                    "amount", economyService.format(total),
                    "reward", economyService.format(reward),
                    "fee", economyService.format(fee)));
            return false;
        }

        long now = System.currentTimeMillis();
        long expiresAt = now + expireHours * 3_600_000L;
        Request createdRequest;
        try {
            createdRequest = requestRepository.insert(requester.getUniqueId(), requester.getName(),
                    title, description, reward, now, expiresAt, minStars, itemDelivery);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼の作成に失敗しました", e);
            economyService.deposit(requester, total);
            requester.sendMessage(messages.get("prefix") + "§c依頼の作成に失敗しました。徴収額は返却されました。");
            return false;
        }

        // 保存と徴収が両方成功した後にだけ、外部連携向けの通知イベントを発火する
        Bukkit.getPluginManager().callEvent(new ContractCreatedEvent(createdRequest.getId(),
                requester.getUniqueId(), requester.getName(), title, reward, economyService.format(reward),
                expireHours, minStars, itemDelivery));

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("title", title);
        placeholders.put("amount", economyService.format(total));
        placeholders.put("reward", economyService.format(reward));
        placeholders.put("fee", economyService.format(fee));
        messages.send(requester, "create.completed", placeholders);
        if (itemDelivery) {
            messages.send(requester, "box.created-with-delivery");
        }
        return true;
    }

    public void acceptRequest(Player worker, int requestId) {
        Request request = findOrNull(requestId);
        if (request == null || request.getStatus() != RequestStatus.OPEN) {
            messages.send(worker, "accept.already-taken");
            return;
        }
        if (request.getRequesterId().equals(worker.getUniqueId())) {
            messages.send(worker, "accept.own-request");
            return;
        }
        try {
            if (request.getMinStars() > 0 && ratingRepository.countByRated(worker.getUniqueId()) > 0
                    && ratingRepository.averageStars(worker.getUniqueId()) < request.getMinStars()) {
                messages.send(worker, "accept.stars-too-low",
                        Map.of("min", String.valueOf(request.getMinStars())));
                return;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "評価情報の取得に失敗しました", e);
            return;
        }
        try {
            List<Request> alreadyAccepted = requestRepository.findByWorker(worker.getUniqueId());
            boolean hasActive = alreadyAccepted.stream()
                    .anyMatch(r -> r.getStatus() == RequestStatus.ACCEPTED);
            if (hasActive) {
                messages.send(worker, "accept.already-accepted-by-you");
                return;
            }
            requestRepository.assignWorker(requestId, worker.getUniqueId(), worker.getName(),
                    System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼の受注に失敗しました", e);
            return;
        }
        messages.send(worker, "accept.success", Map.of("title", request.getTitle()));
        if (request.isItemDelivery()) {
            messages.send(worker, "box.accepted-with-delivery");
        }
    }

    public void giveUpRequest(Player worker, int requestId) {
        Request request = findOrNull(requestId);
        if (request == null || request.getStatus() != RequestStatus.ACCEPTED
                || !worker.getUniqueId().equals(request.getWorkerId())) {
            return;
        }
        closeOpenBoxes(requestId);
        try {
            requestRepository.clearWorker(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "受注の取り消しに失敗しました", e);
            return;
        }
        returnBoxToWorker(request, "giveup");
        messages.send(worker, "giveup.success", Map.of("title", request.getTitle()));
    }

    public void markDelivered(Player worker, int requestId) {
        Request request = findOrNull(requestId);
        if (request == null || request.getStatus() != RequestStatus.ACCEPTED
                || !worker.getUniqueId().equals(request.getWorkerId())) {
            messages.send(worker, "deliver.not-accepted");
            return;
        }
        closeOpenBoxes(requestId);
        if (request.isItemDelivery() && isBoxEmpty(requestId)) {
            messages.send(worker, "box.empty-on-deliver");
            return;
        }
        boolean success;
        try {
            success = requestRepository.markDelivered(requestId, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "納品報告に失敗しました", e);
            return;
        }
        if (!success) {
            messages.send(worker, "deliver.not-accepted");
            return;
        }
        messages.send(worker, "deliver.success", Map.of("title", request.getTitle()));
        if (request.isItemDelivery()) {
            messages.send(worker, "box.locked");
        }
        notify(request.getRequesterId(), "deliver.notify-requester",
                Map.of("title", request.getTitle(), "worker", worker.getName()));
    }

    /** 依頼者が納品内容に納得できないとき、受注中へ差し戻してやり直させる。 */
    public void requestRevision(Player requester, int requestId) {
        Request request = findOrNull(requestId);
        if (request == null || request.getStatus() != RequestStatus.DELIVERED
                || !requester.getUniqueId().equals(request.getRequesterId())) {
            messages.send(requester, "revision.not-delivered");
            return;
        }
        boolean success;
        try {
            success = requestRepository.revertToAccepted(requestId, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "差し戻しに失敗しました", e);
            return;
        }
        if (!success) {
            messages.send(requester, "revision.not-delivered");
            return;
        }
        messages.send(requester, "revision.success", Map.of("title", request.getTitle()));
        notify(request.getWorkerId(), "revision.notify-worker", Map.of("title", request.getTitle()));
    }

    public void forceRevert(Player requester, int requestId) {
        Request request = findOrNull(requestId);
        if (request == null || request.getStatus() != RequestStatus.ACCEPTED
                || !requester.getUniqueId().equals(request.getRequesterId())) {
            return;
        }
        closeOpenBoxes(requestId);
        long autoApproveMillis = config.getLong("request.auto-approve-hours", 72) * 3_600_000L;
        long threshold = System.currentTimeMillis() - autoApproveMillis;
        boolean success;
        try {
            success = requestRepository.forceRevert(requestId, threshold);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "強制差し戻しに失敗しました", e);
            return;
        }
        if (!success) {
            messages.send(requester, "force-revert.not-eligible");
            return;
        }
        returnBoxToWorker(request, "force-revert");
        messages.send(requester, "force-revert.success", Map.of("title", request.getTitle()));
        notify(request.getWorkerId(), "force-revert.notify-worker", Map.of("title", request.getTitle()));
    }

    public boolean completeRequest(Player requester, int requestId, int stars) {
        Request request = findOrNull(requestId);
        if (request == null
                || (request.getStatus() != RequestStatus.ACCEPTED && request.getStatus() != RequestStatus.DELIVERED)
                || !requester.getUniqueId().equals(request.getRequesterId())) {
            messages.send(requester, "complete.not-accepted");
            return false;
        }
        closeOpenBoxes(requestId);
        boolean statusUpdated;
        try {
            statusUpdated = requestRepository.markCompleted(requestId, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼の完了処理に失敗しました", e);
            return false;
        }
        if (!statusUpdated) {
            // 既に他の操作で状態が変わっている(二重クリック等)。入金前なので何もせず終了する。
            messages.send(requester, "complete.not-accepted");
            return false;
        }
        if (!economyService.deposit(Bukkit.getOfflinePlayer(request.getWorkerId()), request.getReward())) {
            requester.sendMessage(messages.get("prefix") + "§c報酬の支払いに失敗しました。管理者に連絡してください。");
            return false;
        }
        int moved = moveBoxToVault(request, request.getRequesterId(), request.getRequesterName(),
                "依頼「" + request.getTitle() + "」の納品物");
        try {
            ratingRepository.insert(new Rating(requestId, requester.getUniqueId(), request.getWorkerId(),
                    stars, null, System.currentTimeMillis()));
            requestRepository.markRated(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "評価の登録に失敗しました", e);
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("title", request.getTitle());
        placeholders.put("amount", economyService.format(request.getReward()));
        placeholders.put("worker", request.getWorkerName());
        placeholders.put("stars", String.valueOf(stars));
        messages.send(requester, "complete.approved", placeholders);
        messages.send(requester, "rate.success", placeholders);
        if (moved > 0) {
            messages.send(requester, "box.moved-to-vault", Map.of("count", String.valueOf(moved)));
            warnIfVaultCrowded(requester);
        }
        notify(request.getWorkerId(), "complete.notify-worker", placeholders);
        return true;
    }

    public void withdrawRequest(Player requester, int requestId) {
        Request request = findOrNull(requestId);
        if (request == null || !requester.getUniqueId().equals(request.getRequesterId())) {
            return;
        }
        if (request.getStatus() != RequestStatus.OPEN) {
            messages.send(requester, "withdraw.already-accepted");
            return;
        }
        boolean statusUpdated;
        try {
            statusUpdated = requestRepository.markWithdrawn(requestId, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼の取り下げに失敗しました", e);
            return;
        }
        if (!statusUpdated) {
            // 既に他の操作で状態が変わっている(二重クリック等)。返金前なので何もせず終了する。
            messages.send(requester, "withdraw.already-accepted");
            return;
        }
        economyService.deposit(requester, request.getReward());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("title", request.getTitle());
        placeholders.put("amount", economyService.format(request.getReward()));
        messages.send(requester, "withdraw.success", placeholders);
    }

    /** 運営による強制終了。報酬は依頼者へ、納品ボックスの中身は受注者へ戻す。 */
    public boolean adminCancel(org.bukkit.command.CommandSender sender, int requestId) {
        Request request = findOrNull(requestId);
        if (request == null) {
            messages.send(sender, "admin.not-found", Map.of("id", String.valueOf(requestId)));
            return false;
        }
        closeOpenBoxes(requestId);
        boolean statusUpdated;
        try {
            statusUpdated = requestRepository.adminCancel(requestId, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼の強制終了に失敗しました", e);
            return false;
        }
        if (!statusUpdated) {
            messages.send(sender, "admin.not-cancellable", Map.of("id", String.valueOf(requestId)));
            return false;
        }
        if (!economyService.deposit(Bukkit.getOfflinePlayer(request.getRequesterId()), request.getReward())) {
            logger.warning("依頼ID " + requestId + " の強制終了で報酬の返還に失敗しました。手動対応が必要です。");
        }
        returnBoxToWorker(request, "admin-cancel");
        Map<String, String> placeholders = Map.of(
                "id", String.valueOf(requestId),
                "title", request.getTitle(),
                "amount", economyService.format(request.getReward()));
        messages.send(sender, "admin.cancelled", placeholders);
        notify(request.getRequesterId(), "admin.notify-requester", placeholders);
        if (request.getWorkerId() != null) {
            notify(request.getWorkerId(), "admin.notify-worker", placeholders);
        }
        return true;
    }

    // ---------------------------------------------------------------
    // 定期処理
    // ---------------------------------------------------------------

    public void processExpired() {
        long now = System.currentTimeMillis();
        List<Request> expired;
        try {
            expired = requestRepository.findExpiredOpen(now);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "期限切れ依頼の取得に失敗しました", e);
            return;
        }
        for (Request request : expired) {
            boolean statusUpdated;
            try {
                statusUpdated = requestRepository.markExpired(request.getId(), now);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "依頼の失効処理に失敗しました", e);
                continue;
            }
            if (!statusUpdated) {
                continue;
            }
            if (!economyService.deposit(
                    Bukkit.getOfflinePlayer(request.getRequesterId()), request.getReward())) {
                logger.warning("依頼ID " + request.getId() + " の期限切れ返還に失敗しました。管理者による手動対応が必要です。");
                continue;
            }
            notify(request.getRequesterId(), "expire.notify-requester", Map.of(
                    "title", request.getTitle(),
                    "amount", economyService.format(request.getReward())));
        }
    }

    public void processAcceptedTimeouts() {
        long now = System.currentTimeMillis();
        long autoApproveMillis = config.getLong("request.auto-approve-hours", 72) * 3_600_000L;
        long reminderLeadMillis = config.getLong("request.reminder-hours-before", 24) * 3_600_000L;
        long reminderThreshold = Math.max(0, autoApproveMillis - reminderLeadMillis);

        autoApproveStaleDeliveries(now - autoApproveMillis);
        remindDeliveredRequesters(now - reminderThreshold);
        remindIdleWorkers(now - reminderThreshold);
    }

    private void autoApproveStaleDeliveries(long deliveredBefore) {
        List<Request> stale;
        try {
            stale = requestRepository.findStaleDelivered(deliveredBefore);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "自動承認対象の取得に失敗しました", e);
            return;
        }
        for (Request request : stale) {
            boolean statusUpdated;
            try {
                statusUpdated = requestRepository.autoApprove(request.getId(), System.currentTimeMillis());
            } catch (SQLException e) {
                logger.log(Level.WARNING, "自動承認処理に失敗しました", e);
                continue;
            }
            if (!statusUpdated) {
                // 既に依頼者が手動承認済みなど。入金前なのでスキップする。
                continue;
            }
            if (!economyService.deposit(
                    Bukkit.getOfflinePlayer(request.getWorkerId()), request.getReward())) {
                logger.warning("依頼ID " + request.getId() + " の自動承認支払いに失敗しました。管理者による手動対応が必要です。");
                continue;
            }
            moveBoxToVault(request, request.getRequesterId(), request.getRequesterName(),
                    "依頼「" + request.getTitle() + "」の納品物");
            Map<String, String> placeholders = Map.of(
                    "title", request.getTitle(),
                    "amount", economyService.format(request.getReward()),
                    "worker", request.getWorkerName());
            notify(request.getRequesterId(), "auto-approve.notify-requester", placeholders);
            notify(request.getWorkerId(), "auto-approve.notify-worker", placeholders);
        }
    }

    private void remindDeliveredRequesters(long deliveredBefore) {
        List<Request> needingReminder;
        try {
            needingReminder = requestRepository.findDeliveredNeedingReminder(deliveredBefore);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "納品確認リマインドの取得に失敗しました", e);
            return;
        }
        int reminderHours = config.getInt("request.reminder-hours-before", 24);
        for (Request request : needingReminder) {
            notify(request.getRequesterId(), "auto-approve.reminder-requester", Map.of(
                    "title", request.getTitle(), "hours", String.valueOf(reminderHours)));
            markReminderSentQuietly(request.getId());
        }
    }

    private void remindIdleWorkers(long acceptedBefore) {
        List<Request> needingReminder;
        try {
            needingReminder = requestRepository.findAcceptedNeedingReminder(acceptedBefore);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "受注放置リマインドの取得に失敗しました", e);
            return;
        }
        int reminderHours = config.getInt("request.reminder-hours-before", 24);
        for (Request request : needingReminder) {
            notify(request.getWorkerId(), "force-revert.reminder-worker", Map.of(
                    "title", request.getTitle(), "hours", String.valueOf(reminderHours)));
            markReminderSentQuietly(request.getId());
        }
    }

    public void purgeOldRequests() {
        int retentionDays = config.getInt("request.retention-days", 30);
        if (retentionDays <= 0) {
            return;
        }
        long closedBefore = System.currentTimeMillis() - retentionDays * 86_400_000L;
        try {
            int deleted = requestRepository.deleteClosedBefore(closedBefore);
            if (deleted > 0) {
                logger.info("保持期間(" + retentionDays + "日)を過ぎた依頼を" + deleted + "件削除しました。");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "古い依頼の削除に失敗しました", e);
        }
    }

    /**
     * 保管庫の保持期限を処理する。予告なしに消えると事故になるため、
     * 削除の前に2段階(既定3日前・1日前)の警告を出してから削除する。
     */
    public void processVaultRetention() {
        int retentionDays = config.getInt("box.vault-retention-days", 30);
        if (retentionDays <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long retentionMillis = retentionDays * 86_400_000L;
        int firstWarnDays = config.getInt("box.vault-warn-days-before", 3);
        int finalWarnDays = config.getInt("box.vault-final-warn-days-before", 1);

        warnVaultItems(now - (retentionMillis - firstWarnDays * 86_400_000L), 1, firstWarnDays);
        warnVaultItems(now - (retentionMillis - finalWarnDays * 86_400_000L), 2, finalWarnDays);

        List<VaultItem> expired;
        try {
            expired = vaultRepository.findExpired(now - retentionMillis);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "保管期限切れアイテムの取得に失敗しました", e);
            return;
        }
        for (VaultItem item : expired) {
            try {
                vaultRepository.deleteById(item.getId());
            } catch (SQLException e) {
                logger.log(Level.WARNING, "保管期限切れアイテムの削除に失敗しました", e);
                continue;
            }
            logger.info("保管期限(" + retentionDays + "日)を過ぎた保管庫アイテムを削除しました(所有者: "
                    + item.getOwnerName() + " / 理由: " + item.getReason() + ")。");
            notify(item.getOwnerUuid(), "vault.expired", Map.of(
                    "reason", item.getReason(), "days", String.valueOf(retentionDays)));
        }
    }

    private void warnVaultItems(long createdBefore, int stage, int daysLeft) {
        List<VaultItem> targets;
        try {
            targets = vaultRepository.findNeedingWarning(createdBefore, stage);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "保管庫の期限警告対象の取得に失敗しました", e);
            return;
        }
        for (VaultItem item : targets) {
            notify(item.getOwnerUuid(), "vault.expiring", Map.of(
                    "reason", item.getReason(), "days", String.valueOf(daysLeft)));
            try {
                vaultRepository.markWarned(item.getId(), stage);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "保管庫の期限警告フラグの更新に失敗しました", e);
            }
        }
    }

    // ---------------------------------------------------------------
    // 納品ボックス
    // ---------------------------------------------------------------

    /** DBに保存された納品ボックスの中身をスロット番号付きで返す。 */
    public Map<Integer, ItemStack> loadBoxContents(int requestId) {
        Map<Integer, ItemStack> result = new LinkedHashMap<>();
        Map<Integer, String> raw;
        try {
            raw = deliveryBoxRepository.findByRequest(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "納品ボックスの取得に失敗しました", e);
            return result;
        }
        for (Map.Entry<Integer, String> entry : raw.entrySet()) {
            ItemSerialization.deserialize(entry.getValue(), logger,
                            "delivery_box_items#" + requestId + ":" + entry.getKey())
                    .ifPresent(item -> result.put(entry.getKey(), item));
        }
        return result;
    }

    /** 納品ボックスGUIの現在の中身をそのままDBへ書き戻す。スロット変更のたびに呼ばれる。 */
    public void saveBoxContents(int requestId, Inventory inventory) {
        Map<Integer, String> contents = new LinkedHashMap<>();
        for (int slot = 0; slot < Math.min(BOX_SIZE, inventory.getSize()); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            contents.put(slot, ItemSerialization.serialize(item));
        }
        try {
            deliveryBoxRepository.replaceAll(requestId, contents);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "納品ボックスの保存に失敗しました(依頼ID " + requestId + ")", e);
        }
    }

    public boolean isBoxEmpty(int requestId) {
        try {
            return deliveryBoxRepository.isEmpty(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "納品ボックスの確認に失敗しました", e);
            // 確認できないときは納品報告を止める側に倒す
            return true;
        }
    }

    /**
     * 指定の依頼の納品ボックスを開いている全員の画面を閉じる。
     * 閉じる処理の中で中身がDBへ保存されるため、状態を変える前に必ず呼ぶ。
     */
    private void closeOpenBoxes(int requestId) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = online.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof DeliveryBoxHolder box && box.getRequestId() == requestId) {
                online.closeInventory();
            }
        }
    }

    public boolean isMaterialBanned(Material material) {
        return config.getStringList("box.banned-materials").contains(material.name());
    }

    /** 納品ボックスの中身を指定プレイヤーの保管庫へ移し、ボックスを空にする。移した件数を返す。 */
    private int moveBoxToVault(Request request, UUID ownerUuid, String ownerName, String reason) {
        Map<Integer, ItemStack> contents = loadBoxContents(request.getId());
        if (contents.isEmpty()) {
            clearBoxQuietly(request.getId());
            return 0;
        }
        int moved = 0;
        for (ItemStack item : contents.values()) {
            try {
                vaultRepository.insert(ownerUuid, ownerName, ItemSerialization.serialize(item),
                        item.getAmount(), reason, System.currentTimeMillis());
                moved++;
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "保管庫への移動に失敗しました(依頼ID " + request.getId()
                        + ")。納品ボックスは中身を残したままにします。", e);
                return moved;
            }
        }
        clearBoxQuietly(request.getId());
        return moved;
    }

    /** 依頼が受注者の手を離れるとき、納品ボックスの中身を受注者の保管庫へ戻す。 */
    private void returnBoxToWorker(Request request, String context) {
        if (request.getWorkerId() == null) {
            return;
        }
        String workerName = request.getWorkerName() == null
                ? request.getWorkerId().toString() : request.getWorkerName();
        int moved = moveBoxToVault(request, request.getWorkerId(), workerName,
                "依頼「" + request.getTitle() + "」の返却");
        if (moved > 0) {
            notify(request.getWorkerId(), "box.returned", Map.of(
                    "title", request.getTitle(), "count", String.valueOf(moved)));
            logger.info("依頼ID " + request.getId() + " の納品ボックス" + moved + "件を受注者へ返却しました("
                    + context + ")。");
        }
    }

    private void clearBoxQuietly(int requestId) {
        try {
            deliveryBoxRepository.clear(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "納品ボックスの初期化に失敗しました", e);
        }
    }

    // ---------------------------------------------------------------
    // 保管庫
    // ---------------------------------------------------------------

    /** 手渡しできなかったアイテムを保管庫へ預ける。 */
    public void depositToVault(Player player, ItemStack item, String reason) {
        try {
            vaultRepository.insert(player.getUniqueId(), player.getName(),
                    ItemSerialization.serialize(item), item.getAmount(), reason, System.currentTimeMillis());
            messages.send(player, "vault.deposited", Map.of("item", displayNameOf(item)));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "保管庫への預け入れに失敗しました(" + reason + ")", e);
        }
    }

    public List<VaultItem> findVaultItems(UUID ownerUuid) {
        try {
            return vaultRepository.findByOwner(ownerUuid);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "保管庫の取得に失敗しました", e);
            return new ArrayList<>();
        }
    }

    public void receiveVaultItem(Player player, int vaultItemId) {
        List<VaultItem> items = findVaultItems(player.getUniqueId());
        VaultItem target = items.stream().filter(v -> v.getId() == vaultItemId).findFirst().orElse(null);
        if (target == null) {
            return;
        }

        Optional<ItemStack> deserialized = ItemSerialization.deserialize(target.getItemData(), logger,
                "vault_items#" + target.getId());
        if (deserialized.isEmpty()) {
            deleteVaultItemQuietly(target.getId());
            messages.send(player, "vault.broken");
            return;
        }

        ItemStack item = deserialized.get();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (leftover.isEmpty()) {
            deleteVaultItemQuietly(target.getId());
            messages.send(player, "vault.received", Map.of("item", displayNameOf(item)));
            return;
        }
        ItemStack remaining = leftover.values().iterator().next();
        try {
            vaultRepository.updateRemainder(target.getId(), ItemSerialization.serialize(remaining),
                    remaining.getAmount());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "保管庫アイテムの更新に失敗しました", e);
        }
        messages.send(player, "vault.inventory-full");
    }

    /** 保管庫が目安のスロット数を超えていれば取り出しを促す。取引自体は止めない。 */
    public void warnIfVaultCrowded(Player player) {
        int softLimit = config.getInt("box.vault-slots", BOX_SIZE);
        if (softLimit <= 0) {
            return;
        }
        int count;
        try {
            count = vaultRepository.countByOwner(player.getUniqueId());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "保管庫の件数取得に失敗しました", e);
            return;
        }
        if (count > softLimit) {
            messages.send(player, "vault.crowded", Map.of(
                    "count", String.valueOf(count), "limit", String.valueOf(softLimit)));
        }
    }

    private void deleteVaultItemQuietly(int id) {
        try {
            vaultRepository.deleteById(id);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "保管庫アイテムの削除に失敗しました", e);
        }
    }

    private String displayNameOf(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name() + " x" + item.getAmount();
    }

    // ---------------------------------------------------------------
    // 通知(オフライン中の分は溜めて次回ログイン時に届ける)
    // ---------------------------------------------------------------

    public void deliverQueuedNotifications(Player player) {
        List<Notification> pending;
        try {
            pending = notificationRepository.findByOwner(player.getUniqueId());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "通知の取得に失敗しました", e);
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        for (Notification notification : pending) {
            player.sendMessage(notification.getMessage());
        }
        try {
            notificationRepository.deleteByOwner(player.getUniqueId());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "通知の削除に失敗しました", e);
        }
        warnIfVaultCrowded(player);
    }

    private void notify(UUID uuid, String messageKey, Map<String, String> placeholders) {
        if (uuid == null) {
            return;
        }
        String text = messages.get("prefix") + messages.get(messageKey, placeholders);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            online.sendMessage(text);
            return;
        }
        try {
            notificationRepository.insert(uuid, text, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "通知の保存に失敗しました", e);
        }
    }

    // ---------------------------------------------------------------

    private void markReminderSentQuietly(int requestId) {
        try {
            requestRepository.markReminderSent(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "リマインド送信済みフラグの更新に失敗しました", e);
        }
    }

    public Request findRequest(int requestId) {
        return findOrNull(requestId);
    }

    private Request findOrNull(int requestId) {
        try {
            return requestRepository.findById(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼の取得に失敗しました", e);
            return null;
        }
    }
}
