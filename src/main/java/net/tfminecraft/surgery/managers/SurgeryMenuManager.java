package net.tfminecraft.surgery.managers;

import net.tfminecraft.rpcharacters.api.HealingInjuries.HealingInjury;
import net.tfminecraft.surgery.procedures.Ailments;
import net.tfminecraft.surgery.procedures.ProcedureRegistry;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.TLibs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

// ==============================================
// Main file for the surgery menu system
// Delegates manager files for different responsibilities
// ==============================================
public class SurgeryMenuManager {
    
    private final JavaPlugin plugin;
    private final SurgeryItemsConfig itemsConfig;
    private ItemAPI api;
    
    // Specialized managers
    private final ProcedureRegistry procedures = new ProcedureRegistry();
    private final SurgeryRequestManager requestManager = new SurgeryRequestManager();
    private SurgeryStateManager stateManager;
    private SurgeryUIUpdater uiUpdater;
    private SurgeryMenuBuilder menuBuilder;
    private SurgeryCompletionHandler completionHandler;
    private SurgeryItemHandler itemHandler;
    private SurgeryMechanicsManager mechanicsManager;
    
    public SurgeryMenuManager(JavaPlugin plugin, SurgeryItemsConfig itemsConfig) {
        this.plugin = plugin;
        this.itemsConfig = itemsConfig;
    }
    
