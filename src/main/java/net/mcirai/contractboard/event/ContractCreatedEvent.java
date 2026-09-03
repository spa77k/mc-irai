package net.mcirai.contractboard.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 依頼が新規作成されたときに発火するイベント。外部連携(Discord通知等)が購読するための通知規約に沿う。 */
public class ContractCreatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int requestId;
    private final UUID requesterId;
    private final String requesterName;
    private final String title;
    private final double reward;
    private final String formattedReward;
    private final int expireHours;
    private final int minStars;
    private final boolean itemDelivery;

    public ContractCreatedEvent(int requestId, UUID requesterId, String requesterName, String title,
                                 double reward, String formattedReward, int expireHours, int minStars,
                                 boolean itemDelivery) {
        this.requestId = requestId;
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.title = title;
        this.reward = reward;
        this.formattedReward = formattedReward;
        this.expireHours = expireHours;
        this.minStars = minStars;
        this.itemDelivery = itemDelivery;
    }

    public int getRequestId() {
        return requestId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public String getTitle() {
        return title;
    }

    public double getReward() {
        return reward;
    }

    public int getExpireHours() {
        return expireHours;
    }

    public int getMinStars() {
        return minStars;
    }

    public boolean isItemDelivery() {
        return itemDelivery;
    }

    /** 通知種別の識別子。 */
    public String getNotifyKind() {
        return "contract.created";
    }

    /** 通知文面への差し込み用プレースホルダ。値は無害化前の生の文字列で、受け手側で整形する。 */
    public Map<String, String> getNotifyPlaceholders() {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", requesterName);
        placeholders.put("title", title);
        placeholders.put("reward", formattedReward);
        placeholders.put("expire_hours", String.valueOf(expireHours));
        placeholders.put("min_stars", String.valueOf(minStars));
        placeholders.put("item_delivery", itemDelivery ? "あり" : "なし");
        return Collections.unmodifiableMap(placeholders);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
