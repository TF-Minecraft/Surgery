package net.tfminecraft.surgery.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;

// ==============================================
// Configuration for surgery item paths
// ==============================================
public class SurgeryItemsConfig {

    private final JavaPlugin plugin;
    private final Map<SurgeryTool, String> itemPaths = new EnumMap<>(SurgeryTool.class);

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

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        for (SurgeryTool tool : SurgeryTool.values()) {
            itemPaths.put(tool, config.getString("items." + tool.getConfigKey(), tool.getDefaultPath()));
        }
    }

    // ==============================================
    // Get the TLibs item path for a tool
    // ==============================================
    public String getItemPath(SurgeryTool tool) {
        return itemPaths.get(tool);
    }
}
