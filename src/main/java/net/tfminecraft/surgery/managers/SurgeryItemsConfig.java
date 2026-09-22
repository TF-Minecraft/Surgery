package net.tfminecraft.surgery.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

// ==============================================
// Configuration for surgery item paths
// ==============================================
public class SurgeryItemsConfig {
    
    private final JavaPlugin plugin;
    private FileConfiguration config;
    
    // Item paths array (index matches original SURGERY_ITEMS)
    private final String[] itemPaths = new String[14];
    
    public SurgeryItemsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    // ==============================================
    // Load or create the surgeryItemsConfig.yml
    // ==============================================
    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "surgeryItemsConfig.yml");
        
        // Create config file if it doesn't exist
        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream in = plugin.getResource("surgeryItemsConfig.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create surgeryItemsConfig.yml: " + e.getMessage());
            }
        }
        
        // Load the config
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Load item paths into array
        itemPaths[0] = config.getString("items.sponge", "m.surgery.sponge");
        itemPaths[1] = config.getString("items.scalpel", "m.surgery.scalpel");
        itemPaths[2] = config.getString("items.stitches", "m.surgery.stitches");
        itemPaths[3] = config.getString("items.antibiotics", "m.surgery.antibiotics");
        itemPaths[4] = config.getString("items.antiseptic", "m.surgery.antiseptic");
        itemPaths[5] = config.getString("items.surgical-glove", "m.surgery.surgical_glove");
        itemPaths[6] = config.getString("items.ultrasound", "m.surgery.ultrasound");
        itemPaths[7] = config.getString("items.lab-kit", "m.surgery.lab_kit");
        itemPaths[8] = config.getString("items.anesthetic", "m.surgery.anesthetic");
        itemPaths[9] = config.getString("items.defibrillator", "m.surgery.defibrillator");
        itemPaths[10] = config.getString("items.pins", "m.surgery.pins");
        itemPaths[11] = config.getString("items.splint", "m.surgery.splint");
        itemPaths[12] = config.getString("items.clamp", "m.surgery.clamp");
        itemPaths[13] = config.getString("items.transfusion", "m.surgery.transfusion");
    }
    
    // ==============================================
    // Get the item paths array
    // ==============================================
    public String[] getItemPaths() {
        return itemPaths;
    }
    
    // ==============================================
    // Get a specific item path by index
    // ==============================================
    public String getItemPath(int index) {
        if (index >= 0 && index < itemPaths.length) {
            return itemPaths[index];
        }
        return null;
    }
}
