package net.mcirai.contractboard.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class MainMenuHolder implements InventoryHolder {

    public static final int SLOT_LIST = 11;
    public static final int SLOT_CREATE = 13;
    public static final int SLOT_MY = 15;

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
