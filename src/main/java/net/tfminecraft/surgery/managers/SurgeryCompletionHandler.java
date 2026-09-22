package net.tfminecraft.surgery.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

// ==============================================
// Handles surgery completion (success and failure)
// ==============================================
public class SurgeryCompletionHandler {
    
    private final JavaPlugin plugin;
    private final SurgeryStateManager stateManager;
    private final SurgeryUIUpdater uiUpdater;
    
    public SurgeryCompletionHandler(JavaPlugin plugin, SurgeryStateManager stateManager, SurgeryUIUpdater uiUpdater) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.uiUpdater = uiUpdater;
    }
    
    // ==============================================
    // Checks if all success conditions are met
    // ==============================================
    public boolean isSurgerySuccessful(UUID playerId) {
        // 1. Check if diagnosis is cured
        if (!stateManager.isCured(playerId)) { return false; }
        
        // 2. Pulse must be "Strong" (LIME)
        if (!stateManager.getPulse(playerId).equals("Strong")) { return false; }
        
        // 3. Status must be "Unconscious" (LIME)
        if (!stateManager.getStatus(playerId).equals("Unconscious")) { return false; }
        
        // 4. Temperature must be at or below the configured success threshold (LIME)
        double successThreshold = plugin.getConfig().getDouble("temperature.success-threshold", 100.0);
        if (stateManager.getTemperature(playerId) > successThreshold) { return false; }
        
        // 5. Operation site must be "Clean" (LIME)
        if (!stateManager.getOperationSite(playerId).equals("Clean")) { return false; }
        
        // 6. Incisions must be 0 (LIME)
        if (stateManager.getIncisions(playerId) != 0) { return false; }
        
        // 7. All bones must be fixed
        if (stateManager.getBrokenBones(playerId) != 0 || stateManager.getShatteredBones(playerId) != 0) { return false; }
        
        // 8. No bleeding
        if (stateManager.isBleeding(playerId)) { return false; }
        
        return true;
    }
    
    // ==============================================
    // Handles successful surgery completion
    // ==============================================
    public void handleSuccess(Player player) {
        UUID playerId = player.getUniqueId();
        
        executeCompletionCommand(player, true);
    
        stateManager.cleanup(playerId);

        closeMenuNextTick(player);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.sendMessage(uiUpdater.getMessage("surgery-successful"));
        player.sendMessage(uiUpdater.getMessage("surgery-successful-subtitle"));
    }
    
    // ==============================================
    // Fails the surgery with a message
    // ==============================================
    public void failSurgery(Player player, String message) {
        UUID playerId = player.getUniqueId();
        
        // Check if player is actually in surgery (prevent duplicate messages)
        if (!stateManager.hasSession(playerId)) { return; }
        
        executeCompletionCommand(player, false);
        
        stateManager.cleanup(playerId);

        closeMenuNextTick(player);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f);
        player.sendMessage(uiUpdater.getMessage("surgery-failed"));
        player.sendMessage(message);
    }
    
    // ==============================================
    // Closes the menu on the next tick; calling closeInventory directly inside
    // an InventoryClickEvent handler is undefined behavior per the Bukkit docs
    // ==============================================
    private void closeMenuNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
    }

    // ==============================================
    // Handles player giving up on surgery (only if they didn't complete it successfully)
    // ==============================================
    public void handleAbandonment(Player player) {
        UUID playerId = player.getUniqueId();

        // Only fail if player still has active surgery data (not cleaned up from success)
        if (stateManager.hasDiagnosis(playerId)) {
            failSurgery(player, uiUpdater.getMessage("failure-gave-up"));
        } else {
            // No diagnosis yet (menu closed before ultrasound) - nothing to fail,
            // but state initialized on menu open must still be released
            stateManager.cleanup(playerId);
        }
    }
    
    // ==============================================
    // Handles the surgeon disconnecting mid-surgery
    // Runs the failure command (no free escape by logging out) but skips the
    // messages, sounds, and menu close, which are pointless for a quitting
    // player. If the close event already failed the surgery, the session is
    // gone and this only cleans up.
    // ==============================================
    public void handleQuit(Player player) {
        UUID playerId = player.getUniqueId();
        if (stateManager.hasDiagnosis(playerId)) {
            executeCompletionCommand(player, false);
        }
        stateManager.cleanup(playerId);
    }

    // ==============================================
    // Executes the configured command for surgery completion (success or failure)
    // ==============================================
    private void executeCompletionCommand(Player surgeon, boolean success) {
        UUID surgeonId = surgeon.getUniqueId();
        String patientName = stateManager.getPatientName(surgeonId);
        
        String configKey = success ? "commands.surgery-success" : "commands.surgery-failure";
        String command = plugin.getConfig().getString(configKey, "");
        
        if (command != null && !command.isEmpty()) {
            command = command.replace("%surgeon%", surgeon.getName());
            command = command.replace("%player%", patientName);

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }
}
