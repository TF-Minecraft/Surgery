package net.tfminecraft.surgery.managers;

import net.tfminecraft.tlibs.enums.APIType;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.TLibs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

// ==============================================
// Main file for the surgery menu system
// Delegates manager files for different responsibilities
// ==============================================
public class SurgeryMenuManager {
    
    private final JavaPlugin plugin;
    private final SurgeryItemsConfig itemsConfig;
    private ItemAPI api;
    
    // Specialized managers
    private DiagnosisChecker diagnosisChecker;
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
        api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
        if (api == null) {
            plugin.getLogger().severe("[Surgery] TLibs ItemAPI is unavailable - disabling plugin.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        // Initialize all managers in dependency order
        diagnosisChecker = new DiagnosisChecker(plugin);
        stateManager = new SurgeryStateManager();
        uiUpdater = new SurgeryUIUpdater(plugin, stateManager, diagnosisChecker);
        completionHandler = new SurgeryCompletionHandler(plugin, stateManager, uiUpdater);
        mechanicsManager = new SurgeryMechanicsManager(plugin, api, stateManager, uiUpdater, completionHandler, diagnosisChecker, itemsConfig);
        menuBuilder = new SurgeryMenuBuilder(plugin, api, stateManager, uiUpdater, itemsConfig);
        itemHandler = new SurgeryItemHandler(plugin, api, stateManager, uiUpdater, mechanicsManager, completionHandler, diagnosisChecker, itemsConfig);
        
        // Initialize any managers that need config
        mechanicsManager.initialize();
        itemHandler.initialize();
        
        plugin.getLogger().info("[Surgery] Surgery menu manager initialized!");
    }
    
    // ==============================================
    // Opens the surgery menu for the surgeon, operating on the specified patient
    // ==============================================
    public void openSurgeryMenu(Player surgeon, Player patient) {
        stateManager.setPatientName(surgeon.getUniqueId(), patient.getName());
        stateManager.setPatientUuid(surgeon.getUniqueId(), patient.getUniqueId());
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
        java.util.UUID surgeonId = stateManager.findSurgeonForPatient(patient.getUniqueId());
        if (surgeonId == null) {
            return;
        }
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
    public SurgeryUIUpdater getUiUpdater() { return uiUpdater; }
    public SurgeryMenuBuilder getMenuBuilder() { return menuBuilder; }
    public SurgeryCompletionHandler getCompletionHandler() { return completionHandler; }
    public SurgeryItemHandler getItemHandler() { return itemHandler; }
    public SurgeryMechanicsManager getMechanicsManager() { return mechanicsManager; }
}
