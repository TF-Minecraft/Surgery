package net.tfminecraft.surgery.managers;

import net.tfminecraft.rpcharacters.api.HealingInjuries.HealingInjury;
import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent;
import net.tfminecraft.surgery.procedures.ProcedureRegistry;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.TLibs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.inventory.Inventory;
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
        CharacterSession surgeonSession = Characters.currentSession(surgeon).orElse(null);
        CharacterSession patientSession = Characters.currentSession(patient).orElse(null);
        if (surgeonSession == null || patientSession == null || stateManager.hasSession(surgeon.getUniqueId())
                || stateManager.isPatientInSurgery(patient.getUniqueId())) {
            return;
        }
        stateManager.setPatientName(surgeon.getUniqueId(), patient.getName());
        stateManager.setPatientUuid(surgeon.getUniqueId(), patient.getUniqueId());
        stateManager.setSurgeonSession(surgeon.getUniqueId(), surgeonSession);
        stateManager.setPatientSession(surgeon.getUniqueId(), patientSession);
        stateManager.setAilment(surgeon.getUniqueId(), ailment.traitId(), ailment.displayName(),
            procedures.get(ailment.traitId()));
        menuBuilder.buildAndOpenMenu(surgeon);
    }

    // ==============================================
    // Checks if an inventory is the surgery menu (by holder, not title)
    // ==============================================
    public boolean isSurgeryMenu(org.bukkit.inventory.Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof SurgeryMenuHolder;
    }

    // ==============================================
    // Handles item clicks within the surgery menu
    // Delegates to the item handler
    // ==============================================
    public void handleItemClick(Player player, Inventory inventory, ItemStack clickedItem, int slot) {
        if (ownsMenu(player, inventory)) {
            itemHandler.handleItemClick(player, clickedItem, slot);
        }
    }

    private boolean ownsMenu(Player player, Inventory inventory) {
        return inventory.getHolder(false) instanceof SurgeryMenuHolder holder
                && holder.matches(player, stateManager.getOperationId(player.getUniqueId()));
    }

    // ==============================================
    // Handles menu abandonment (player closed menu early)
    // Called from PlayerListener
    // ==============================================
    public void handleSurgeryAbandonment(Player player, Inventory inventory) {
        if (ownsMenu(player, inventory)) {
            completionHandler.handleAbandonment(player);
        }
    }

    public void handleSessionEnd(CharacterSessionEndEvent event) {
        CharacterSession ending = event.getSession();
        UUID owner = ending.ref().ownerId();
        requestManager.forget(ending);
        endOperation(owner, ending, event.getReason());
        UUID surgeon = stateManager.findSurgeonForPatient(owner);
        if (surgeon != null) {
            endOperation(surgeon, ending, event.getReason());
        }
    }

    private void endOperation(UUID surgeonId, CharacterSession ending, CharacterSessionEndEvent.Reason reason) {
        if (!ending.equals(stateManager.getSurgeonSession(surgeonId))
                && !ending.equals(stateManager.getPatientSession(surgeonId))) {
            return;
        }
        Player surgeon = plugin.getServer().getPlayer(surgeonId);
        if (surgeon == null) {
            stateManager.cleanup(surgeonId);
            return;
        }
        Inventory inventory = surgeon.getOpenInventory().getTopInventory();
        boolean close = ownsMenu(surgeon, inventory);
        completionHandler.handleEnd(surgeon, ending, reason);
        if (close && surgeon.getOpenInventory().getTopInventory() == inventory) {
            surgeon.closeInventory();
        }
    }

    public void shutdown() {
        requestManager.clear();
        if (stateManager != null) {
            stateManager.clear();
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
