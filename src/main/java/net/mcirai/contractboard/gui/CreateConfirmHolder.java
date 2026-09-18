package net.mcirai.contractboard.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** 依頼作成の確認画面。入力内容そのものは CreateRequestSession 側に持つ。 */
public class CreateConfirmHolder implements InventoryHolder {

    public static final int SLOT_ITEM_DELIVERY = 11;
    public static final int SLOT_SUMMARY = 13;
    public static final int SLOT_MIN_STARS = 15;
    public static final int SLOT_CANCEL = 18;
    public static final int SLOT_RESTART = 20;
    public static final int SLOT_CREATE = 26;

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
