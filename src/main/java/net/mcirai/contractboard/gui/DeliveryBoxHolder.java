package net.mcirai.contractboard.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * 依頼の納品ボックス。GUIの中で唯一アイテムの出し入れを許す画面で、
 * 閲覧専用(依頼者側、または納品報告後)のときは editable が false になる。
 */
public class DeliveryBoxHolder implements InventoryHolder {

    private Inventory inventory;
    private final int requestId;
    private final boolean editable;

    public DeliveryBoxHolder(int requestId, boolean editable) {
        this.requestId = requestId;
        this.editable = editable;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int getRequestId() {
        return requestId;
    }

    public boolean isEditable() {
        return editable;
    }
}
