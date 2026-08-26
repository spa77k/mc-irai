package net.mcirai.contractboard;

import net.mcirai.contractboard.economy.EconomyService;
import net.mcirai.contractboard.model.Rating;
import net.mcirai.contractboard.model.Request;
import net.mcirai.contractboard.model.RequestStatus;
import net.mcirai.contractboard.storage.RatingRepository;
import net.mcirai.contractboard.storage.RequestRepository;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestService {

    private final RequestRepository requestRepository;
    private final RatingRepository ratingRepository;
    private final EconomyService economyService;
    private final MessageUtil messages;
    private final FileConfiguration config;
    private final Logger logger;

    public RequestService(RequestRepository requestRepository, RatingRepository ratingRepository,
                           EconomyService economyService, MessageUtil messages,
                           FileConfiguration config, Logger logger) {
        this.requestRepository = requestRepository;
        this.ratingRepository = ratingRepository;
        this.economyService = economyService;
        this.messages = messages;
        this.config = config;
        this.logger = logger;
    }

    public boolean createRequest(Player requester, String title, String description,
                                  double reward, int expireHours, int minStars) {
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
        try {
            requestRepository.insert(requester.getUniqueId(), requester.getName(),
                    title, description, reward, now, expiresAt, minStars);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼の作成に失敗しました", e);
            economyService.deposit(requester, total);
            requester.sendMessage(messages.get("prefix") + "§c依頼の作成に失敗しました。徴収額は返却されました。");
            return false;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("title", title);
        placeholders.put("amount", economyService.format(total));
        placeholders.put("reward", economyService.format(reward));
        placeholders.put("fee", economyService.format(fee));
        messages.send(requester, "create.completed", placeholders);
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
    }

    public void giveUpRequest(Player worker, int requestId) {
        Request request = findOrNull(requestId);
        if (request == null || request.getStatus() != RequestStatus.ACCEPTED
                || !worker.getUniqueId().equals(request.getWorkerId())) {
            return;
        }
        try {
            requestRepository.clearWorker(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "受注の取り消しに失敗しました", e);
            return;
        }
        messages.send(worker, "giveup.success", Map.of("title", request.getTitle()));
    }

    public void markDelivered(Player worker, int requestId) {
        Request request = findOrNull(requestId);
        if (request == null || request.getStatus() != RequestStatus.ACCEPTED
                || !worker.getUniqueId().equals(request.getWorkerId())) {
            messages.send(worker, "deliver.not-accepted");
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
        Player requesterPlayer = org.bukkit.Bukkit.getPlayer(request.getRequesterId());
        if (requesterPlayer != null) {
            messages.send(requesterPlayer, "deliver.notify-requester",
                    Map.of("title", request.getTitle(), "worker", worker.getName()));
        }
    }

    public void forceRevert(Player requester, int requestId) {
        Request request = findOrNull(requestId);
        if (request == null || request.getStatus() != RequestStatus.ACCEPTED
                || !requester.getUniqueId().equals(request.getRequesterId())) {
            return;
        }
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
        messages.send(requester, "force-revert.success", Map.of("title", request.getTitle()));
        Player workerPlayer = org.bukkit.Bukkit.getPlayer(request.getWorkerId());
        if (workerPlayer != null) {
            messages.send(workerPlayer, "force-revert.notify-worker", Map.of("title", request.getTitle()));
        }
    }

    public boolean completeRequest(Player requester, int requestId, int stars) {
        Request request = findOrNull(requestId);
        if (request == null
                || (request.getStatus() != RequestStatus.ACCEPTED && request.getStatus() != RequestStatus.DELIVERED)
                || !requester.getUniqueId().equals(request.getRequesterId())) {
            messages.send(requester, "complete.not-accepted");
            return false;
        }
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
        if (!economyService.deposit(org.bukkit.Bukkit.getOfflinePlayer(request.getWorkerId()), request.getReward())) {
            requester.sendMessage(messages.get("prefix") + "§c報酬の支払いに失敗しました。管理者に連絡してください。");
            return false;
        }
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
                    org.bukkit.Bukkit.getOfflinePlayer(request.getRequesterId()), request.getReward())) {
                logger.warning("依頼ID " + request.getId() + " の期限切れ返還に失敗しました。管理者による手動対応が必要です。");
                continue;
            }
            UUID requesterId = request.getRequesterId();
            Player online = org.bukkit.Bukkit.getPlayer(requesterId);
            if (online != null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("title", request.getTitle());
                placeholders.put("amount", economyService.format(request.getReward()));
                messages.send(online, "expire.notify-requester", placeholders);
            }
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
                    org.bukkit.Bukkit.getOfflinePlayer(request.getWorkerId()), request.getReward())) {
                logger.warning("依頼ID " + request.getId() + " の自動承認支払いに失敗しました。管理者による手動対応が必要です。");
                continue;
            }
            Map<String, String> placeholders = Map.of(
                    "title", request.getTitle(),
                    "amount", economyService.format(request.getReward()),
                    "worker", request.getWorkerName());
            Player requesterPlayer = org.bukkit.Bukkit.getPlayer(request.getRequesterId());
            if (requesterPlayer != null) {
                messages.send(requesterPlayer, "auto-approve.notify-requester", placeholders);
            }
            Player workerPlayer = org.bukkit.Bukkit.getPlayer(request.getWorkerId());
            if (workerPlayer != null) {
                messages.send(workerPlayer, "auto-approve.notify-worker", placeholders);
            }
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
            Player requesterPlayer = org.bukkit.Bukkit.getPlayer(request.getRequesterId());
            if (requesterPlayer != null) {
                messages.send(requesterPlayer, "auto-approve.reminder-requester", Map.of(
                        "title", request.getTitle(), "hours", String.valueOf(reminderHours)));
            }
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
            Player workerPlayer = org.bukkit.Bukkit.getPlayer(request.getWorkerId());
            if (workerPlayer != null) {
                messages.send(workerPlayer, "force-revert.reminder-worker", Map.of(
                        "title", request.getTitle(), "hours", String.valueOf(reminderHours)));
            }
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

    private void markReminderSentQuietly(int requestId) {
        try {
            requestRepository.markReminderSent(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "リマインド送信済みフラグの更新に失敗しました", e);
        }
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
