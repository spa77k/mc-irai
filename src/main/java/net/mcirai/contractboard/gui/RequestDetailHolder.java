package net.mcirai.contractboard.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class RequestDetailHolder implements InventoryHolder {

    public static final int SLOT_ACCEPT = 22;
    public static final int SLOT_BACK = 26;

    private Inventory inventory;
    private final int requestId;
    private final boolean fromList;

    public RequestDetailHolder(int requestId, boolean fromList) {
        this.requestId = requestId;
        this.fromList = fromList;
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

    public boolean isFromList() {
        return fromList;
    }
}
