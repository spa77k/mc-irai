package net.mcirai.contractboard.command;

import net.mcirai.contractboard.gui.GuiManager;
import net.mcirai.contractboard.session.SessionManager;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IraiCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("create", "list", "my", "cancel");

    private final GuiManager guiManager;
    private final SessionManager sessionManager;
    private final MessageUtil messages;
    private final FileConfiguration config;

    public IraiCommand(GuiManager guiManager, SessionManager sessionManager, MessageUtil messages,
                        FileConfiguration config) {
        this.guiManager = guiManager;
        this.sessionManager = sessionManager;
        this.messages = messages;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }

        if (args.length == 0) {
            guiManager.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                sessionManager.start(player.getUniqueId());
                messages.send(player, "create.start");
                int maxTitle = config.getInt("request.max-title-length", 32);
                messages.send(player, "create.ask-title", Map.of("max", String.valueOf(maxTitle)));
            }
            case "list" -> guiManager.openRequestList(player, 0);
            case "my" -> guiManager.openMyRequests(player);
            case "cancel" -> {
                if (sessionManager.has(player.getUniqueId())) {
                    sessionManager.end(player.getUniqueId());
                    messages.send(player, "create.cancelled-input");
                } else {
                    messages.send(player, "create.no-active-session");
                }
            }
            default -> messages.send(player, "unknown-subcommand");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String subcommand : SUBCOMMANDS) {
            if (subcommand.startsWith(prefix)) {
                matches.add(subcommand);
            }
        }
        return matches;
    }
}
