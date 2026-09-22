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
        
        // Map specific slots to item config indexes
        int[][] slotMapping = {
            {SurgeryConstants.SLOT_SPONGE, 0},
            {SurgeryConstants.SLOT_SCALPEL, 1},
            {SurgeryConstants.SLOT_STITCHES, 2},
            {SurgeryConstants.SLOT_ANTISEPTIC, 4},
            {SurgeryConstants.SLOT_ULTRASOUND, 6},
            {SurgeryConstants.SLOT_LAB_KIT, 7},
            {SurgeryConstants.SLOT_ANESTHETIC, 8},
            {SurgeryConstants.SLOT_TRANSFUSION, 13}

            // Antibiotics appear after using the lab kit
            // Surgical Glove, Defibrillator, Pins, Splint, and Clamp
            // appear dynamically
        };
        
        // Place items in the specified slots
        for (int[] mapping : slotMapping) {
            int slot = mapping[0];
            int itemIndex = mapping[1];
            
            String itemPath = itemsConfig.getItemPath(itemIndex);
            if (itemPath != null) {
                ItemStack item = api.getCreator().getItemFromPath(itemPath);
                if (item != null) {
                    menu.setItem(slot, item);
                } else {
                    plugin.getLogger().warning("[Surgery] Could not load item: " + itemPath);
                }
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
    private void initializePlayerState(Player player, Inventory menu) {
        UUID playerId = player.getUniqueId();
        
        // ==============================================
        // Set the first info block (slot 10) as "diagnosis"
        // ==============================================
        ItemStack diagnosisBlock = uiUpdater.createInfoBlock(Material.RED_CONCRETE, ChatColor.GOLD + "Diagnosis", ChatColor.GRAY + "The patient has not been diagnosed.");
        menu.setItem(SurgeryConstants.SLOT_DIAGNOSIS, diagnosisBlock);
        
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
