package net.tfminecraft.surgery.managers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;

// ==============================================
// Handles all UI updates for the surgery menu
// ==============================================
public class SurgeryUIUpdater {
    
    private final JavaPlugin plugin;
    private final SurgeryStateManager stateManager;
    private final DiagnosisChecker diagnosisChecker;
    private FileConfiguration messages;
    
    public SurgeryUIUpdater(JavaPlugin plugin, SurgeryStateManager stateManager, DiagnosisChecker diagnosisChecker) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.diagnosisChecker = diagnosisChecker;
        loadMessages();
    }
    
    // ==============================================
    // Loads the messages.yml file
    // ==============================================
    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }
    
    // ==============================================
    // Creates an info block with material, name, and description
    // ==============================================
    public ItemStack createInfoBlock(Material material, String name, String description) {
        ItemStack block = new ItemStack(material);
        ItemMeta meta = block.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.Arrays.asList(description));
            block.setItemMeta(meta);
        }
        return block;
    }
    
    // ==============================================
    // Creates an info block with material, name, and lore list
    // ==============================================
    public ItemStack createInfoBlock(Material material, String name, java.util.List<String> lore) {
        ItemStack block = new ItemStack(material);
        ItemMeta meta = block.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            block.setItemMeta(meta);
        }
        return block;
    }
    
    // ==============================================
    // Updates the incision block
    // ==============================================
    public void updateIncisionBlock(Inventory menu, UUID playerId, int incisions) {
        Material incisionColor = SurgeryConstants.getIncisionColor(incisions);
        ItemStack incisionBlock = createInfoBlock(incisionColor, ChatColor.GOLD + "Incisions", ChatColor.GRAY + String.valueOf(incisions));
        menu.setItem(SurgeryConstants.SLOT_INCISIONS, incisionBlock);
    }
    
    // ==============================================
    // Updates the temperature block
    // ==============================================
    public void updateTemperatureBlock(Inventory menu, UUID playerId, double temp) {
        Material tempColor = SurgeryConstants.getTemperatureColor(temp);
        String tempDisplay = SurgeryConstants.formatTemperature(temp);
        ItemStack tempBlock = createInfoBlock(tempColor, ChatColor.GOLD + "Temperature", ChatColor.GRAY + tempDisplay);
        menu.setItem(SurgeryConstants.SLOT_TEMPERATURE, tempBlock);
    }
    
    // ==============================================
    // Updates the operation site block
    // ==============================================
    public void updateOperationSiteBlock(Inventory menu, UUID playerId, String status) {
        Material siteColor = SurgeryConstants.getOperationSiteColor(status);
        ItemStack siteBlock = createInfoBlock(siteColor, ChatColor.GOLD + "Operation Site", ChatColor.GRAY + status);
        menu.setItem(SurgeryConstants.SLOT_OPERATION_SITE, siteBlock);
    }
    
    // ==============================================
    // Updates the status block
    // ==============================================
    public void updateStatusBlock(Inventory menu, UUID playerId, String status) {
        Material statusColor = SurgeryConstants.getStatusColor(status);
        ItemStack statusBlock = createInfoBlock(statusColor, ChatColor.GOLD + "Status", ChatColor.GRAY + status);
        menu.setItem(SurgeryConstants.SLOT_STATUS, statusBlock);
    }
    
    // ==============================================
    // Updates the pulse block
    // ==============================================
    public void updatePulseBlock(Inventory menu, UUID playerId, String pulse) {
        Material pulseColor = SurgeryConstants.getPulseColor(pulse);
        ItemStack pulseBlock = createInfoBlock(pulseColor, ChatColor.GOLD + "Pulse", ChatColor.GRAY + pulse);
        menu.setItem(SurgeryConstants.SLOT_PULSE, pulseBlock);
    }
    
    // ==============================================
    // Updates the diagnosis block
    // ==============================================
    public void updateDiagnosisBlock(Inventory menu, UUID playerId) {
        String diagnosis = stateManager.getDiagnosis(playerId);
        boolean cured = stateManager.isCured(playerId);
        
        Material diagnosisColor = cured ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String diagnosisText = diagnosis != null ? ChatColor.GRAY + diagnosis : ChatColor.GRAY + "The patient has not been diagnosed.";
        
        ItemStack diagnosisBlock;
        if (diagnosis != null && diagnosisChecker.hasBones(diagnosis)) {
            // ==============================================
            // Show bone information in description
            // ==============================================
            int brokenBones = stateManager.getRevealedBrokenBones(playerId);
            int shatteredBones = stateManager.getRevealedShatteredBones(playerId);
            
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(diagnosisText);
            lore.add(ChatColor.YELLOW + "Broken Bones: " + ChatColor.GRAY + brokenBones);
            lore.add(ChatColor.RED + "Shattered Bones: " + ChatColor.GRAY + shatteredBones);
            
            diagnosisBlock = createInfoBlock(diagnosisColor, ChatColor.GOLD + "Diagnosis", lore);
        } else {
            diagnosisBlock = createInfoBlock(diagnosisColor, ChatColor.GOLD + "Diagnosis", diagnosisText);
        }
        
        menu.setItem(SurgeryConstants.SLOT_DIAGNOSIS, diagnosisBlock);
    }
    
    // ==============================================
    // Sends a numbered message to the player
    // ==============================================
    public void sendNumberedMessage(Player player, String message) {
        UUID playerId = player.getUniqueId();
        int moveNumber = stateManager.getMoveCount(playerId) + 1;
        String prefix = ChatColor.GRAY + "" + ChatColor.BOLD + "[Move " + moveNumber + "] " + ChatColor.RESET;
        player.sendMessage(prefix + message);
    }
    
    // ==============================================
    // Gets a message from messages.yml and translates color codes
    // ==============================================
    public String getMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&', messages.getString(path, ""));
    }

    // ==============================================
    // Gets a message with a fallback default, translating color codes
    // ==============================================
    public String getMessage(String path, String def) {
        return ChatColor.translateAlternateColorCodes('&', messages.getString(path, def));
    }
    
    // ==============================================
    // Gets a list of messages from messages.yml
    // ==============================================
    public java.util.List<String> getMessageList(String path) {
        return messages.getStringList(path);
    }
}
