package net.mcirai.contractboard.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mcirai.contractboard.session.CreateRequestSession;
import net.mcirai.contractboard.session.RequestInputProcessor;
import net.mcirai.contractboard.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * 通常のPaperでは会話(Conversation)がチャット入力を先に処理するため発火しないが、
 * 会話が効かないサーバー実装/フォークでも依頼作成が壊れないようにする保険のリスナー。
 */
public class ChatInputListener implements Listener {

    private final Plugin plugin;
    private final SessionManager sessionManager;
    private final RequestInputProcessor processor;

    public ChatInputListener(Plugin plugin, SessionManager sessionManager, RequestInputProcessor processor) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.processor = processor;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        CreateRequestSession session = sessionManager.get(player.getUniqueId());
        // 確認画面の段階では入力を受け付けないので、通常のチャットとして流す
        if (session == null || session.isAwaitingConfirm()) {
            return;
        }
        event.setCancelled(true);
        event.viewers().clear();
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> handleInput(player, input));
    }

    private void handleInput(Player player, String input) {
        processor.handle(player, input);
        CreateRequestSession session = sessionManager.get(player.getUniqueId());
        if (session != null && !session.isAwaitingConfirm()) {
            player.sendMessage(processor.promptText(player, session));
        }
    }
}
