package net.mcirai.contractboard.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class RatingHolder implements InventoryHolder {

    public static final int[] STAR_SLOTS = {11, 12, 13, 14, 15};

    private Inventory inventory;
    private final int requestId;

    public RatingHolder(int requestId) {
        this.requestId = requestId;
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
}
