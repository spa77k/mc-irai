package net.mcirai.contractboard.util;

/**
 * 通知の種類。チャット1行だけでは流れて見落とされるため、
 * 種類ごとに効果音とアクションバーの要約文言を対応づける。
 *
 * <p>効果音は {@code notify.sounds.<key>}、アクションバーの文言は
 * {@code messages.action-bar.<key>} から引く。音程は種類ごとの既定値を使い、
 * 良い知らせは高め、残念な知らせは低めにして耳だけで区別できるようにする。
 */
public enum NotifyTone {

    /** 自分の依頼が受注された(依頼者へ)。 */
    ACCEPTED("accepted", "entity.experience_orb.pickup", 1.2f),
    /** 納品完了の報告が届いた(依頼者へ)。 */
    DELIVERED("delivered", "block.note_block.pling", 1.4f),
    /** 完了承認されて報酬が入った(受注者へ)。 */
    APPROVED("approved", "entity.player.levelup", 1.0f),
    /** 放置していた納品報告が自動承認された(依頼者へ)。 */
    AUTO_APPROVED("auto-approved", "block.note_block.chime", 1.0f),
    /** 納品内容を差し戻された(受注者へ)。 */
    REVISION("revision", "block.note_block.didgeridoo", 1.0f),
    /** 受注の取り消し・運営による強制終了(当事者へ)。 */
    CANCELLED("cancelled", "block.note_block.bass", 0.8f),
    /** 依頼が期限切れになった(依頼者へ)。 */
    EXPIRED("expired", "block.note_block.bass", 1.0f),
    /** 納品物が保管庫へ戻された。 */
    RETURNED("returned", "entity.item.pickup", 1.0f),
    /** 放置に対する催促・ログイン時のまとめ通知。 */
    REMINDER("reminder", "block.note_block.hat", 1.0f),
    /** 保管庫の保管期限に関する警告。 */
    VAULT_WARNING("vault-warning", "block.note_block.bass", 1.2f),

    /** 自分の操作が成功した(本人へ、音のみ)。 */
    SUCCESS("success", "entity.experience_orb.pickup", 1.0f),
    /** 自分の操作が条件を満たさず弾かれた(本人へ、音のみ)。 */
    DENIED("denied", "block.note_block.bass", 0.6f);

    private final String key;
    private final String defaultSound;
    private final float pitch;

    NotifyTone(String key, String defaultSound, float pitch) {
        this.key = key;
        this.defaultSound = defaultSound;
        this.pitch = pitch;
    }

    public String getKey() {
        return key;
    }

    public String getDefaultSound() {
        return defaultSound;
    }

    public float getPitch() {
        return pitch;
    }
}
