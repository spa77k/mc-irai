package net.mcirai.contractboard.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mcirai.contractboard.RequestService;
import net.mcirai.contractboard.session.CreateRequestSession;
import net.mcirai.contractboard.session.SessionManager;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public class ChatInputListener implements Listener {

    private final Plugin plugin;
    private final FileConfiguration config;
    private final SessionManager sessionManager;
    private final RequestService requestService;
    private final MessageUtil messages;

    public ChatInputListener(Plugin plugin, FileConfiguration config, SessionManager sessionManager,
                              RequestService requestService, MessageUtil messages) {
        this.plugin = plugin;
        this.config = config;
        this.sessionManager = sessionManager;
        this.requestService = requestService;
        this.messages = messages;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!sessionManager.has(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> handleInput(player, input));
    }

    private void handleInput(Player player, String input) {
        CreateRequestSession session = sessionManager.get(player.getUniqueId());
        if (session == null) {
            return;
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
                    return;
                }
                session.setTitle(input);
                session.setStep(CreateRequestSession.Step.DESCRIPTION);
                messages.send(player, "create.ask-description", Map.of("max", String.valueOf(maxDescription)));
            }
            case DESCRIPTION -> {
                if (input.isEmpty() || input.length() > maxDescription) {
                    messages.send(player, "create.description-too-long",
                            Map.of("max", String.valueOf(maxDescription)));
                    return;
                }
                session.setDescription(input);
                session.setStep(CreateRequestSession.Step.REWARD);
                String feePercent = formatPercent(config.getDouble("request.fee-rate", 0.0));
                messages.send(player, "create.ask-reward", Map.of(
                        "min", String.valueOf((int) minReward),
                        "max", String.valueOf((int) maxReward),
                        "fee-percent", feePercent));
            }
            case REWARD -> {
                double reward;
                try {
                    reward = Double.parseDouble(input);
                } catch (NumberFormatException e) {
                    messages.send(player, "create.invalid-reward", Map.of(
                            "min", String.valueOf((int) minReward),
                            "max", String.valueOf((int) maxReward)));
                    return;
                }
                if (reward < minReward || reward > maxReward) {
                    messages.send(player, "create.invalid-reward", Map.of(
                            "min", String.valueOf((int) minReward),
                            "max", String.valueOf((int) maxReward)));
                    return;
                }
                session.setReward(reward);
                session.setStep(CreateRequestSession.Step.EXPIRE);
                messages.send(player, "create.ask-expire", Map.of(
                        "min", String.valueOf(minExpire),
                        "max", String.valueOf(maxExpire)));
            }
            case EXPIRE -> {
                int hours;
                try {
                    hours = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    messages.send(player, "create.invalid-expire", Map.of(
                            "min", String.valueOf(minExpire),
                            "max", String.valueOf(maxExpire)));
                    return;
                }
                if (hours < minExpire || hours > maxExpire) {
                    messages.send(player, "create.invalid-expire", Map.of(
                            "min", String.valueOf(minExpire),
                            "max", String.valueOf(maxExpire)));
                    return;
                }
                session.setExpireHours(hours);
                session.setStep(CreateRequestSession.Step.MIN_STARS);
                messages.send(player, "create.ask-min-stars", Map.of("max", String.valueOf(maxMinStars)));
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
                        return;
                    }
                }
                if (minStars < 0 || minStars > maxMinStars) {
                    messages.send(player, "create.invalid-min-stars", Map.of("max", String.valueOf(maxMinStars)));
                    return;
                }
                session.setMinStars(minStars);
                sessionManager.end(player.getUniqueId());
                requestService.createRequest(player, session.getTitle(), session.getDescription(),
                        session.getReward(), session.getExpireHours(), minStars);
            }
        }
    }

    private String formatPercent(double rate) {
        double percent = rate * 100;
        if (percent == Math.floor(percent)) {
            return String.valueOf((int) percent);
        }
        return String.valueOf(percent);
    }
}
