package net.mcirai.contractboard.util;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * チャットに加えて、効果音とアクションバーで通知を目立たせる。
 * チャット欄は他の発言ですぐ流れるため、受注・納品報告・承認などの取引の節目を
 * 見落とさせないことが目的。チャットの送信自体は呼び出し側が行う。
 */
public class Notifier {

    private final FileConfiguration config;
    private final MessageUtil messages;

    public Notifier(FileConfiguration config, MessageUtil messages) {
        this.config = config;
        this.messages = messages;
    }

    /** 効果音を鳴らし、アクションバーに要約を出す。相手から届く通知用。 */
    public void alert(Player player, NotifyTone tone, Map<String, String> placeholders) {
        play(player, tone);
        actionBar(player, tone.getKey(), placeholders);
    }

    /** {@code messages.action-bar.<key>} の文言をアクションバーに出す。未設定なら何もしない。 */
    public void actionBar(Player player, String key, Map<String, String> placeholders) {
        if (!config.getBoolean("notify.action-bar", true)) {
            return;
        }
        String text = messages.getOrNull("action-bar." + key, placeholders);
        if (text != null && !text.isEmpty()) {
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
        }
    }

    /** 効果音だけを鳴らす。自分の操作の結果など、画面を見ている本人向け。 */
    public void play(Player player, NotifyTone tone) {
        if (!config.getBoolean("notify.sound", true)) {
            return;
        }
        String sound = config.getString("notify.sounds." + tone.getKey(), tone.getDefaultSound());
        if (sound == null || sound.isBlank() || sound.equalsIgnoreCase("none")) {
            return;
        }
        float volume = (float) config.getDouble("notify.volume", 0.8);
        // 音名の文字列指定はバージョン間で互換性が高い(Sound列挙はPaper 1.21系でインターフェース化された)
        player.playSound(player.getLocation(), sound.trim().toLowerCase(Locale.ROOT), SoundCategory.MASTER,
                volume, tone.getPitch());
    }
}
