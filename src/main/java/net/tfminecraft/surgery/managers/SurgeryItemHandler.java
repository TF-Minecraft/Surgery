package net.tfminecraft.surgery.managers;

import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// ==============================================
// Handles all surgery item clicks and tool logic
// ==============================================
public class SurgeryItemHandler {
    
    private final JavaPlugin plugin;
    private final ItemAPI api;
    private final SurgeryStateManager stateManager;
    private final SurgeryUIUpdater uiUpdater;
    private final SurgeryMechanicsManager mechanicsManager;
    private final SurgeryCompletionHandler completionHandler;
    private final DiagnosisChecker diagnosisChecker;
    private final SurgeryItemsConfig itemsConfig;

    // Skill fail message lists
    private java.util.List<String> skillFailLabKit;
    private java.util.List<String> skillFailUltrasound;
    private java.util.List<String> skillFailScalpel;
    private java.util.List<String> skillFailStitches;
    private java.util.List<String> skillFailAntibiotics;
    private java.util.List<String> skillFailTransfusion;
    private java.util.List<String> skillFailAntiseptic;
    private java.util.List<String> skillFailSponge;
    private java.util.List<String> skillFailAnesthetic;
    private java.util.List<String> skillFailDefibrillator;
    private java.util.List<String> skillFailSplint;
    private java.util.List<String> skillFailPins;
    private java.util.List<String> skillFailClamp;
    private java.util.List<String> skillFailSurgicalGlove;
    
    // Diagnoses list
    private java.util.List<String> diagnosesList;
    
    public SurgeryItemHandler(JavaPlugin plugin, ItemAPI api, SurgeryStateManager stateManager, 
                              SurgeryUIUpdater uiUpdater, SurgeryMechanicsManager mechanicsManager,
                              SurgeryCompletionHandler completionHandler, DiagnosisChecker diagnosisChecker,
                              SurgeryItemsConfig itemsConfig) {
        this.plugin = plugin;
        this.api = api;
        this.stateManager = stateManager;
        this.uiUpdater = uiUpdater;
        this.mechanicsManager = mechanicsManager;
        this.completionHandler = completionHandler;
        this.diagnosisChecker = diagnosisChecker;
        this.itemsConfig = itemsConfig;
    }

    // ==============================================
    // Initializes skill fail messages from messages.yml
    // ==============================================
    public void initialize() {
        skillFailLabKit = uiUpdater.getMessageList("skill-fail-lab-kit");
        skillFailUltrasound = uiUpdater.getMessageList("skill-fail-ultrasound");
        skillFailScalpel = uiUpdater.getMessageList("skill-fail-scalpel");
        skillFailStitches = uiUpdater.getMessageList("skill-fail-stitches");
        skillFailAntibiotics = uiUpdater.getMessageList("skill-fail-antibiotics");
        skillFailTransfusion = uiUpdater.getMessageList("skill-fail-transfusion");
        skillFailAntiseptic = uiUpdater.getMessageList("skill-fail-antiseptic");
        skillFailSponge = uiUpdater.getMessageList("skill-fail-sponge");
        skillFailAnesthetic = uiUpdater.getMessageList("skill-fail-anesthetic");
        skillFailDefibrillator = uiUpdater.getMessageList("skill-fail-defibrillator");
        skillFailSplint = uiUpdater.getMessageList("skill-fail-splint");
        skillFailPins = uiUpdater.getMessageList("skill-fail-pins");
        skillFailClamp = uiUpdater.getMessageList("skill-fail-clamp");
        skillFailSurgicalGlove = uiUpdater.getMessageList("skill-fail-surgical-glove");
        
        diagnosesList = plugin.getConfig().getStringList("diagnoses");
    }
    
