package net.mcirai.contractboard.command;

import net.mcirai.contractboard.gui.GuiManager;
import net.mcirai.contractboard.session.SessionManager;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;

public class IraiCommand implements CommandExecutor {

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
}
