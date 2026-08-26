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
                                  double reward, int expireHours) {
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
                    title, description, reward, now, expiresAt);
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
            List<Request> alreadyAccepted = requestRepository.findByWorker(worker.getUniqueId());
            boolean hasActive = alreadyAccepted.stream()
                    .anyMatch(r -> r.getStatus() == RequestStatus.ACCEPTED);
            if (hasActive) {
                messages.send(worker, "accept.already-accepted-by-you");
                return;
            }
            requestRepository.assignWorker(requestId, worker.getUniqueId(), worker.getName());
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

    public boolean completeRequest(Player requester, int requestId) {
        Request request = findOrNull(requestId);
        if (request == null || request.getStatus() != RequestStatus.ACCEPTED
                || !requester.getUniqueId().equals(request.getRequesterId())) {
            messages.send(requester, "complete.not-accepted");
            return false;
        }
        if (!economyService.deposit(org.bukkit.Bukkit.getOfflinePlayer(request.getWorkerId()), request.getReward())) {
            requester.sendMessage(messages.get("prefix") + "§c報酬の支払いに失敗しました。");
            return false;
        }
        try {
            requestRepository.updateStatus(requestId, RequestStatus.COMPLETED);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼の完了処理に失敗しました", e);
            return false;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("title", request.getTitle());
        placeholders.put("amount", economyService.format(request.getReward()));
        placeholders.put("worker", request.getWorkerName());
        messages.send(requester, "complete.approved", placeholders);
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
        economyService.deposit(requester, request.getReward());
        try {
            requestRepository.updateStatus(requestId, RequestStatus.WITHDRAWN);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼の取り下げに失敗しました", e);
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("title", request.getTitle());
        placeholders.put("amount", economyService.format(request.getReward()));
        messages.send(requester, "withdraw.success", placeholders);
    }

    public void rate(Player requester, int requestId, int stars, String comment) {
        Request request = findOrNull(requestId);
        if (request == null || !requester.getUniqueId().equals(request.getRequesterId())
                || request.getStatus() != RequestStatus.COMPLETED) {
            return;
        }
        if (request.isRated()) {
            messages.send(requester, "rate.already-rated");
            return;
        }
        try {
            ratingRepository.insert(new Rating(requestId, requester.getUniqueId(), request.getWorkerId(),
                    stars, comment, System.currentTimeMillis()));
            requestRepository.markRated(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "評価の登録に失敗しました", e);
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("worker", request.getWorkerName());
        placeholders.put("stars", String.valueOf(stars));
        messages.send(requester, "rate.success", placeholders);
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
            economyService.deposit(org.bukkit.Bukkit.getOfflinePlayer(request.getRequesterId()), request.getReward());
            try {
                requestRepository.updateStatus(request.getId(), RequestStatus.EXPIRED);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "依頼の失効処理に失敗しました", e);
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

    private Request findOrNull(int requestId) {
        try {
            return requestRepository.findById(requestId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "依頼の取得に失敗しました", e);
            return null;
        }
    }
}
