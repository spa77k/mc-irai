package net.mcirai.contractboard.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class MyRequestsHolder implements InventoryHolder {

    public static final int SLOT_BACK = 49;

    private Inventory inventory;
    // スロット -> [requestId, action(0=何もしない,1=完了承認,2=取り下げ,3=ギブアップ,4=納品報告,5=強制差し戻し)]
    private final Map<Integer, int[]> slotActions = new HashMap<>();

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Map<Integer, int[]> getSlotActions() {
        return slotActions;
    }
}
