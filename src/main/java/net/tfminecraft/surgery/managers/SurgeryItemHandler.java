package net.tfminecraft.surgery.managers;

import net.tfminecraft.surgery.procedures.Complication;
import net.tfminecraft.surgery.procedures.Procedure;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private final SurgeryItemsConfig itemsConfig;

    // Skill fail message lists, one per tool
    private final Map<SurgeryTool, List<String>> skillFailMessages = new EnumMap<>(SurgeryTool.class);

    public SurgeryItemHandler(JavaPlugin plugin, ItemAPI api, SurgeryStateManager stateManager,
                              SurgeryUIUpdater uiUpdater, SurgeryMechanicsManager mechanicsManager,
                              SurgeryCompletionHandler completionHandler, SurgeryItemsConfig itemsConfig) {
        this.plugin = plugin;
        this.api = api;
        this.stateManager = stateManager;
        this.uiUpdater = uiUpdater;
        this.mechanicsManager = mechanicsManager;
        this.completionHandler = completionHandler;
        this.itemsConfig = itemsConfig;
    }

    // ==============================================
    // Initializes skill fail messages from messages.yml
    // ==============================================
    public void initialize() {
        for (SurgeryTool tool : SurgeryTool.values()) {
            skillFailMessages.put(tool, uiUpdater.getMessageList("skill-fail-" + tool.getConfigKey()));
        }
    }

    // ==============================================
    // Handles clicking on a surgery menu item
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    public void handleItemClick(Player player, ItemStack clickedItem, int slot) {
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        // Ignore clicks on info blocks and anything that is not a tool
        SurgeryTool tool = SurgeryTool.fromSlot(slot);
        if (tool == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // Special check for scalpel - cannot be used when patient is awake
        if (tool == SurgeryTool.SCALPEL && stateManager.getStatus(playerId).equals("Awake")) {
            stateManager.setOperated(playerId, true);
            completionHandler.failSurgery(player, uiUpdater.getMessage("failure-stabbed-awake"));
            return;
        }

        // Check if the player has this item in their inventory and remove it
        if (removeItemFromPlayer(player, tool)) {
            // Process per-move effects before updating menu
            mechanicsManager.processMoveEffects(player, tool);

            // Move effects may have ended the surgery (death timers, fever, bleed-out);
            // the session is cleaned up and the menu is closed, so stop here
            if (!stateManager.hasSession(playerId)) {
                return;
            }

            // Update the menu based on what was clicked
            updateMenu(player, tool);

            // Play correct sound only if no skill fail occurred
            String skillFailMsg = stateManager.getSkillFail(playerId);
            if (skillFailMsg.isEmpty()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        } else {
            // Player doesn't have the item - show error message
            player.sendMessage(uiUpdater.getMessage("item-not-in-inventory"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    // ==============================================
    // Updates the menu when specific items are clicked
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private void updateMenu(Player player, SurgeryTool tool) {
        Inventory menu = player.getOpenInventory().getTopInventory();
        UUID playerId = player.getUniqueId();
        boolean skillFail = isSkillFail(playerId);

        String skillFailMsg = switch (tool) {
            case SPONGE -> handleSponge(player, menu, playerId, skillFail);
            case SCALPEL -> handleScalpel(player, menu, playerId, skillFail);
            case SUTURE -> handleSuture(player, menu, playerId, skillFail);
            case TINCTURE -> handleTincture(player, menu, playerId, skillFail);
            case CARBOLIC_ACID -> handleCarbolicAcid(player, menu, playerId, skillFail);
            case DRESSING -> handleDressing(player, menu, playerId, skillFail);
            case STETHOSCOPE -> handleStethoscope(player, menu, playerId, skillFail);
            case THERMOMETER -> handleThermometer(menu, skillFail);
            case CHLOROFORM -> handleChloroform(player, menu, playerId, skillFail);
            case SMELLING_SALTS -> handleSmellingSalts(menu, playerId, skillFail);
            case SILVER_WIRE -> handleSilverWire(player, menu, playerId, skillFail);
            case SPLINT -> handleSplint(player, menu, playerId, skillFail);
            case ARTERY_FORCEPS -> handleArteryForceps(player, menu, playerId, skillFail);
            case TRANSFUSION -> handleTransfusion(menu, playerId, skillFail);
        };

        // Failing to use chloroform correctly can end the surgery outright
        if (!stateManager.hasSession(playerId)) {
            return;
        }

        if (stateManager.isBleeding(playerId)) {
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("bleeding-warning"));
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

        // Check if surgery is complete after this move (if the ailment was already treated)
        if (stateManager.isCured(playerId) && completionHandler.isSurgerySuccessful(playerId)) {
            completionHandler.handleSuccess(player);
        }
    }

    // ==============================================
    // Clinical thermometer: reveals the fever tincture
    // ==============================================
    private String handleThermometer(Inventory menu, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(SurgeryTool.THERMOMETER);
        }
        menu.setItem(SurgeryTool.THERMOMETER.getSlot(), null);
        ItemStack tincture = api.getCreator().getItemFromPath(itemsConfig.getItemPath(SurgeryTool.TINCTURE));
        if (tincture != null) {
            menu.setItem(SurgeryTool.TINCTURE.getSlot(), tincture);
        }
        return "";
    }

    // ==============================================
    // Stethoscope: examines the patient, revealing the procedure and its bones
    // ==============================================
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private String handleStethoscope(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(SurgeryTool.STETHOSCOPE);
        }
        menu.setItem(SurgeryTool.STETHOSCOPE.getSlot(), null);
        stateManager.setExamined(playerId, true);
        Procedure procedure = stateManager.getProcedure(playerId);

        // Sepsis: the patient is already feverish and the fever keeps climbing
        if (procedure.has(Complication.SEPSIS)) {
            double feverMin = plugin.getConfig().getDouble("complications.sepsis.fever-min", 100.0);
            double feverMax = plugin.getConfig().getDouble("complications.sepsis.fever-max", 104.0);
            double fever = Math.max(stateManager.getTemperature(playerId),
                feverMin + ThreadLocalRandom.current().nextDouble() * (feverMax - feverMin));
            stateManager.setHasRisingTemp(playerId, true);
            stateManager.setTemperature(playerId, fever);
            uiUpdater.updateTemperatureBlock(menu, playerId, fever);
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("sepsis-found"));
        }

        stateManager.setBrokenBones(playerId, procedure.brokenBones());
        stateManager.setShatteredBones(playerId, procedure.shatteredBones());
        uiUpdater.updateDiagnosisBlock(menu, playerId);
        uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("examined")
            .replace("%procedure%", procedure.name())
            .replace("%incisions%", String.valueOf(procedure.requiredIncisions())));

        // Incisions made before the examination still count
        int incisions = stateManager.getIncisions(playerId);
        mechanicsManager.handleBoneReveal(player, menu, playerId, incisions);
        mechanicsManager.checkForDressing(player, menu, playerId, incisions);
        return "";
    }

    // ==============================================
    // Scalpel: creates incisions and affects pulse
    // ==============================================
    private String handleScalpel(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            // On skill fail: lower pulse instead of creating incision
            worsenPulse(player, menu, playerId);
            return getRandomSkillFail(SurgeryTool.SCALPEL);
        }
        // Once the patient has been sedated or cut, walking away counts as a failed surgery
        stateManager.setOperated(playerId, true);
        int incisions = stateManager.getIncisions(playerId) + 1;
        stateManager.setIncisions(playerId, incisions);
        uiUpdater.updateIncisionBlock(menu, playerId, incisions);

        // Cutting makes a clean operation site unclean
        if (stateManager.getOperationSite(playerId).equals("Clean")) {
            stateManager.setOperationSite(playerId, "Unclean");
            uiUpdater.updateOperationSiteBlock(menu, playerId, "Unclean");
        }

        // Reveal bones at the procedure's incision count
        mechanicsManager.handleBoneReveal(player, menu, playerId, incisions);

        // Always refresh dynamic tools; the forceps depend on incision count and
        // bleeding, which can both be true before the examination
        mechanicsManager.updateDynamicTools(player, menu, playerId);

        // Chance for pulse to decrease when making incision
        if (ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("pulse.scalpel-decrease-chance", 0.50)) {
            worsenPulse(player, menu, playerId);
        }

        mechanicsManager.checkForDressing(player, menu, playerId, incisions);
        return "";
    }

    private void worsenPulse(Player player, Inventory menu, UUID playerId) {
        String newPulse = SurgeryConstants.worsenPulse(stateManager.getPulse(playerId));
        stateManager.setPulse(playerId, newPulse);
        uiUpdater.updatePulseBlock(menu, playerId, newPulse);
        uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("pulse-weakening"));
    }

    // ==============================================
    // Catgut suture: closes incisions and can stop bleeding
    // ==============================================
    private String handleSuture(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(SurgeryTool.SUTURE);
        }
        int incisions = Math.max(0, stateManager.getIncisions(playerId) - 1);
        stateManager.setIncisions(playerId, incisions);
        uiUpdater.updateIncisionBlock(menu, playerId, incisions);
        if (incisions == 0) {
            stateManager.setBleeding(playerId, false);
        }
        mechanicsManager.updateDynamicTools(player, menu, playerId);
        return "";
    }

    // ==============================================
    // Willow-bark tincture: brings the fever down
    // ==============================================
    private String handleTincture(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        double change = plugin.getConfig().getDouble("temperature.tincture-change", 5.4);
        if (skillFail) {
            double temp = stateManager.getTemperature(playerId) + change;
            temp = Math.min(temp, plugin.getConfig().getDouble("temperature.instant-death-threshold", 110.0));
            stateManager.setTemperature(playerId, temp);
            uiUpdater.updateTemperatureBlock(menu, playerId, temp);
            return getRandomSkillFail(SurgeryTool.TINCTURE);
        }
        double temp = stateManager.getTemperature(playerId) - change;
        temp = Math.max(temp, plugin.getConfig().getDouble("temperature.normal", 98.6));
        stateManager.setTemperature(playerId, temp);
        uiUpdater.updateTemperatureBlock(menu, playerId, temp);
        uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("temperature-reduced"));
        return "";
    }

    // ==============================================
    // Transfusion syringe: improves pulse
    // ==============================================
    private String handleTransfusion(Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            stateManager.setOperationSite(playerId, "Unsanitary");
            uiUpdater.updateOperationSiteBlock(menu, playerId, "Unsanitary");
            return getRandomSkillFail(SurgeryTool.TRANSFUSION);
        }
        String newPulse = SurgeryConstants.improvePulse(stateManager.getPulse(playerId));
        stateManager.setPulse(playerId, newPulse);
        uiUpdater.updatePulseBlock(menu, playerId, newPulse);
        return "";
    }

    // ==============================================
    // Carbolic acid: cleans the operation site and protects against fever
    // ==============================================
    private String handleCarbolicAcid(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(SurgeryTool.CARBOLIC_ACID);
        }
        stateManager.setOperationSite(playerId, "Clean");
        uiUpdater.updateOperationSiteBlock(menu, playerId, "Clean");
        stateManager.setAntisepticProtection(playerId, true);
        uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("operation-clean"));
        return "";
    }

    // ==============================================
    // Sponge: removes bleeding and reduces the next move's skill fail chance
    // ==============================================
    private String handleSponge(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(SurgeryTool.SPONGE);
        }
        stateManager.setBleeding(playerId, false);
        stateManager.setSpongeEffect(playerId, true);
        stateManager.setMovesSinceLastSponge(playerId, 0);
        mechanicsManager.updateDynamicTools(player, menu, playerId);
        uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("vision-cleared"));
        return "";
    }

    // ==============================================
    // Chloroform: puts the patient under
    // ==============================================
    private String handleChloroform(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(SurgeryTool.CHLOROFORM);
        }
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
        stateManager.setOperated(playerId, true);
        return "";
    }

    // ==============================================
    // Smelling salts: revive a collapsed patient
    // ==============================================
    private String handleSmellingSalts(Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(SurgeryTool.SMELLING_SALTS);
        }
        if (stateManager.getStatus(playerId).equals("Collapsed")) {
            stateManager.setStatus(playerId, "Unconscious");
            uiUpdater.updateStatusBlock(menu, playerId, "Unconscious");
            stateManager.removeCollapseCountdown(playerId);
            menu.setItem(SurgeryTool.SMELLING_SALTS.getSlot(), null);
        }
        return "";
    }

    // ==============================================
    // Splint: sets broken bones
    // ==============================================
    private String handleSplint(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            stateManager.setBleeding(playerId, true);
            mechanicsManager.updateDynamicTools(player, menu, playerId);
            return getRandomSkillFail(SurgeryTool.SPLINT);
        }
        int actualBroken = stateManager.getBrokenBones(playerId);
        if (actualBroken > 0) {
            stateManager.setBrokenBones(playerId, actualBroken - 1);
            int revealedBroken = stateManager.getRevealedBrokenBones(playerId);
            if (revealedBroken > 0) {
                stateManager.setRevealedBrokenBones(playerId, revealedBroken - 1);
            }
            uiUpdater.updateDiagnosisBlock(menu, playerId);
            mechanicsManager.updateDynamicTools(player, menu, playerId);
            mechanicsManager.checkForDressing(player, menu, playerId, stateManager.getIncisions(playerId));
        }
        return "";
    }

    // ==============================================
    // Silver wire: binds shattered bones into broken bones that can be splinted
    // ==============================================
    private String handleSilverWire(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            stateManager.setBleeding(playerId, true);
            mechanicsManager.updateDynamicTools(player, menu, playerId);
            return getRandomSkillFail(SurgeryTool.SILVER_WIRE);
        }
        int shatteredBones = stateManager.getShatteredBones(playerId);
        if (shatteredBones > 0) {
            stateManager.setBrokenBones(playerId, stateManager.getBrokenBones(playerId) + 1);
            stateManager.setShatteredBones(playerId, shatteredBones - 1);

            int revealedShattered = stateManager.getRevealedShatteredBones(playerId);
            if (revealedShattered > 0) {
                stateManager.setRevealedShatteredBones(playerId, revealedShattered - 1);
            }
            stateManager.setRevealedBrokenBones(playerId, stateManager.getRevealedBrokenBones(playerId) + 1);

            uiUpdater.updateDiagnosisBlock(menu, playerId);
            mechanicsManager.updateDynamicTools(player, menu, playerId);
        }
        return "";
    }

    // ==============================================
    // Artery forceps: stop bleeding from an open wound
    // ==============================================
    private String handleArteryForceps(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(SurgeryTool.ARTERY_FORCEPS);
        }
        if (stateManager.getIncisions(playerId) > 0 && stateManager.isBleeding(playerId)) {
            stateManager.setBleeding(playerId, false);
            mechanicsManager.updateDynamicTools(player, menu, playerId);
        }
        return "";
    }

    // ==============================================
    // Linen dressing: treats the ailment once the procedure is done
    // ==============================================
    private String handleDressing(Player player, Inventory menu, UUID playerId, boolean skillFail) {
        if (skillFail) {
            return getRandomSkillFail(SurgeryTool.DRESSING);
        }
        stateManager.setCured(playerId, true);
        menu.setItem(SurgeryTool.DRESSING.getSlot(), null);
        uiUpdater.updateDiagnosisBlock(menu, playerId);

        // Don't show incomplete message if surgery is already successful
        if (!completionHandler.isSurgerySuccessful(playerId)) {
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("condition-treated-incomplete"));
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("check-remaining"));
        }
        return "";
    }

    // ==============================================
    // Gets a random skill fail message for a tool
    // ==============================================
    private String getRandomSkillFail(SurgeryTool tool) {
        List<String> messages = skillFailMessages.get(tool);
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
    // Removes one of the tool from the player's inventory
    // Matches by item path rather than exact meta, so tools crafted before a
    // rename or lore change still count
    // ==============================================
    private boolean removeItemFromPlayer(Player player, SurgeryTool tool) {
        String path = itemsConfig.getItemPath(tool);
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && api.getChecker().checkItemWithPath(item, path)) {
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
