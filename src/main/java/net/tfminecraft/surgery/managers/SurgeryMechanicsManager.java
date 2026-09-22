package net.tfminecraft.surgery.managers;

import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// ==============================================
// Handles diagnosis-specific mechanics and per-move effects
// ==============================================
public class SurgeryMechanicsManager {
    
    private final JavaPlugin plugin;
    private final ItemAPI api;
    private final SurgeryStateManager stateManager;
    private final SurgeryUIUpdater uiUpdater;
    private final SurgeryCompletionHandler completionHandler;
    private final DiagnosisChecker diagnosisChecker;
    private final SurgeryItemsConfig itemsConfig;

    // Map diagnoses to required incision counts
    private final Map<String, Integer> requiredIncisions = new HashMap<>();
    
    public SurgeryMechanicsManager(JavaPlugin plugin, ItemAPI api, SurgeryStateManager stateManager,
                                   SurgeryUIUpdater uiUpdater, SurgeryCompletionHandler completionHandler,
                                   DiagnosisChecker diagnosisChecker, SurgeryItemsConfig itemsConfig) {
        this.plugin = plugin;
        this.api = api;
        this.stateManager = stateManager;
        this.uiUpdater = uiUpdater;
        this.completionHandler = completionHandler;
        this.diagnosisChecker = diagnosisChecker;
        this.itemsConfig = itemsConfig;
    }
    
    // ==============================================
    // Initializes required incisions from config
    // ==============================================
    public void initialize() {
        var config = plugin.getConfig().getConfigurationSection("required-incisions");
        if (config != null) {
            for (String key : config.getKeys(false)) {
                requiredIncisions.put(key, config.getInt(key));
            }
        }
    }
    
