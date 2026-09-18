package net.mcirai.contractboard.session;

import net.mcirai.contractboard.gui.GuiManager;
import net.mcirai.contractboard.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class CreateRequestConversation {

    private final SessionManager sessionManager;
    private final RequestInputProcessor processor;
    private final GuiManager guiManager;
    private final MessageUtil messages;
    private final ConversationFactory factory;

    public CreateRequestConversation(Plugin plugin, FileConfiguration config, SessionManager sessionManager,
                                      RequestInputProcessor processor, GuiManager guiManager,
                                      MessageUtil messages) {
        this.sessionManager = sessionManager;
        this.processor = processor;
        this.guiManager = guiManager;
        this.messages = messages;
        this.factory = new ConversationFactory(plugin)
                .withModality(false)
                .withLocalEcho(false)
                .withTimeout(config.getInt("request.input-timeout-seconds", 120))
                .withFirstPrompt(new AskPrompt())
                .addConversationAbandonedListener(this::onAbandoned);
    }

    public void start(Player player) {
        // 確認画面を閉じただけの入力は捨てずに、確認画面へ戻す
        CreateRequestSession pending = sessionManager.get(player.getUniqueId());
        if (pending != null && pending.isAwaitingConfirm()) {
            guiManager.openCreateConfirm(player, pending);
            return;
        }
        // 進行中の入力が残っていると新しい会話が待ち行列に入り、キャンセル後も発言を飲み込んでしまうため先に終了させる
        sessionManager.end(player.getUniqueId());
        sessionManager.start(player.getUniqueId());
        messages.send(player, "create.start");
        Conversation conversation = factory.buildConversation(player);
        sessionManager.attach(player.getUniqueId(), conversation);
        player.beginConversation(conversation);
    }

    private void onAbandoned(ConversationAbandonedEvent event) {
        if (event.gracefulExit()) {
            return;
        }
        if (!(event.getContext().getForWhom() instanceof Player player)) {
            return;
        }
        if (sessionManager.has(player.getUniqueId())) {
            sessionManager.end(player.getUniqueId());
            messages.send(player, "create.input-timeout");
        }
    }

    private class AskPrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {
            if (!(context.getForWhom() instanceof Player player)) {
                return "";
            }
            CreateRequestSession session = sessionManager.get(player.getUniqueId());
            return processor.promptText(player, session);
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if (!(context.getForWhom() instanceof Player player)) {
                return Prompt.END_OF_CONVERSATION;
            }
            processor.handle(player, input == null ? "" : input.trim());
            CreateRequestSession session = sessionManager.get(player.getUniqueId());
            if (session == null) {
                return Prompt.END_OF_CONVERSATION;
            }
            if (session.isAwaitingConfirm()) {
                // チャット入力はここで終わり。セッションは確認画面のために残す
                sessionManager.detachConversation(player.getUniqueId());
                return Prompt.END_OF_CONVERSATION;
            }
            return this;
        }
    }
}
