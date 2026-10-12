package net.tfminecraft.surgery.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent;
import net.tfminecraft.surgery.managers.SurgeryMenuManager;

public class PlayerListener implements Listener {

    private final SurgeryMenuManager menuManager;

    public PlayerListener(SurgeryMenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSessionEnd(CharacterSessionEndEvent event) {
        menuManager.handleSessionEnd(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Block drags entirely while the surgery menu is open; drag events are not
        // covered by the click handler and could place items into menu slots
        if (menuManager.isSurgeryMenu(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the top inventory of the open view is the surgery menu
        if (menuManager.isSurgeryMenu(event.getInventory())) {
            // Cancel the event to prevent item removal/movement
            event.setCancelled(true);

            // Only handle clicks on the top inventory (the menu), not the player's inventory
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getInventory())) {
                // Handle the item click if it's a player
                if (event.getWhoClicked() instanceof Player) {
                    Player player = (Player) event.getWhoClicked();
                    ItemStack clickedItem = event.getCurrentItem();
                    int slot = event.getSlot();

                    // Handle the click
                    menuManager.handleItemClick(player, event.getInventory(), clickedItem, slot);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Check if the closed inventory is the surgery menu
        if (menuManager.isSurgeryMenu(event.getInventory())) {
            if (event.getPlayer() instanceof Player) {
                Player player = (Player) event.getPlayer();
                // Handle abandonment (giving up on surgery)
                menuManager.handleSurgeryAbandonment(player, event.getInventory());
            }
        }
    }
}
