package net.tfminecraft.surgery.managers;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identifies one operation, so an old close or click cannot reach a replacement menu. */
public class SurgeryMenuHolder implements InventoryHolder {
    private final Inventory inventory;
    private final UUID surgeonId;
    private final long operationId;

    @SuppressWarnings("deprecation")
    public SurgeryMenuHolder(UUID surgeonId, long operationId) {
        this.surgeonId = surgeonId;
        this.operationId = operationId;
        this.inventory = Bukkit.createInventory(this, 54, "Surgery Menu");
    }

    public boolean matches(Player player, long currentOperation) {
        return operationId != 0 && operationId == currentOperation && surgeonId.equals(player.getUniqueId());
    }

    @Override public Inventory getInventory() { return inventory; }
}
