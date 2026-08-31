package net.mcirai.contractboard.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class MainMenuHolder implements InventoryHolder {

    public static final int SLOT_LIST = 10;
    public static final int SLOT_CREATE = 12;
    public static final int SLOT_MY = 14;
    public static final int SLOT_VAULT = 16;

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
