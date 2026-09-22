package net.tfminecraft.surgery.managers;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

// ==============================================
// Marker holder for the surgery menu inventory
// Lets listeners identify the menu by holder type instead of matching the
// title string, which any other inventory could share
// ==============================================
public class SurgeryMenuHolder implements InventoryHolder {

    private final Inventory inventory;

    public SurgeryMenuHolder() {
        this.inventory = Bukkit.createInventory(this, 54, "Surgery Menu");
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
