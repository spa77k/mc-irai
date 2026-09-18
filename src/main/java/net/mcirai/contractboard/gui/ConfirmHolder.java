package net.mcirai.contractboard.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** 取り下げ・ギブアップ・強制差し戻しなど、取り消せない操作を実行する前の確認画面。 */
public class ConfirmHolder implements InventoryHolder {

    public static final int SLOT_CONFIRM = 11;
    public static final int SLOT_INFO = 13;
    public static final int SLOT_CANCEL = 15;

    private Inventory inventory;
    private final int requestId;
    private final int action;

    public ConfirmHolder(int requestId, int action) {
        this.requestId = requestId;
        this.action = action;
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

    /** GuiManager.ACTION_* のいずれか。 */
    public int getAction() {
        return action;
    }
}