    // ==============================================
    // Initializes all managers and loads TLibs API
    // ==============================================
    public void initialize() {
        plugin.getLogger().info("[Surgery] Loading TLibs API...");
        api = TLibs.getItemAPI();
        if (api == null) {
            plugin.getLogger().severe("[Surgery] TLibs ItemAPI is unavailable - disabling plugin.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        // Initialize all managers in dependency order
        procedures.load(plugin.getConfig(), plugin.getLogger()::warning);
        stateManager = new SurgeryStateManager();
        uiUpdater = new SurgeryUIUpdater(plugin, stateManager);
        completionHandler = new SurgeryCompletionHandler(plugin, stateManager, uiUpdater);
        mechanicsManager = new SurgeryMechanicsManager(plugin, api, stateManager, uiUpdater, completionHandler, itemsConfig);
        menuBuilder = new SurgeryMenuBuilder(plugin, api, stateManager, uiUpdater, itemsConfig);
        itemHandler = new SurgeryItemHandler(plugin, api, stateManager, uiUpdater, mechanicsManager, completionHandler, itemsConfig);
        
        // Initialize any managers that need config
        itemHandler.initialize();
        
        plugin.getLogger().info("[Surgery] Surgery menu manager initialized!");
    }
    
    // ==============================================
    // Opens the surgery menu for the surgeon, operating on the patient's ailment
    // ==============================================
    public void openSurgeryMenu(Player surgeon, Player patient, HealingInjury ailment) {
        stateManager.setPatientName(surgeon.getUniqueId(), patient.getName());
        stateManager.setPatientUuid(surgeon.getUniqueId(), patient.getUniqueId());
        stateManager.setSurgeonCharacterId(surgeon.getUniqueId(), Ailments.characterId(surgeon));
        stateManager.setPatientCharacterId(surgeon.getUniqueId(), Ailments.characterId(patient));
        stateManager.setAilment(surgeon.getUniqueId(), ailment.traitId(), ailment.displayName(),
            procedures.get(ailment.traitId()));
        menuBuilder.buildAndOpenMenu(surgeon);
    }
    
    // ==============================================
    // Checks if an inventory is the surgery menu (by holder, not title)
    // ==============================================
    public boolean isSurgeryMenu(org.bukkit.inventory.Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof SurgeryMenuHolder;
    }
    
    // ==============================================
    // Handles item clicks within the surgery menu
    // Delegates to the item handler
    // ==============================================
    public void handleItemClick(Player player, ItemStack clickedItem, int slot) {
        itemHandler.handleItemClick(player, clickedItem, slot);
    }
    
    // ==============================================
    // Handles menu abandonment (player closed menu early)
    // Called from PlayerListener
    // ==============================================
    public void handleSurgeryAbandonment(Player player) {
        completionHandler.handleAbandonment(player);
    }
    
    // ==============================================
    // Handles the surgeon quitting the server
    // Called from PlayerListener; fails an in-progress surgery and cleans up
    // ==============================================
    public void handleSurgeonQuit(Player player) {
        completionHandler.handleQuit(player);
    }

    // ==============================================
    // Handles the patient quitting the server
    // Fails the surgeon's surgery immediately instead of waiting for
    // their next move to notice the patient is gone
    // ==============================================
    public void handlePatientQuit(Player patient) {
        UUID surgeonId = stateManager.findSurgeonForPatient(patient.getUniqueId());
        if (surgeonId == null) {
            return;
        }
        failForMissingPatient(surgeonId);
    }

    // ==============================================
    // Handles a player's active character changing
    // A surgeon who changes character gives up; a patient who changes
    // character has left the table, the same as quitting
    // ==============================================
    public void handleCharacterActivated(UUID ownerId, String characterId) {
        if (stateManager.hasSession(ownerId)) {
            endForSurgeonChange(ownerId);
        }
        UUID surgeonId = stateManager.findSurgeonForPatient(ownerId);
        if (surgeonId != null && !Objects.equals(characterId, stateManager.getPatientCharacterId(surgeonId))) {
            endForPatientChange(surgeonId);
        }
    }

    // ==============================================
    // Handles a character being permakilled
    // Ends any operation that character is part of, including when it was
    // the account's last character and no other character takes over
    // ==============================================
    public void handleCharacterPermakilled(UUID ownerId, String characterId) {
        if (stateManager.hasSession(ownerId) && Objects.equals(characterId, stateManager.getSurgeonCharacterId(ownerId))) {
            endForSurgeonChange(ownerId);
        }
        UUID surgeonId = stateManager.findSurgeonForPatient(ownerId);
        if (surgeonId != null && Objects.equals(characterId, stateManager.getPatientCharacterId(surgeonId))) {
            endForPatientChange(surgeonId);
        }
    }

    // The surgeon gives up; an unchanged patient still takes the failure penalty
    private void endForSurgeonChange(UUID surgeonId) {
        stateManager.markSurgeonChanged(surgeonId);
        Player surgeon = plugin.getServer().getPlayer(surgeonId);
        if (surgeon == null) {
            stateManager.cleanup(surgeonId);
            return;
        }
        completionHandler.handleAbandonment(surgeon);
        surgeon.closeInventory();
    }

    // The patient's character is gone, so nothing lands on the account
    private void endForPatientChange(UUID surgeonId) {
        stateManager.markPatientChanged(surgeonId);
        failForMissingPatient(surgeonId);
    }

    private void failForMissingPatient(UUID surgeonId) {
        Player surgeon = plugin.getServer().getPlayer(surgeonId);
        if (surgeon != null && surgeon.isOnline()) {
            completionHandler.failSurgery(surgeon, uiUpdater.getMessage("failure-patient-left",
                "&cThe patient is no longer on the operating table!"));
        } else {
            stateManager.cleanup(surgeonId);
        }
    }
    
    // ==============================================
    // Getters for accessing individual managers
    // ==============================================
    public SurgeryStateManager getStateManager() { return stateManager; }
    public SurgeryRequestManager getRequestManager() { return requestManager; }
    public SurgeryUIUpdater getUiUpdater() { return uiUpdater; }
    public SurgeryMenuBuilder getMenuBuilder() { return menuBuilder; }
    public SurgeryCompletionHandler getCompletionHandler() { return completionHandler; }
    public SurgeryItemHandler getItemHandler() { return itemHandler; }
    public SurgeryMechanicsManager getMechanicsManager() { return mechanicsManager; }
}
