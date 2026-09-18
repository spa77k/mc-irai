package net.mcirai.contractboard.session;

import net.mcirai.contractboard.economy.EconomyService;
import net.mcirai.contractboard.gui.GuiManager;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public class RequestInputProcessor {

    private final Plugin plugin;
    private final FileConfiguration config;
    private final SessionManager sessionManager;
    private final EconomyService economyService;
    private final GuiManager guiManager;
    private final MessageUtil messages;

    public RequestInputProcessor(Plugin plugin, FileConfiguration config, SessionManager sessionManager,
                                  EconomyService economyService, GuiManager guiManager, MessageUtil messages) {
        this.plugin = plugin;
        this.config = config;
        this.sessionManager = sessionManager;
        this.economyService = economyService;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    public boolean handle(Player player, String input) {
        CreateRequestSession session = sessionManager.get(player.getUniqueId());
        if (session == null) {
            return false;
        }

        int maxTitle = config.getInt("request.max-title-length", 32);
        int maxDescription = config.getInt("request.max-description-length", 200);
        double minReward = config.getDouble("request.min-reward", 1);
        double maxReward = config.getDouble("request.max-reward", 1_000_000);
        int minExpire = config.getInt("request.min-expire-hours", 1);
        int maxExpire = config.getInt("request.max-expire-hours", 168);

        switch (session.getStep()) {
            case TITLE -> {
                if (input.isEmpty() || input.length() > maxTitle) {
                    messages.send(player, "create.title-too-long", Map.of("max", String.valueOf(maxTitle)));
                    return false;
                }
                session.setTitle(input);
                session.setStep(CreateRequestSession.Step.DESCRIPTION);
                return true;
            }
            case DESCRIPTION -> {
                if (input.isEmpty() || input.length() > maxDescription) {
                    messages.send(player, "create.description-too-long",
                            Map.of("max", String.valueOf(maxDescription)));
                    return false;
                }
                session.setDescription(input);
                session.setStep(CreateRequestSession.Step.REWARD);
                return true;
            }
            case REWARD -> {
                double reward;
                try {
                    reward = Double.parseDouble(input);
                } catch (NumberFormatException e) {
                    messages.send(player, "create.invalid-reward", Map.of(
                            "min", String.valueOf((int) minReward),
                            "max", String.valueOf((int) maxReward)));
                    return false;
                }
                if (reward < minReward || reward > maxReward) {
                    messages.send(player, "create.invalid-reward", Map.of(
                            "min", String.valueOf((int) minReward),
                            "max", String.valueOf((int) maxReward)));
                    return false;
                }
                // 残りの項目を入力し終えてから所持金不足で弾かれないよう、この時点で確認する
                double fee = reward * config.getDouble("request.fee-rate", 0.0);
                if (economyService.isReady() && !economyService.has(player, reward + fee)) {
                    messages.send(player, "create.insufficient-funds-input", Map.of(
                            "amount", economyService.format(reward + fee),
                            "reward", economyService.format(reward),
                            "fee", economyService.format(fee),
                            "balance", economyService.format(economyService.getBalance(player))));
                    return false;
                }
                session.setReward(reward);
                session.setStep(CreateRequestSession.Step.EXPIRE);
                return true;
            }
            case EXPIRE -> {
                int hours;
                try {
                    hours = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    messages.send(player, "create.invalid-expire", Map.of(
                            "min", String.valueOf(minExpire),
                            "max", String.valueOf(maxExpire)));
                    return false;
                }
                if (hours < minExpire || hours > maxExpire) {
                    messages.send(player, "create.invalid-expire", Map.of(
                            "min", String.valueOf(minExpire),
                            "max", String.valueOf(maxExpire)));
                    return false;
                }
                session.setExpireHours(hours);
                session.setStep(CreateRequestSession.Step.CONFIRM);
                // 最低星数・アイテム納品の有無は確認画面で切り替える。会話の終了処理と重ならないよう次tickで開く
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && sessionManager.get(player.getUniqueId()) == session) {
                        guiManager.openCreateConfirm(player, session);
                    }
                });
                return true;
            }
            case CONFIRM -> {
                return false;
            }
        }
        return false;
    }

    public String promptText(Player player, CreateRequestSession session) {
        if (session == null) {
            return "";
        }

        int maxTitle = config.getInt("request.max-title-length", 32);
        int maxDescription = config.getInt("request.max-description-length", 200);
        double minReward = config.getDouble("request.min-reward", 1);
        double maxReward = config.getDouble("request.max-reward", 1_000_000);
        int minExpire = config.getInt("request.min-expire-hours", 1);
        int maxExpire = config.getInt("request.max-expire-hours", 168);

        return switch (session.getStep()) {
            case TITLE -> messages.get("prefix")
                    + messages.get("create.ask-title", Map.of("max", String.valueOf(maxTitle)));
            case DESCRIPTION -> messages.get("prefix")
                    + messages.get("create.ask-description", Map.of("max", String.valueOf(maxDescription)));
            case REWARD -> {
                String feePercent = formatPercent(config.getDouble("request.fee-rate", 0.0));
                String balance = economyService.isReady()
                        ? economyService.format(economyService.getBalance(player)) : "?";
                yield messages.get("prefix") + messages.get("create.ask-reward", Map.of(
                        "min", String.valueOf((int) minReward),
                        "max", String.valueOf((int) maxReward),
                        "fee-percent", feePercent,
                        "balance", balance));
            }
            case EXPIRE -> messages.get("prefix") + messages.get("create.ask-expire", Map.of(
                    "min", String.valueOf(minExpire),
                    "max", String.valueOf(maxExpire)));
            case CONFIRM -> "";
        };
    }

    public static String formatPercent(double rate) {
        double percent = rate * 100;
        if (percent == Math.floor(percent)) {
            return String.valueOf((int) percent);
        }
        return String.valueOf(percent);
    }
}