    // ==============================================
    // Handles clicking on a surgery menu item
    // ==============================================
    public void handleItemClick(Player player, ItemStack clickedItem, int slot) {
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        
        // Ignore clicks on info blocks
        if (slot >= SurgeryConstants.INFO_SLOT_FIRST && slot <= SurgeryConstants.INFO_SLOT_LAST) {
            return;
        }

        // Special check for scalpel - cannot be used when patient is awake
        if (slot == SurgeryConstants.SLOT_SCALPEL) {
            String patientStatus = stateManager.getStatus(playerId);
            if (patientStatus.equals("Awake")) {
                completionHandler.failSurgery(player, uiUpdater.getMessage("failure-stabbed-awake"));
                return;
            }
        }
        
        // Check if the player has this item in their inventory and remove it
        if (removeItemFromPlayer(player, clickedItem)) {
            // Process per-move effects before updating menu
            mechanicsManager.processMoveEffects(player, slot);

            // Move effects may have ended the surgery (death timers, fever, bleed-out);
            // the session is cleaned up and the menu is closed, so stop here
            if (!stateManager.hasSession(playerId)) {
                return;
            }

            // Update the menu based on what was clicked
            updateMenu(player, slot);
            
            // Play correct sound only if no skill fail occurred
            String skillFailMsg = stateManager.getSkillFail(playerId);
            if (skillFailMsg.isEmpty()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        } else {
            // Player doesn't have the item - show error message
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                uiUpdater.getMessage("item-not-in-inventory")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }
    
    // ==============================================
    // Updates the menu when specific items are clicked
    // ==============================================
    private void updateMenu(Player player, int clickedSlot) {
        Inventory menu = player.getOpenInventory().getTopInventory();
        UUID playerId = player.getUniqueId();
        boolean skillFail = isSkillFail(playerId);
        
        String skillFailMsg = "";
        
        // Show bleeding warning if bleeding
        if (stateManager.isBleeding(playerId)) {
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("bleeding-warning"));
        }
        
        // ==============================================
        // Skill fail checks for each item
        // ==============================================
        switch (clickedSlot) {
            case SurgeryConstants.SLOT_SPONGE: skillFailMsg = handleSponge(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_SCALPEL: skillFailMsg = handleScalpel(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_STITCHES: skillFailMsg = handleStitches(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_ANTIBIOTICS: skillFailMsg = handleAntibiotics(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_ANTISEPTIC: skillFailMsg = handleAntiseptic(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_SURGICAL_GLOVE: skillFailMsg = handleSurgicalGlove(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_ULTRASOUND: skillFailMsg = handleUltrasound(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_LAB_KIT: skillFailMsg = handleLabKit(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_ANESTHETIC: skillFailMsg = handleAnesthetic(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_DEFIBRILLATOR: skillFailMsg = handleDefibrillator(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_PINS: skillFailMsg = handlePins(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_SPLINT: skillFailMsg = handleSplint(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_CLAMP: skillFailMsg = handleClamp(player, menu, playerId, skillFail); break;
            case SurgeryConstants.SLOT_TRANSFUSION: skillFailMsg = handleTransfusion(player, menu, playerId, skillFail); break;
        }
        
        // ==============================================
        // Update skill fail block
        // ==============================================
        stateManager.setSkillFail(playerId, skillFailMsg);
        Material skillFailColor = skillFailMsg.isEmpty() ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        String skillFailDisplay = skillFailMsg.isEmpty() ? ChatColor.GRAY + "Nothing to show here" : ChatColor.GRAY + skillFailMsg;
        ItemStack skillFailBlock = uiUpdater.createInfoBlock(skillFailColor, ChatColor.GOLD + "Skill Fail", skillFailDisplay);
        menu.setItem(SurgeryConstants.SLOT_SKILL_FAIL, skillFailBlock);
        
        // Clear sponge effect (it only lasts for one move)
        stateManager.setSpongeEffect(playerId, false);
        
        // Play "broken item" sound if skill fail occurred
        if (!skillFailMsg.isEmpty()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        }
        
        // Check if surgery is complete after this move (if diagnosis was already cured)
        if (stateManager.isCured(playerId) && completionHandler.isSurgerySuccessful(playerId)) {
            completionHandler.handleSuccess(player);
        }
    }
    
    // ==============================================
    // Labkit functionality: reveals antibiotics
    // ==============================================
    private String handleLabKit(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(skillFailLabKit);
        } else {
            menu.setItem(SurgeryConstants.SLOT_LAB_KIT, null);
            ItemStack antibiotics = api.getCreator().getItemFromPath(itemsConfig.getItemPath(3));
            if (antibiotics != null) {
                menu.setItem(SurgeryConstants.SLOT_ANTIBIOTICS, antibiotics);
            }
            return "";
        }
    }
    
    // ==============================================
    // Ultrasound functionality: reveals diagnosis and sets temperature for flu diagnoses
    // ==============================================
    private String handleUltrasound(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(skillFailUltrasound);
        } else {
            menu.setItem(SurgeryConstants.SLOT_ULTRASOUND, null);
            String diagnosis = diagnosesList.get(ThreadLocalRandom.current().nextInt(diagnosesList.size()));
            stateManager.setDiagnosis(playerId, diagnosis);
            
            // If diagnosis is a flu, set initial high temperature from config range
            if (diagnosisChecker.isFlu(diagnosis)) {
                double fluMin = plugin.getConfig().getDouble("temperature.flu-temp-min", 99.0);
                double fluMax = plugin.getConfig().getDouble("temperature.flu-temp-max", 104.0);
                double fluTemp = fluMin + (ThreadLocalRandom.current().nextDouble() * (fluMax - fluMin));
                stateManager.setTemperature(playerId, fluTemp);
                uiUpdater.updateTemperatureBlock(menu, playerId, fluTemp);
            }
            
            // Assign bones only for bone-related diagnoses
            if (diagnosisChecker.hasBones(diagnosis)) {
                // Get bone counts from config, or use random if not specified
                int brokenBones = plugin.getConfig().getInt("bone-counts." + diagnosis + ".broken", ThreadLocalRandom.current().nextInt(3));
                int shatteredBones = plugin.getConfig().getInt("bone-counts." + diagnosis + ".shattered", ThreadLocalRandom.current().nextInt(2));
                stateManager.setBrokenBones(playerId, brokenBones);
                stateManager.setShatteredBones(playerId, shatteredBones);
            }
            
            // Update diagnosis block
            ItemStack diagnosisBlock = uiUpdater.createInfoBlock(Material.YELLOW_CONCRETE, 
                ChatColor.GOLD + "Diagnosis", ChatColor.GRAY + "The patient suffers from " + diagnosis);
            menu.setItem(SurgeryConstants.SLOT_DIAGNOSIS, diagnosisBlock);
            
            // Check if surgical glove should appear
            mechanicsManager.checkForFixItButton(player, menu, playerId, 0);
            return "";
        }
    }
    
    // ==============================================
    // Scalpel functionality: creates incisions and affects pulse
    // ==============================================
    private String handleScalpel(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            // On skill fail: lower pulse instead of creating incision
            String currentPulse = stateManager.getPulse(playerId);
            String newPulse = SurgeryConstants.worsenPulse(currentPulse);
            stateManager.setPulse(playerId, newPulse);
            uiUpdater.updatePulseBlock(menu, playerId, newPulse);
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("pulse-weakening"));
            return getRandomSkillFail(skillFailScalpel);
        } else {
            int incisions = stateManager.getIncisions(playerId) + 1;
            stateManager.setIncisions(playerId, incisions);
            uiUpdater.updateIncisionBlock(menu, playerId, incisions);
            
            // Scalpel makes operation site unclean ("bleeding")
            String currentSite = stateManager.getOperationSite(playerId);
            if (currentSite.equals("Clean")) {
                stateManager.setOperationSite(playerId, "Unclean");
                uiUpdater.updateOperationSiteBlock(menu, playerId, "Unclean");
            }
            
            // Paper Cuts: Show examined message after configured scalpel uses
            String diagnosis = stateManager.getDiagnosis(playerId);
            int paperCutsUses = plugin.getConfig().getInt("diagnosis-mechanics.paper-cuts.scalpel-uses-required", 2);
            if (diagnosis != null && diagnosis.equals("Paper Cuts") && incisions == paperCutsUses) {
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("wounds-examined"));
            }
            
            // Reveal bones at required incision count for bone-based diagnoses
            if (diagnosis != null) {
                mechanicsManager.handleBoneReveal(player, menu, playerId, diagnosis, incisions);
            }

            // Always refresh dynamic tools; the clamp depends on incision count and
            // bleeding, which can both be true before a diagnosis exists
            mechanicsManager.updateDynamicTools(player, menu, playerId);
            
            // 50% chance for pulse to decrease when making incision
            if (ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("pulse.scalpel-decrease-chance", 0.50)) {
                String currentPulse = stateManager.getPulse(playerId);
                String newPulse = SurgeryConstants.worsenPulse(currentPulse);
                stateManager.setPulse(playerId, newPulse);
                uiUpdater.updatePulseBlock(menu, playerId, newPulse);
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("pulse-weakening"));
            }
            
            mechanicsManager.checkForFixItButton(player, menu, playerId, incisions);
            return "";
        }
    }
    
    // ==============================================
    // Stitches functionality: closes incisions and can stop bleeding
    // ==============================================
    private String handleStitches(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(skillFailStitches);
        } else {
            int incisions = Math.max(0, stateManager.getIncisions(playerId) - 1);
            stateManager.setIncisions(playerId, incisions);
            uiUpdater.updateIncisionBlock(menu, playerId, incisions);
            if (incisions == 0) {
                stateManager.setBleeding(playerId, false);
            }
            mechanicsManager.updateDynamicTools(player, menu, playerId);
            return "";
        }
    }
    
    // ==============================================
    // Antibiotics functionality: reduces temperature by 5.4°F (3.0°C)
    // ==============================================
    private String handleAntibiotics(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        double change = plugin.getConfig().getDouble("temperature.antibiotics-change", 5.4);
        if (skillFail) {
            double temp = stateManager.getTemperature(playerId) + change;
            temp = Math.min(temp, plugin.getConfig().getDouble("temperature.instant-death-threshold", 110.0));
            stateManager.setTemperature(playerId, temp);
            uiUpdater.updateTemperatureBlock(menu, playerId, temp);
            return getRandomSkillFail(skillFailAntibiotics);
        } else {
            double temp = stateManager.getTemperature(playerId) - change;
            temp = Math.max(temp, plugin.getConfig().getDouble("temperature.normal", 98.6));
            stateManager.setTemperature(playerId, temp);
            uiUpdater.updateTemperatureBlock(menu, playerId, temp);
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("temperature-reduced"));
            mechanicsManager.checkForFixItButton(player, menu, playerId, 0);
            return "";
        }
    }
    
    // ==============================================
    // Transfusion functionality: improves pulse
    // ==============================================
    private String handleTransfusion(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            stateManager.setOperationSite(playerId, "Unsanitary");
            uiUpdater.updateOperationSiteBlock(menu, playerId, "Unsanitary");
            return getRandomSkillFail(skillFailTransfusion);
        } else {
            String currentPulse = stateManager.getPulse(playerId);
            String newPulse = SurgeryConstants.improvePulse(currentPulse);
            stateManager.setPulse(playerId, newPulse);
            uiUpdater.updatePulseBlock(menu, playerId, newPulse);
            return "";
        }
    }
    
    // ==============================================
    // Antiseptic functionality: cleans the operation site and provides temperature protection
    // ==============================================
    private String handleAntiseptic(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(skillFailAntiseptic);
        } else {
            stateManager.setOperationSite(playerId, "Clean");
            uiUpdater.updateOperationSiteBlock(menu, playerId, "Clean");
            stateManager.setAntisepticProtection(playerId, true);
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("operation-clean"));
            return "";
        }
    }
    
    // ==============================================
    // Sponge functionality: removes bleeding and provides temporary protection against skill fails
    // ==============================================
    private String handleSponge(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(skillFailSponge);
        } else {
            stateManager.setBleeding(playerId, false);
            stateManager.setSpongeEffect(playerId, true);
            stateManager.setMovesSinceLastSponge(playerId, 0);
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("vision-cleared"));
            return "";
        }
    }
    
