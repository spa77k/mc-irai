package net.mcirai.contractboard.command;

import net.mcirai.contractboard.gui.GuiManager;
import net.mcirai.contractboard.session.SessionManager;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class IraiCommand implements CommandExecutor {

    private final GuiManager guiManager;
    private final SessionManager sessionManager;
    private final MessageUtil messages;

    public IraiCommand(GuiManager guiManager, SessionManager sessionManager, MessageUtil messages) {
        this.guiManager = guiManager;
        this.sessionManager = sessionManager;
        this.messages = messages;
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
                messages.send(player, "create.ask-title");
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
