package net.tfminecraft.surgery.managers;

import net.tfminecraft.surgery.procedures.Complication;
import net.tfminecraft.surgery.procedures.Procedure;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// ==============================================
// Handles complications and per-move effects
// ==============================================
public class SurgeryMechanicsManager {

    private final JavaPlugin plugin;
    private final ItemAPI api;
    private final SurgeryStateManager stateManager;
    private final SurgeryUIUpdater uiUpdater;
    private final SurgeryCompletionHandler completionHandler;
    private final SurgeryItemsConfig itemsConfig;

    public SurgeryMechanicsManager(JavaPlugin plugin, ItemAPI api, SurgeryStateManager stateManager,
                                   SurgeryUIUpdater uiUpdater, SurgeryCompletionHandler completionHandler,
                                   SurgeryItemsConfig itemsConfig) {
        this.plugin = plugin;
        this.api = api;
        this.stateManager = stateManager;
        this.uiUpdater = uiUpdater;
        this.completionHandler = completionHandler;
        this.itemsConfig = itemsConfig;
    }

    // ==============================================
    // Processes per-move effects (anesthetic wear-off, fever, blood loss, etc.)
    // ==============================================
    public void processMoveEffects(Player player, SurgeryTool clickedTool) {
        UUID playerId = player.getUniqueId();
        Inventory menu = player.getOpenInventory().getTopInventory();

        // Fail if the patient logged off or moved out of surgery range
        if (!isPatientPresent(player)) {
            completionHandler.failSurgery(player, uiUpdater.getMessage("failure-patient-left",
                "&cThe patient is no longer on the operating table!"));
            return;
        }

        // Increment move counter
        int moveCount = stateManager.getMoveCount(playerId) + 1;
        stateManager.setMoveCount(playerId, moveCount);

        // Increment moves since last sponge
        int movesSinceSponge = stateManager.getMovesSinceLastSponge(playerId) + 1;
        stateManager.setMovesSinceLastSponge(playerId, movesSinceSponge);

        // Advance the chloroform wear-off cycle: Unconscious -> Coming to -> Awake
        String currentStatus = stateManager.getStatus(playerId);
        if (currentStatus.equals("Unconscious") || currentStatus.equals("Coming to")) {
            Integer unconsciousTimer = stateManager.getUnconsciousTimer(playerId);
            if (unconsciousTimer != null) {
                int timer = unconsciousTimer + 1;
                stateManager.setUnconsciousTimer(playerId, timer);

                int unconsciousMoves = plugin.getConfig().getInt("anesthetic.unconscious-moves", 6);
                int comingToMoves = plugin.getConfig().getInt("anesthetic.coming-to-moves", 2);

                if (currentStatus.equals("Unconscious") && timer >= unconsciousMoves) {
                    stateManager.setStatus(playerId, "Coming to");
                    uiUpdater.updateStatusBlock(menu, playerId, "Coming to");
                    uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("patient-coming-to",
                        "&eThe chloroform is wearing off - the patient is coming to!"));
                } else if (currentStatus.equals("Coming to") && timer >= unconsciousMoves + comingToMoves) {
                    stateManager.setStatus(playerId, "Awake");
                    uiUpdater.updateStatusBlock(menu, playerId, "Awake");
                    uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("patient-woke-up",
                        "&cThe patient is fully awake! Give more chloroform before cutting!"));
                }
            }
        }

        // Check the collapse countdown (failure after configured moves)
        // Skip the decrement when the smelling salts were used, otherwise the
        // countdown expires before the patient is revived and the full window
        // promised by the config is never usable
        if (currentStatus.equals("Collapsed") && clickedTool != SurgeryTool.SMELLING_SALTS) {
            Integer countdown = stateManager.getCollapseCountdown(playerId);
            if (countdown != null) {
                countdown--;
                if (countdown <= 0) {
                    completionHandler.failSurgery(player, uiUpdater.getMessage("failure-not-resuscitated"));
                    return;
                }
                stateManager.setCollapseCountdown(playerId, countdown);
            }
        }

        // Check if temperature reached instant death threshold
        // >= because every temperature increase clamps to the threshold, so a
        // strict > could never fire and the mechanic was dead
        double currentTemp = stateManager.getTemperature(playerId);
        double instantDeathTemp = plugin.getConfig().getDouble("temperature.instant-death-threshold", 110.0);
        if (currentTemp >= instantDeathTemp) {
            completionHandler.failSurgery(player, uiUpdater.getMessage("failure-infection"));
            return;
        }

        // Check for consecutive red temperature. Fail after configured turns
        double redTempThreshold = plugin.getConfig().getDouble("temperature.red-temp-threshold", 106.0);
        int maxRedTempTurns = plugin.getConfig().getInt("death-timers.red-temp-turns", 2);
        if (currentTemp > redTempThreshold) {
            int redTempCounter = stateManager.getRedTempCounter(playerId) + 1;
            stateManager.setRedTempCounter(playerId, redTempCounter);
            if (redTempCounter > maxRedTempTurns) {
                completionHandler.failSurgery(player, uiUpdater.getMessage("failure-high-fever"));
                return;
            }
        } else {
            stateManager.setRedTempCounter(playerId, 0);
        }

        // Degrade pulse if bleeding
        double pulseDegradationChance = plugin.getConfig().getDouble("pulse.degradation-chance-bleeding", 0.30);
        if (stateManager.isBleeding(playerId) && ThreadLocalRandom.current().nextDouble() < pulseDegradationChance) {
            String currentPulse = stateManager.getPulse(playerId);
            if (currentPulse.equals("Extremely Weak")) {
                completionHandler.failSurgery(player, uiUpdater.getMessage("failure-bled-out"));
                return;
            }
            String newPulse = SurgeryConstants.worsenPulse(currentPulse);
            stateManager.setPulse(playerId, newPulse);
            uiUpdater.updatePulseBlock(menu, playerId, newPulse);
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("pulse-weakening"));
        }

        // Check for consecutive Extremely Weak pulse. Fail after configured turns
        String currentPulse = stateManager.getPulse(playerId);
        int maxWeakPulseTurns = plugin.getConfig().getInt("death-timers.weak-pulse-turns", 2);
        if (currentPulse.equals("Extremely Weak")) {
            int weakCounter = stateManager.getExtremelyWeakCounter(playerId) + 1;
            stateManager.setExtremelyWeakCounter(playerId, weakCounter);
            if (weakCounter > maxWeakPulseTurns) {
                completionHandler.failSurgery(player, uiUpdater.getMessage("failure-weak-pulse"));
                return;
            }
        } else {
            stateManager.setExtremelyWeakCounter(playerId, 0);
        }

        // Handle temperature rise
        String opSite = stateManager.getOperationSite(playerId);
        boolean hasProtection = stateManager.hasAntisepticProtection(playerId);
        boolean isBleeding = stateManager.isBleeding(playerId);
        int incisions = stateManager.getIncisions(playerId);
        boolean hasRisingTemp = stateManager.hasRisingTemp(playerId);

        boolean shouldRiseTemp = (!opSite.equals("Clean") && (incisions > 0 || isBleeding)) || hasRisingTemp;

        if (shouldRiseTemp && !hasProtection) {
            double riseRate = plugin.getConfig().getDouble("temperature.rise-rate", 1.8);
            double maxTemp = plugin.getConfig().getDouble("temperature.instant-death-threshold", 110.0);
            double temp = stateManager.getTemperature(playerId) + riseRate;
            temp = Math.min(temp, maxTemp);
            stateManager.setTemperature(playerId, temp);
            uiUpdater.updateTemperatureBlock(menu, playerId, temp);
        }

        // Disable carbolic acid protection if operation site becomes unclean
        if (!opSite.equals("Clean") && hasProtection) {
            stateManager.setAntisepticProtection(playerId, false);
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("protection-lost"));
        }

        // Complications only show once the surgeon knows what they are dealing with
        Procedure procedure = stateManager.getProcedure(playerId);
        if (procedure != null && stateManager.isExamined(playerId)) {
            runComplications(player, menu, playerId, procedure);
        }
    }

    // ==============================================
    // Checks the patient is still online, in the same world, and within range
    // Range gets a configurable multiplier so normal fidgeting doesn't end the surgery
    // ==============================================
    private boolean isPatientPresent(Player surgeon) {
        UUID patientUuid = stateManager.getPatientUuid(surgeon.getUniqueId());
        if (patientUuid == null) {
            return true;
        }
        Player patient = Bukkit.getPlayer(patientUuid);
        if (patient == null || !patient.isOnline()) {
            return false;
        }
        if (!patient.getWorld().equals(surgeon.getWorld())) {
            return false;
        }
        double maxDistance = plugin.getConfig().getDouble("max-surgery-distance", 5.0);
        double multiplier = plugin.getConfig().getDouble("patient-leave-distance-multiplier", 2.0);
        return surgeon.getLocation().distance(patient.getLocation()) <= maxDistance * multiplier;
    }

    // ==============================================
    // Runs the procedure's complications
    // ==============================================
    private void runComplications(Player player, Inventory menu, UUID playerId, Procedure procedure) {
        if (procedure.has(Complication.HAEMORRHAGE)) {
            // Forces bleeding somewhere between min and max moves since the last
            // sponge: 50/50 each move once min is reached, guaranteed at max
            int min = plugin.getConfig().getInt("complications.haemorrhage.bleeding-interval-min", 3);
            int max = plugin.getConfig().getInt("complications.haemorrhage.bleeding-interval-max", 4);
            int movesSinceSponge = stateManager.getMovesSinceLastSponge(playerId);
            if (movesSinceSponge >= max || (movesSinceSponge >= min && ThreadLocalRandom.current().nextBoolean())) {
                stateManager.setBleeding(playerId, true);
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("haemorrhage"));
                updateDynamicTools(player, menu, playerId);
            }
        }

        if (procedure.has(Complication.SHOCK)) {
            double chance = plugin.getConfig().getDouble("complications.shock.collapse-chance", 0.20);
            if (stateManager.getStatus(playerId).equals("Unconscious") && ThreadLocalRandom.current().nextDouble() < chance) {
                collapse(player, menu, playerId);
            }
        }
    }

    // ==============================================
    // The patient collapses: status, UI, message, death timer, tools
    // ==============================================
    private void collapse(Player player, Inventory menu, UUID playerId) {
        stateManager.setStatus(playerId, "Collapsed");
        uiUpdater.updateStatusBlock(menu, playerId, "Collapsed");
        uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("shock-collapse"));
        int countdown = plugin.getConfig().getInt("death-timers.collapse-countdown", 2);
        stateManager.setCollapseCountdown(playerId, countdown);
        updateDynamicTools(player, menu, playerId);
    }

    // ==============================================
    // Shows the linen dressing once the procedure's incisions are made and
    // every bone has been set
    // ==============================================
    public void checkForDressing(Player player, Inventory menu, UUID playerId, int currentIncisions) {
        Procedure procedure = stateManager.getProcedure(playerId);
        if (procedure == null || !stateManager.isExamined(playerId) || stateManager.isCured(playerId)) {
            return;
        }
        if (currentIncisions < procedure.requiredIncisions()) {
            return;
        }
        if (stateManager.getBrokenBones(playerId) > 0 || stateManager.getShatteredBones(playerId) > 0) {
            return;
        }
        if (menu.getItem(SurgeryTool.DRESSING.getSlot()) != null) {
            return;
        }
        ItemStack dressing = api.getCreator().getItemFromPath(itemsConfig.getItemPath(SurgeryTool.DRESSING));
        if (dressing != null) {
            menu.setItem(SurgeryTool.DRESSING.getSlot(), dressing);
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("dressing-ready"));
        }
    }

    // ==============================================
    // Reveals the procedure's bones once enough incisions expose them
    // ==============================================
    public void handleBoneReveal(Player player, Inventory menu, UUID playerId, int incisions) {
        Procedure procedure = stateManager.getProcedure(playerId);
        if (procedure == null || !procedure.hasBones() || !stateManager.isExamined(playerId)) {
            return;
        }
        // >= so overshooting the required count still reveals; re-running is
        // idempotent because revealed counts track the actual bone counts
        if (incisions >= procedure.requiredIncisions()) {
            int brokenBefore = stateManager.getRevealedBrokenBones(playerId);
            int shatteredBefore = stateManager.getRevealedShatteredBones(playerId);
            stateManager.setRevealedBrokenBones(playerId, stateManager.getBrokenBones(playerId));
            stateManager.setRevealedShatteredBones(playerId, stateManager.getShatteredBones(playerId));
            if (stateManager.getRevealedBrokenBones(playerId) > brokenBefore) {
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("discovered-broken-bone"));
            }
            if (stateManager.getRevealedShatteredBones(playerId) > shatteredBefore) {
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("discovered-shattered-bone"));
            }
            uiUpdater.updateDiagnosisBlock(menu, playerId);
            updateDynamicTools(player, menu, playerId);
        }
    }

    // ==============================================
    // Updates dynamic tools that appear based on conditions
    // ==============================================
    public void updateDynamicTools(Player player, Inventory menu, UUID playerId) {
        // Smelling salts: appear when the patient has collapsed
        boolean collapsed = stateManager.getStatus(playerId).equals("Collapsed");
        showTool(player, menu, SurgeryTool.SMELLING_SALTS, collapsed, "smelling-salts-available");

        // Silver wire: appears when shattered bones are revealed
        showTool(player, menu, SurgeryTool.SILVER_WIRE, stateManager.getRevealedShatteredBones(playerId) > 0,
            "silver-wire-available");

        // Splint: appears when broken bones are revealed
        showTool(player, menu, SurgeryTool.SPLINT, stateManager.getRevealedBrokenBones(playerId) > 0,
            "splint-available");

        // Artery forceps: appear when incisions > 1 AND bleeding
        boolean forceps = stateManager.getIncisions(playerId) > 1 && stateManager.isBleeding(playerId);
        showTool(player, menu, SurgeryTool.ARTERY_FORCEPS, forceps, "artery-forceps-available");
    }

    private void showTool(Player player, Inventory menu, SurgeryTool tool, boolean visible, String messageKey) {
        boolean shown = menu.getItem(tool.getSlot()) != null;
        if (visible && !shown) {
            ItemStack item = api.getCreator().getItemFromPath(itemsConfig.getItemPath(tool));
            if (item != null) {
                menu.setItem(tool.getSlot(), item);
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage(messageKey));
            }
        } else if (!visible && shown) {
            menu.setItem(tool.getSlot(), null);
        }
    }
}
