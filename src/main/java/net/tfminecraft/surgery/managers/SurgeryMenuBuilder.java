package net.tfminecraft.surgery.managers;

import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// ==============================================
// Builds and initializes the surgery menu
// ==============================================
public class SurgeryMenuBuilder {
    
    private final JavaPlugin plugin;
    private final ItemAPI api;
    private final SurgeryStateManager stateManager;
    private final SurgeryUIUpdater uiUpdater;
    private final SurgeryItemsConfig itemsConfig;

    public SurgeryMenuBuilder(JavaPlugin plugin, ItemAPI api, SurgeryStateManager stateManager, SurgeryUIUpdater uiUpdater, SurgeryItemsConfig itemsConfig) {
        this.plugin = plugin;
        this.api = api;
        this.stateManager = stateManager;
        this.uiUpdater = uiUpdater;
        this.itemsConfig = itemsConfig;
    }
    
    // ==============================================
    // Builds and opens the surgery menu for a player
    // ==============================================
    public void buildAndOpenMenu(Player player) {
        Inventory menu = new SurgeryMenuHolder().getInventory();
        
        // Tools on the table from the start; the tincture appears after the
        // thermometer, and the dressing, smelling salts, silver wire, splint,
        // and artery forceps appear when needed
        SurgeryTool[] startingTools = {
            SurgeryTool.SPONGE, SurgeryTool.SCALPEL, SurgeryTool.SUTURE, SurgeryTool.CARBOLIC_ACID,
            SurgeryTool.STETHOSCOPE, SurgeryTool.THERMOMETER, SurgeryTool.CHLOROFORM, SurgeryTool.TRANSFUSION
        };
        for (SurgeryTool tool : startingTools) {
            String itemPath = itemsConfig.getItemPath(tool);
            ItemStack item = itemPath == null ? null : api.getCreator().getItemFromPath(itemPath);
            if (item != null) {
                menu.setItem(tool.getSlot(), item);
            } else {
                plugin.getLogger().warning("[Surgery] Could not load item: " + itemPath);
            }
        }

        // Add placeholder info blocks
        for (int i = SurgeryConstants.INFO_SLOT_FIRST; i <= SurgeryConstants.INFO_SLOT_LAST; i++) {
            ItemStack infoBlock = uiUpdater.createInfoBlock(Material.RED_CONCRETE, " ", "");
            menu.setItem(i, infoBlock);
        }
        
        initializePlayerState(player, menu);
        player.openInventory(menu);
    }
    
    // ==============================================
    // Initializes player state when opening menu
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private void initializePlayerState(Player player, Inventory menu) {
        UUID playerId = player.getUniqueId();
        
        // ==============================================
        // Set the first info block (slot 10) as "diagnosis"
        // ==============================================
        uiUpdater.updateDiagnosisBlock(menu, playerId);
        
        // ==============================================
        // Set the second info block (slot 11) as "pulse". Always starts at Strong
        // ==============================================
        String pulseStatus = "Strong";
        stateManager.setPulse(playerId, pulseStatus);
        Material pulseColor = SurgeryConstants.getPulseColor(pulseStatus);
        ItemStack pulseBlock = uiUpdater.createInfoBlock(pulseColor, ChatColor.GOLD + "Pulse", ChatColor.GRAY + pulseStatus);
        menu.setItem(SurgeryConstants.SLOT_PULSE, pulseBlock);
        
        // ==============================================
        // Set the third info block (slot 12) as "status". Always starts at Awake
        // ==============================================
        String patientStatus = "Awake";
        stateManager.setStatus(playerId, patientStatus);
        Material statusColor = SurgeryConstants.getStatusColor(patientStatus);
        ItemStack statusBlock = uiUpdater.createInfoBlock(statusColor, ChatColor.GOLD + "Status", ChatColor.GRAY + patientStatus);
        menu.setItem(SurgeryConstants.SLOT_STATUS, statusBlock);
        
        // ==============================================
        // Randomize if patient has rising temperature (50% chance)
        // ==============================================
        boolean hasRisingTemp = ThreadLocalRandom.current().nextBoolean();
        stateManager.setHasRisingTemp(playerId, hasRisingTemp);
        
        // ==============================================
        // Set the fourth info block (slot 13) as "temperature"
        // If patient has rising temp, start with random temperature from config range
        // ==============================================
        double temperature;
        if (hasRisingTemp) {
            double minTemp = plugin.getConfig().getDouble("temperature.rising-temp-min", 98.6);
            double maxTemp = plugin.getConfig().getDouble("temperature.rising-temp-max", 104.0);
            temperature = minTemp + (ThreadLocalRandom.current().nextDouble() * (maxTemp - minTemp));
        } else {
            temperature = plugin.getConfig().getDouble("temperature.normal", 98.6);
        }
        stateManager.setTemperature(playerId, temperature);
        Material tempColor = SurgeryConstants.getTemperatureColor(temperature);
        String tempDisplay = SurgeryConstants.formatTemperature(temperature);
        ItemStack tempBlock = uiUpdater.createInfoBlock(tempColor, ChatColor.GOLD + "Temperature", ChatColor.GRAY + tempDisplay);
        menu.setItem(SurgeryConstants.SLOT_TEMPERATURE, tempBlock);
        
        // ==============================================
        // Set the fifth info block (slot 14) as "operation site". Always starts at Not sanitized
        // ==============================================
        String opSiteStatus = "Not sanitized";
        stateManager.setOperationSite(playerId, opSiteStatus);
        Material opSiteColor = SurgeryConstants.getOperationSiteColor(opSiteStatus);
        ItemStack opSiteBlock = uiUpdater.createInfoBlock(opSiteColor, ChatColor.GOLD + "Operation site", ChatColor.GRAY + opSiteStatus);
        menu.setItem(SurgeryConstants.SLOT_OPERATION_SITE, opSiteBlock);
        
        // ==============================================
        // Set the sixth info block (slot 15) as "incisions". Always starts at 0
        // ==============================================
        int incisions = 0;
        stateManager.setIncisions(playerId, incisions);
        Material incisionColor = SurgeryConstants.getIncisionColor(incisions);
        ItemStack incisionBlock = uiUpdater.createInfoBlock(incisionColor, ChatColor.GOLD + "Incisions", ChatColor.GRAY + String.valueOf(incisions));
        menu.setItem(SurgeryConstants.SLOT_INCISIONS, incisionBlock);
        
        // ==============================================
        // Set the seventh info block (slot 16) as "skill fail". Starts empty
        // ==============================================
        stateManager.setSkillFail(playerId, "");
        ItemStack skillFailBlock = uiUpdater.createInfoBlock(Material.LIME_CONCRETE, ChatColor.GOLD + "Skill Fail", ChatColor.GRAY + "Nothing to show here");
        menu.setItem(SurgeryConstants.SLOT_SKILL_FAIL, skillFailBlock);

        // ==============================================
        // Initialize other state variables
        // ==============================================
        stateManager.setBleeding(playerId, false);
        stateManager.setCured(playerId, false);
        stateManager.setAntisepticProtection(playerId, false);
        stateManager.setSpongeEffect(playerId, false);
        stateManager.setMoveCount(playerId, 0);
        stateManager.setMovesSinceLastSponge(playerId, 0);
        stateManager.setUnconsciousTimer(playerId, 0);

        // ==============================================
        // Initialize bone counts (will be set after diagnosis)
        // ==============================================
        stateManager.setBrokenBones(playerId, 0);
        stateManager.setShatteredBones(playerId, 0);
        stateManager.setRevealedBrokenBones(playerId, 0);
        stateManager.setRevealedShatteredBones(playerId, 0);
    }
}