    // ==============================================
    // Processes per-move effects (antibiotics countdown, temperature changes, etc.)
    // ==============================================
    public void processMoveEffects(Player player, int clickedSlot) {
        UUID playerId = player.getUniqueId();
        Inventory menu = player.getOpenInventory().getTopInventory();
        String diagnosis = stateManager.getDiagnosis(playerId);

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

        // Advance the anesthetic wear-off cycle: Unconscious -> Coming to -> Awake
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
                        "&eThe anesthetic is wearing off - the patient is coming to!"));
                } else if (currentStatus.equals("Coming to") && timer >= unconsciousMoves + comingToMoves) {
                    stateManager.setStatus(playerId, "Awake");
                    uiUpdater.updateStatusBlock(menu, playerId, "Awake");
                    uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("patient-woke-up",
                        "&cThe patient is fully awake! Re-administer anesthetic before cutting!"));
                }
            }
        }
        
        // Check defibrillator countdown (failure after configured moves)
        // Skip the decrement when the defibrillator itself was clicked, otherwise
        // the countdown expires before the shock is applied and the full window
        // promised by the config is never usable
        if (currentStatus.equals("Heart Stopped") && clickedSlot != SurgeryConstants.SLOT_DEFIBRILLATOR) {
            Integer countdown = stateManager.getDefibrillatorCountdown(playerId);
            if (countdown != null) {
                countdown--;
                if (countdown <= 0) {
                    completionHandler.failSurgery(player, uiUpdater.getMessage("failure-not-resuscitated"));
                    return;
                }
                stateManager.setDefibrillatorCountdown(playerId, countdown);
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
        
        // Disable antiseptic protection if operation site becomes unclean
        if (!opSite.equals("Clean") && hasProtection) {
            stateManager.setAntisepticProtection(playerId, false);
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("protection-lost"));
        }
        
        if (diagnosis != null) {
            runDiagnosisSpecificMechanics(player, menu, playerId, diagnosis);
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
    // Runs diagnosis-specific mechanics
    // ==============================================
    private void runDiagnosisSpecificMechanics(Player player, Inventory menu, UUID playerId, String diagnosis) {
        switch (diagnosis) {
            case "Moldy Guts":
                // Forces bleeding somewhere between min and max moves since the last
                // sponge: 50/50 each move once min is reached, guaranteed at max
                int moldyGutsMin = plugin.getConfig().getInt("diagnosis-mechanics.moldy-guts.bleeding-interval-min", 3);
                int moldyGutsMax = plugin.getConfig().getInt("diagnosis-mechanics.moldy-guts.bleeding-interval-max", 4);
                int movesSinceSpongeNow = stateManager.getMovesSinceLastSponge(playerId);
                if (movesSinceSpongeNow >= moldyGutsMax
                        || (movesSinceSpongeNow >= moldyGutsMin && ThreadLocalRandom.current().nextBoolean())) {
                    stateManager.setBleeding(playerId, true);
                    uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("moldy-guts"));
                    updateDynamicTools(player, menu, playerId);
                }
                break;
                
            case "Fatty Liver":
                double fattyLiverChance = plugin.getConfig().getDouble("diagnosis-mechanics.fatty-liver.heart-stop-chance", 0.20);
                if (stateManager.getStatus(playerId).equals("Unconscious") && ThreadLocalRandom.current().nextDouble() < fattyLiverChance) {
                    stopHeart(player, menu, playerId, "fatty-liver-heart-stop");
                }
                break;

            case "Broken Heart":
                double brokenHeartChance = plugin.getConfig().getDouble("diagnosis-mechanics.broken-heart.heart-stop-chance", 0.35);
                if (stateManager.getStatus(playerId).equals("Unconscious") && ThreadLocalRandom.current().nextDouble() < brokenHeartChance) {
                    stopHeart(player, menu, playerId, "broken-heart-stop");
                }
                break;
                
            case "Arcane Infection":
                double arcaneChance = plugin.getConfig().getDouble("diagnosis-mechanics.arcane-infection.chaos-chance", 0.25);
                if (ThreadLocalRandom.current().nextDouble() < arcaneChance) {
                    handleArcaneInfectionChaos(player, menu, playerId);
                }
                break;
                
            case "Lupus":
                double lupusChance = plugin.getConfig().getDouble("diagnosis-mechanics.lupus.howl-chance", 0.15);
                if (ThreadLocalRandom.current().nextDouble() < lupusChance) {
                    int currentIncisions = stateManager.getIncisions(playerId);
                    stateManager.setIncisions(playerId, currentIncisions + 1);
                    uiUpdater.updateIncisionBlock(menu, playerId, currentIncisions + 1);
                    stateManager.setBleeding(playerId, true);
                    uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("lupus-howl"));
                    updateDynamicTools(player, menu, playerId);
                }
                break;
        }
    }
    
    // ==============================================
    // Stops the patient's heart: status, UI, message, death timer, tools
    // ==============================================
    private void stopHeart(Player player, Inventory menu, UUID playerId, String messageKey) {
        stateManager.setStatus(playerId, "Heart Stopped");
        uiUpdater.updateStatusBlock(menu, playerId, "Heart Stopped");
        uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage(messageKey));
        int defibCountdown = plugin.getConfig().getInt("death-timers.defibrillator-countdown", 2);
        stateManager.setDefibrillatorCountdown(playerId, defibCountdown);
        updateDynamicTools(player, menu, playerId);
    }

    // ==============================================
    // Handles Arcane Infection chaos effects
    // ==============================================
    private void handleArcaneInfectionChaos(Player player, Inventory menu, UUID playerId) {
        int chaosEffect = ThreadLocalRandom.current().nextInt(4);
        double temp;
        
        switch (chaosEffect) {
            case 0: // Temperature spike
                double tempSpikeMax = plugin.getConfig().getDouble("diagnosis-mechanics.arcane-infection.temp-spike-max", 4.0);
                double maxTemp = plugin.getConfig().getDouble("temperature.instant-death-threshold", 110.0);
                temp = Math.min(stateManager.getTemperature(playerId) + ThreadLocalRandom.current().nextDouble() * tempSpikeMax, maxTemp);
                stateManager.setTemperature(playerId, temp);
                uiUpdater.updateTemperatureBlock(menu, playerId, temp);
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("chaos-temp-spike"));
                break;

            case 1: // Temperature drop
                double tempDropMax = plugin.getConfig().getDouble("diagnosis-mechanics.arcane-infection.temp-drop-max", 2.0);
                double normalTemp = plugin.getConfig().getDouble("temperature.normal", 98.6);
                temp = Math.max(stateManager.getTemperature(playerId) - ThreadLocalRandom.current().nextDouble() * tempDropMax, normalTemp);
                stateManager.setTemperature(playerId, temp);
                uiUpdater.updateTemperatureBlock(menu, playerId, temp);
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("chaos-temp-drop"));
                break;
                
            case 2: // Heart stop
                stopHeart(player, menu, playerId, "chaos-heart-stop");
                break;
                
            case 3: // Random status change
                String newStatus = SurgeryConstants.PATIENT_STATUSES[ThreadLocalRandom.current().nextInt(SurgeryConstants.PATIENT_STATUSES.length)];
                stateManager.setStatus(playerId, newStatus);
                uiUpdater.updateStatusBlock(menu, playerId, newStatus);
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("chaos-status-change"));
                // A random "Heart Stopped" needs the death timer like every other
                // heart-stop path; without it the countdown stays null and the
                // patient can never die from the stopped heart
                if (newStatus.equals("Heart Stopped")) {
                    int countdown = plugin.getConfig().getInt("death-timers.defibrillator-countdown", 2);
                    stateManager.setDefibrillatorCountdown(playerId, countdown);
                } else {
                    // Leaving "Heart Stopped" by chaos skips the defibrillator, so the
                    // old countdown must not survive into a later heart stop
                    stateManager.removeDefibrillatorCountdown(playerId);
                }
                // Align the anesthetic wear-off timer with the forced status, otherwise
                // a stale timer makes the patient wake instantly or far too late
                if (newStatus.equals("Unconscious")) {
                    stateManager.setUnconsciousTimer(playerId, 0);
                } else if (newStatus.equals("Coming to")) {
                    stateManager.setUnconsciousTimer(playerId, plugin.getConfig().getInt("anesthetic.unconscious-moves", 6));
                }
                updateDynamicTools(player, menu, playerId);
                break;
        }
    }
    
    // ==============================================
    // Check if Fix it button should appear based on diagnosis and conditions
    // ==============================================
    public void checkForFixItButton(Player player, Inventory menu, UUID playerId, int currentIncisions) {
        String diagnosis = stateManager.getDiagnosis(playerId);
        if (diagnosis == null || stateManager.isCured(playerId)) {
            return;
        }
        
        // Check for flu diagnoses. Show surgical glove at normal temperature
        if (diagnosisChecker.isFlu(diagnosis)) {
            double currentTemp = stateManager.getTemperature(playerId);
            double normalTemp = plugin.getConfig().getDouble("temperature.normal", 98.6);
            if (Math.abs(currentTemp - normalTemp) < 0.1) {
                ItemStack surgicalGlove = api.getCreator().getItemFromPath(itemsConfig.getItemPath(5));
                if (surgicalGlove != null) {
                    menu.setItem(SurgeryConstants.SLOT_SURGICAL_GLOVE, surgicalGlove);
                    uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("surgical-glove-ready"));
                }
            }
            return;
        }
        
        // For non-flu diagnoses, check incision count
        if (requiredIncisions.containsKey(diagnosis)) {
            int requiredInc = requiredIncisions.get(diagnosis);
            if (currentIncisions >= requiredInc) {
                // If diagnosis has bones, check that all bones are fixed first
                if (diagnosisChecker.hasBones(diagnosis)) {
                    int brokenBones = stateManager.getBrokenBones(playerId);
                    int shatteredBones = stateManager.getShatteredBones(playerId);
                    if (brokenBones > 0 || shatteredBones > 0) {
                        return;
                    }
                }
                
                ItemStack surgicalGlove = api.getCreator().getItemFromPath(itemsConfig.getItemPath(5));
                if (surgicalGlove != null) {
                    menu.setItem(SurgeryConstants.SLOT_SURGICAL_GLOVE, surgicalGlove);
                    uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("surgical-glove-ready"));
                }
            }
        }
    }

    // ==============================================
    // Handles bone reveal logic for scalpel
    // ==============================================
    public void handleBoneReveal(Player player, Inventory menu, UUID playerId, String diagnosis, int incisions) {
        if (diagnosisChecker.hasBones(diagnosis)) {
            // >= so overshooting the required count still reveals; re-running is
            // idempotent because revealed counts track the actual bone counts
            int required = requiredIncisions.getOrDefault(diagnosis, 0);
            if (incisions >= required) {
                int totalBrokenBones = stateManager.getBrokenBones(playerId);
                int totalShatteredBones = stateManager.getShatteredBones(playerId);
                stateManager.setRevealedBrokenBones(playerId, totalBrokenBones);
                stateManager.setRevealedShatteredBones(playerId, totalShatteredBones);
                updateDynamicTools(player, menu, playerId);
            }
        } else {
            revealBonesRandomly(player, menu, playerId);
        }
    }
    
    // ==============================================
    // Randomly reveal bones when scalpel is used (25% chance per incision)
    // ==============================================
    private void revealBonesRandomly(Player player, Inventory menu, UUID playerId) {
        int totalBroken = stateManager.getBrokenBones(playerId);
        int totalShattered = stateManager.getShatteredBones(playerId);
        int revealedBroken = stateManager.getRevealedBrokenBones(playerId);
        int revealedShattered = stateManager.getRevealedShatteredBones(playerId);
        
        double revealChance = plugin.getConfig().getDouble("bones.random-reveal-chance", 0.25);
        
        if (revealedBroken < totalBroken && ThreadLocalRandom.current().nextDouble() < revealChance) {
            stateManager.setRevealedBrokenBones(playerId, revealedBroken + 1);
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("discovered-broken-bone"));
        }
        
        if (revealedShattered < totalShattered && ThreadLocalRandom.current().nextDouble() < revealChance) {
            stateManager.setRevealedShatteredBones(playerId, revealedShattered + 1);
            uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("discovered-shattered-bone"));
        }
        
        updateDynamicTools(player, menu, playerId);
    }
    
    // ==============================================
    // Updates dynamic tools that appear based on conditions
    // ==============================================
    public void updateDynamicTools(Player player, Inventory menu, UUID playerId) {
        // Defibrillator: appears when heart stopped
        String status = stateManager.getStatus(playerId);
        if (status.equals("Heart Stopped") && menu.getItem(SurgeryConstants.SLOT_DEFIBRILLATOR) == null) {
            ItemStack defibrillator = api.getCreator().getItemFromPath(itemsConfig.getItemPath(9));
            if (defibrillator != null) {
                menu.setItem(SurgeryConstants.SLOT_DEFIBRILLATOR, defibrillator);
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("defibrillator-available"));
            }
        } else if (!status.equals("Heart Stopped") && menu.getItem(SurgeryConstants.SLOT_DEFIBRILLATOR) != null) {
            menu.setItem(SurgeryConstants.SLOT_DEFIBRILLATOR, null);
        }

        // Pins: appears when shattered bones revealed
        int revealedShattered = stateManager.getRevealedShatteredBones(playerId);
        if (revealedShattered > 0 && menu.getItem(SurgeryConstants.SLOT_PINS) == null) {
            ItemStack pins = api.getCreator().getItemFromPath(itemsConfig.getItemPath(10));
            if (pins != null) {
                menu.setItem(SurgeryConstants.SLOT_PINS, pins);
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("pins-available"));
            }
        } else if (revealedShattered == 0 && menu.getItem(SurgeryConstants.SLOT_PINS) != null) {
            menu.setItem(SurgeryConstants.SLOT_PINS, null);
        }

        // Splint: appears when broken bones revealed
        int revealedBroken = stateManager.getRevealedBrokenBones(playerId);
        if (revealedBroken > 0 && menu.getItem(SurgeryConstants.SLOT_SPLINT) == null) {
            ItemStack splint = api.getCreator().getItemFromPath(itemsConfig.getItemPath(11));
            if (splint != null) {
                menu.setItem(SurgeryConstants.SLOT_SPLINT, splint);
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("splint-available"));
            }
        } else if (revealedBroken == 0 && menu.getItem(SurgeryConstants.SLOT_SPLINT) != null) {
            menu.setItem(SurgeryConstants.SLOT_SPLINT, null);
        }

        // Clamp: appears when incisions > 1 AND bleeding
        int incisions = stateManager.getIncisions(playerId);
        boolean bleeding = stateManager.isBleeding(playerId);
        if (incisions > 1 && bleeding && menu.getItem(SurgeryConstants.SLOT_CLAMP) == null) {
            ItemStack clamp = api.getCreator().getItemFromPath(itemsConfig.getItemPath(12));
            if (clamp != null) {
                menu.setItem(SurgeryConstants.SLOT_CLAMP, clamp);
                uiUpdater.sendNumberedMessage(player, uiUpdater.getMessage("clamp-available"));
            }
        } else if ((incisions <= 1 || !bleeding) && menu.getItem(SurgeryConstants.SLOT_CLAMP) != null) {
            menu.setItem(SurgeryConstants.SLOT_CLAMP, null);
        }
    }
}
