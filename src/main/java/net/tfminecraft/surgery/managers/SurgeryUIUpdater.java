package net.tfminecraft.surgery.managers;

import net.tfminecraft.surgery.procedures.Procedure;
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
    private FileConfiguration messages;
    
    public SurgeryUIUpdater(JavaPlugin plugin, SurgeryStateManager stateManager) {
        this.plugin = plugin;
        this.stateManager = stateManager;
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
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
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
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
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
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void updateIncisionBlock(Inventory menu, UUID playerId, int incisions) {
        Material incisionColor = SurgeryConstants.getIncisionColor(incisions);
        ItemStack incisionBlock = createInfoBlock(incisionColor, ChatColor.GOLD + "Incisions", ChatColor.GRAY + String.valueOf(incisions));
        menu.setItem(SurgeryConstants.SLOT_INCISIONS, incisionBlock);
    }
    
    // ==============================================
    // Updates the temperature block
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void updateTemperatureBlock(Inventory menu, UUID playerId, double temp) {
        Material tempColor = SurgeryConstants.getTemperatureColor(temp);
        String tempDisplay = SurgeryConstants.formatTemperature(temp);
        ItemStack tempBlock = createInfoBlock(tempColor, ChatColor.GOLD + "Temperature", ChatColor.GRAY + tempDisplay);
        menu.setItem(SurgeryConstants.SLOT_TEMPERATURE, tempBlock);
    }
    
    // ==============================================
    // Updates the operation site block
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void updateOperationSiteBlock(Inventory menu, UUID playerId, String status) {
        Material siteColor = SurgeryConstants.getOperationSiteColor(status);
        ItemStack siteBlock = createInfoBlock(siteColor, ChatColor.GOLD + "Operation Site", ChatColor.GRAY + status);
        menu.setItem(SurgeryConstants.SLOT_OPERATION_SITE, siteBlock);
    }
    
    // ==============================================
    // Updates the status block
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void updateStatusBlock(Inventory menu, UUID playerId, String status) {
        Material statusColor = SurgeryConstants.getStatusColor(status);
        ItemStack statusBlock = createInfoBlock(statusColor, ChatColor.GOLD + "Status", ChatColor.GRAY + status);
        menu.setItem(SurgeryConstants.SLOT_STATUS, statusBlock);
    }
    
    // ==============================================
    // Updates the pulse block
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void updatePulseBlock(Inventory menu, UUID playerId, String pulse) {
        Material pulseColor = SurgeryConstants.getPulseColor(pulse);
        ItemStack pulseBlock = createInfoBlock(pulseColor, ChatColor.GOLD + "Pulse", ChatColor.GRAY + pulse);
        menu.setItem(SurgeryConstants.SLOT_PULSE, pulseBlock);
    }
    
    // ==============================================
    // Updates the diagnosis block
    // Before the stethoscope examination only the patient's complaint is known
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void updateDiagnosisBlock(Inventory menu, UUID playerId) {
        String ailment = stateManager.getAilmentName(playerId);
        java.util.List<String> lore = new java.util.ArrayList<>();
        Material color;

        Procedure procedure = stateManager.getProcedure(playerId);
        if (!stateManager.isExamined(playerId) || procedure == null) {
            color = Material.RED_CONCRETE;
            lore.add(ChatColor.GRAY + "Complaint: " + ChatColor.WHITE + ailment);
            lore.add(ChatColor.GRAY + "Examine the patient with the stethoscope.");
        } else {
            color = stateManager.isCured(playerId) ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE;
            lore.add(ChatColor.GRAY + "The patient suffers from " + ChatColor.WHITE + procedure.name());
            lore.add(ChatColor.GRAY + "Ailment: " + ChatColor.WHITE + ailment);
            lore.add(ChatColor.GRAY + "Incisions needed: " + ChatColor.WHITE + procedure.requiredIncisions());
            if (procedure.hasBones()) {
                lore.add(ChatColor.YELLOW + "Broken Bones: " + ChatColor.GRAY + stateManager.getRevealedBrokenBones(playerId));
                lore.add(ChatColor.RED + "Shattered Bones: " + ChatColor.GRAY + stateManager.getRevealedShatteredBones(playerId));
            }
            if (stateManager.isCured(playerId)) {
                lore.add(ChatColor.GREEN + "Treated and dressed");
            }
        }

        menu.setItem(SurgeryConstants.SLOT_DIAGNOSIS, createInfoBlock(color, ChatColor.GOLD + "Diagnosis", lore));
    }

    // ==============================================
    // Sends a numbered message to the player
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void sendNumberedMessage(Player player, String message) {
        UUID playerId = player.getUniqueId();
        int moveNumber = stateManager.getMoveCount(playerId) + 1;
        String prefix = ChatColor.GRAY + "" + ChatColor.BOLD + "[Move " + moveNumber + "] " + ChatColor.RESET;
        player.sendMessage(prefix + message);
    }
    
    // ==============================================
    // Gets a message from messages.yml and translates color codes
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public String getMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&', messages.getString(path, ""));
    }

    // ==============================================
    // Gets a message with a fallback default, translating color codes
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
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
