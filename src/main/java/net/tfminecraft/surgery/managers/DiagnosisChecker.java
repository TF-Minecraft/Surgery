package net.tfminecraft.surgery.managers;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

// ==============================================
// Helper class to check diagnosis categories from config
// ==============================================
public class DiagnosisChecker {
    
    private final JavaPlugin plugin;
    private List<String> fluDiagnoses;
    
    public DiagnosisChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    // ==============================================
    // Loads configurable diagnosis categories
    // ==============================================
    public void loadConfig() {
        fluDiagnoses = plugin.getConfig().getStringList("flu-diagnoses");
    }
    
    // ==============================================
    // Checks if a diagnosis should have bones
    // A diagnosis has bones if it's defined in the bone-counts section
    // ==============================================
    public boolean hasBones(String diagnosis) {
        return plugin.getConfig().contains("bone-counts." + diagnosis);
    }
    
    // ==============================================
    // Checks if a diagnosis is a flu type
    // Flu types are defined in the flu-diagnoses config list
    // ==============================================
    public boolean isFlu(String diagnosis) {
        return fluDiagnoses != null && fluDiagnoses.contains(diagnosis);
    }
}