    // ==============================================
    // Anesthetic functionality: unconsciousness for the patient
    // ==============================================
    private String handleAnesthetic(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(skillFailAnesthetic);
        } else {
            Integer unconsciousTimer = stateManager.getUnconsciousTimer(playerId);
            String currentStatus = stateManager.getStatus(playerId);
            
            int reuseCooldown = plugin.getConfig().getInt("death-timers.anesthetic-reuse-cooldown", 4);
            if ((currentStatus.equals("Unconscious") || currentStatus.equals("Coming to")) &&
                unconsciousTimer != null && unconsciousTimer < reuseCooldown) {
                completionHandler.failSurgery(player, uiUpdater.getMessage("failure-anesthetic-misuse"));
                return "";
            }
            
            stateManager.setStatus(playerId, "Unconscious");
            uiUpdater.updateStatusBlock(menu, playerId, "Unconscious");
            stateManager.setUnconsciousTimer(playerId, 0);
            return "";
        }
    }
    
    // ==============================================
    // Defibrillator functionality: revives the patient from a stopped heart
    // ==============================================
    private String handleDefibrillator(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(skillFailDefibrillator);
        } else {
            String status = stateManager.getStatus(playerId);
            if (status.equals("Heart Stopped")) {
                stateManager.setStatus(playerId, "Unconscious");
                uiUpdater.updateStatusBlock(menu, playerId, "Unconscious");
                stateManager.removeDefibrillatorCountdown(playerId);
                menu.setItem(SurgeryConstants.SLOT_DEFIBRILLATOR, null);
            }
            return "";
        }
    }
    
    // ==============================================
    // Splint functionality: fixes broken bones
    // ==============================================
    private String handleSplint(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            stateManager.setBleeding(playerId, true);
            return getRandomSkillFail(skillFailSplint);
        } else {
            int actualBroken = stateManager.getBrokenBones(playerId);
            if (actualBroken > 0) {
                stateManager.setBrokenBones(playerId, actualBroken - 1);
                int revealedBroken = stateManager.getRevealedBrokenBones(playerId);
                if (revealedBroken > 0) {
                    stateManager.setRevealedBrokenBones(playerId, revealedBroken - 1);
                }
                uiUpdater.updateDiagnosisBlock(menu, playerId);
                if (actualBroken - 1 == 0) {
                    menu.setItem(SurgeryConstants.SLOT_SPLINT, null);
                }
            }
            return "";
        }
    }
    
    // ==============================================
    // Pins functionality: Fixes shattered bones
    // ==============================================
    private String handlePins(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            String diagnosis = stateManager.getDiagnosis(playerId);
            if (diagnosis != null && diagnosis.equals("Ecto-Bones")) {
                int shatteredBones = stateManager.getShatteredBones(playerId) + 1;
                stateManager.setShatteredBones(playerId, shatteredBones);
                int revealedShattered = stateManager.getRevealedShatteredBones(playerId) + 1;
                stateManager.setRevealedShatteredBones(playerId, revealedShattered);
                return uiUpdater.getMessage("skill-fail-pins-ecto-bones");
            } else {
                stateManager.setBleeding(playerId, true);
                return getRandomSkillFail(skillFailPins);
            }
        } else {
            int shatteredBones = stateManager.getShatteredBones(playerId);
            if (shatteredBones > 0) {
                int brokenBones = stateManager.getBrokenBones(playerId);
                stateManager.setBrokenBones(playerId, brokenBones + 1);
                stateManager.setShatteredBones(playerId, shatteredBones - 1);
                
                int revealedShattered = stateManager.getRevealedShatteredBones(playerId);
                int revealedBroken = stateManager.getRevealedBrokenBones(playerId);
                if (revealedShattered > 0) {
                    stateManager.setRevealedShatteredBones(playerId, revealedShattered - 1);
                }
                stateManager.setRevealedBrokenBones(playerId, revealedBroken + 1);
                
                uiUpdater.updateDiagnosisBlock(menu, playerId);
                mechanicsManager.updateDynamicTools(player, menu, playerId);
                
                if (shatteredBones - 1 == 0) {
                    menu.setItem(SurgeryConstants.SLOT_PINS, null);
                }
            }
            return "";
        }
    }
    
    // ==============================================
    // Clamp functionality: stops bleeding
    // ==============================================
    private String handleClamp(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(skillFailClamp);
        } else {
            if (stateManager.getIncisions(playerId) > 0 && stateManager.isBleeding(playerId)) {
                stateManager.setBleeding(playerId, false);
                mechanicsManager.updateDynamicTools(player, menu, playerId);
            }
            return "";
        }
    }
    
    // ==============================================
    // Surgical Glove functionality: "Fixes" the patient
    // ==============================================
    private String handleSurgicalGlove(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(skillFailSurgicalGlove);
        } else {
            stateManager.setCured(playerId, true);
            menu.setItem(SurgeryConstants.SLOT_SURGICAL_GLOVE, null);
            uiUpdater.updateDiagnosisBlock(menu, playerId);
            
            // Don't show incomplete message if surgery is already successful
            if (!completionHandler.isSurgerySuccessful(playerId)) {
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("condition-treated-incomplete"));
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("check-remaining"));
            }
            return "";
        }
    }
    
    // ==============================================
    // Gets a random skill fail message from a list
    // ==============================================
    private String getRandomSkillFail(java.util.List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return "Something went wrong!";
        }
        return messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
    }
    
    // ==============================================
    // Checks if skill fail should occur based on bleeding and sponge effect
    // ==============================================
    private boolean isSkillFail(UUID playerId) {
        double baseChance = plugin.getConfig().getDouble("skill-fail.base-chance", 0.25);
        
        if (stateManager.isBleeding(playerId)) {
            baseChance = plugin.getConfig().getDouble("skill-fail.bleeding-chance", 0.40);
        }
        
        if (stateManager.hasSpongeEffect(playerId)) {
            baseChance = plugin.getConfig().getDouble("skill-fail.with-sponge-chance", 0.10);
        }
        
        return ThreadLocalRandom.current().nextDouble() < baseChance;
    }
    
    // ==============================================
    // Removes item from player inventory
    // ==============================================
    private boolean removeItemFromPlayer(Player player, ItemStack targetItem) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.isSimilar(targetItem)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }
}
