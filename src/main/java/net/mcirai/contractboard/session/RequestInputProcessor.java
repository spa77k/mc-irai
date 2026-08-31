package net.mcirai.contractboard.session;

import net.mcirai.contractboard.RequestService;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;

public class RequestInputProcessor {

    private final FileConfiguration config;
    private final SessionManager sessionManager;
    private final RequestService requestService;
    private final MessageUtil messages;

    public RequestInputProcessor(FileConfiguration config, SessionManager sessionManager,
                                  RequestService requestService, MessageUtil messages) {
        this.config = config;
        this.sessionManager = sessionManager;
        this.requestService = requestService;
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
        int maxMinStars = config.getInt("request.min-stars-max", 5);

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
                session.setStep(CreateRequestSession.Step.MIN_STARS);
                return true;
            }
            case MIN_STARS -> {
                int minStars;
                if (input.isEmpty() || input.equals("なし") || input.equals("0")) {
                    minStars = 0;
                } else {
                    try {
                        minStars = Integer.parseInt(input);
                    } catch (NumberFormatException e) {
                        messages.send(player, "create.invalid-min-stars", Map.of("max", String.valueOf(maxMinStars)));
                        return false;
                    }
                }
                if (minStars < 0 || minStars > maxMinStars) {
                    messages.send(player, "create.invalid-min-stars", Map.of("max", String.valueOf(maxMinStars)));
                    return false;
                }
                session.setMinStars(minStars);
                session.setStep(CreateRequestSession.Step.ITEM_DELIVERY);
                return true;
            }
            case ITEM_DELIVERY -> {
                Boolean itemDelivery = parseYesNo(input);
                if (itemDelivery == null) {
                    messages.send(player, "create.invalid-item-delivery");
                    return false;
                }
                session.setItemDelivery(itemDelivery);
                sessionManager.end(player.getUniqueId());
                requestService.createRequest(player, session.getTitle(), session.getDescription(),
                        session.getReward(), session.getExpireHours(), session.getMinStars(), itemDelivery);
                return true;
            }
        }
        return false;
    }

    public String promptText(CreateRequestSession session) {
        if (session == null) {
            return "";
        }

        int maxTitle = config.getInt("request.max-title-length", 32);
        int maxDescription = config.getInt("request.max-description-length", 200);
        double minReward = config.getDouble("request.min-reward", 1);
        double maxReward = config.getDouble("request.max-reward", 1_000_000);
        int minExpire = config.getInt("request.min-expire-hours", 1);
        int maxExpire = config.getInt("request.max-expire-hours", 168);
        int maxMinStars = config.getInt("request.min-stars-max", 5);

        return switch (session.getStep()) {
            case TITLE -> messages.get("prefix")
                    + messages.get("create.ask-title", Map.of("max", String.valueOf(maxTitle)));
            case DESCRIPTION -> messages.get("prefix")
                    + messages.get("create.ask-description", Map.of("max", String.valueOf(maxDescription)));
            case REWARD -> {
                String feePercent = formatPercent(config.getDouble("request.fee-rate", 0.0));
                yield messages.get("prefix") + messages.get("create.ask-reward", Map.of(
                        "min", String.valueOf((int) minReward),
                        "max", String.valueOf((int) maxReward),
                        "fee-percent", feePercent));
            }
            case EXPIRE -> messages.get("prefix") + messages.get("create.ask-expire", Map.of(
                    "min", String.valueOf(minExpire),
                    "max", String.valueOf(maxExpire)));
            case MIN_STARS -> messages.get("prefix")
                    + messages.get("create.ask-min-stars", Map.of("max", String.valueOf(maxMinStars)));
            case ITEM_DELIVERY -> messages.get("prefix") + messages.get("create.ask-item-delivery");
        };
    }

    /** 「はい/いいえ」入力の解釈。何も入力せずEnterした場合はアイテム納品なしとして扱う。 */
    private Boolean parseYesNo(String input) {
        String normalized = input.trim().toLowerCase();
        if (normalized.isEmpty() || normalized.equals("いいえ") || normalized.equals("no")
                || normalized.equals("n") || normalized.equals("なし") || normalized.equals("0")) {
            return Boolean.FALSE;
        }
        if (normalized.equals("はい") || normalized.equals("yes") || normalized.equals("y")
                || normalized.equals("あり") || normalized.equals("1")) {
            return Boolean.TRUE;
        }
        return null;
    }

    private String formatPercent(double rate) {
        double percent = rate * 100;
        if (percent == Math.floor(percent)) {
            return String.valueOf((int) percent);
        }
        return String.valueOf(percent);
    }
}
