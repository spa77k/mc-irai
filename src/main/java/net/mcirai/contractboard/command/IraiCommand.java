package net.mcirai.contractboard.command;

import net.mcirai.contractboard.RequestService;
import net.mcirai.contractboard.gui.GuiManager;
import net.mcirai.contractboard.session.CreateRequestConversation;
import net.mcirai.contractboard.session.SessionManager;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IraiCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "irai.admin";
    private static final List<String> SUBCOMMANDS = List.of("create", "list", "my", "vault", "cancel");

    private final GuiManager guiManager;
    private final RequestService requestService;
    private final SessionManager sessionManager;
    private final MessageUtil messages;
    private final CreateRequestConversation createRequestConversation;

    public IraiCommand(GuiManager guiManager, RequestService requestService, SessionManager sessionManager,
                        MessageUtil messages, CreateRequestConversation createRequestConversation) {
        this.guiManager = guiManager;
        this.requestService = requestService;
        this.sessionManager = sessionManager;
        this.messages = messages;
        this.createRequestConversation = createRequestConversation;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 運営コマンドだけはコンソールからも実行できる
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            handleAdmin(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }

        if (args.length == 0) {
            guiManager.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> createRequestConversation.start(player);
            case "list" -> guiManager.openRequestList(player, 0);
            case "my" -> guiManager.openMyRequests(player);
            case "vault" -> guiManager.openVault(player, 0);
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

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("cancel")) {
            messages.send(sender, "admin.usage");
            return;
        }
        int requestId;
        try {
            requestId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            messages.send(sender, "admin.invalid-id", Map.of("id", args[2]));
            return;
        }
        requestService.adminCancel(sender, requestId);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> matches = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String subcommand : SUBCOMMANDS) {
                if (subcommand.startsWith(prefix)) {
                    matches.add(subcommand);
                }
            }
            if (sender.hasPermission(ADMIN_PERMISSION) && "admin".startsWith(prefix)) {
                matches.add("admin");
            }
            return matches;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission(ADMIN_PERMISSION)) {
            if ("cancel".startsWith(args[1].toLowerCase())) {
                matches.add("cancel");
            }
            return matches;
        }
        return List.of();
    }
}
