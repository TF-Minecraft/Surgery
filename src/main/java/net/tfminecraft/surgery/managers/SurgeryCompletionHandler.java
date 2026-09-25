package net.tfminecraft.surgery.managers;

import net.tfminecraft.surgery.procedures.Ailments;
import net.tfminecraft.surgery.procedures.Durations;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

// ==============================================
// Handles surgery completion (success and failure)
// Success cures the patient's healing ailment in RPCharacters; failure makes
// it take longer to heal
// ==============================================
public class SurgeryCompletionHandler {

    private static final long DEFAULT_FAILURE_PENALTY_MS = 12 * 3_600_000L;

    private final JavaPlugin plugin;
    private final SurgeryStateManager stateManager;
    private final SurgeryUIUpdater uiUpdater;

    public SurgeryCompletionHandler(JavaPlugin plugin, SurgeryStateManager stateManager, SurgeryUIUpdater uiUpdater) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.uiUpdater = uiUpdater;
    }

    // ==============================================
    // Checks if all success conditions are met
    // ==============================================
    public boolean isSurgerySuccessful(UUID playerId) {
        // 1. The ailment has been treated and dressed
        if (!stateManager.isCured(playerId)) { return false; }

        // 2. Pulse must be "Strong" (LIME)
        if (!stateManager.getPulse(playerId).equals("Strong")) { return false; }

        // 3. Status must be "Unconscious" (LIME)
        if (!stateManager.getStatus(playerId).equals("Unconscious")) { return false; }

        // 4. Temperature must be at or below the configured success threshold (LIME)
        double successThreshold = plugin.getConfig().getDouble("temperature.success-threshold", 100.0);
        if (stateManager.getTemperature(playerId) > successThreshold) { return false; }

        // 5. Operation site must be "Clean" (LIME)
        if (!stateManager.getOperationSite(playerId).equals("Clean")) { return false; }

        // 6. Incisions must be 0 (LIME)
        if (stateManager.getIncisions(playerId) != 0) { return false; }

        // 7. All bones must be set
        if (stateManager.getBrokenBones(playerId) != 0 || stateManager.getShatteredBones(playerId) != 0) { return false; }

        // 8. No bleeding
        if (stateManager.isBleeding(playerId)) { return false; }

        return true;
    }

    // ==============================================
    // Handles successful surgery completion
    // ==============================================
    public void handleSuccess(Player surgeon) {
        UUID surgeonId = surgeon.getUniqueId();
        String traitId = stateManager.getTraitId(surgeonId);
        String ailment = stateManager.getAilmentName(surgeonId);
        Player patient = patientOf(surgeonId);

        // The ailment may have healed on its own while the operation was running.
        // Do not announce a success the cure did not actually perform.
        if (patient == null || !Ailments.cure(patient, traitId)) {
            stateManager.cleanup(surgeonId);
            closeMenuNextTick(surgeon);
            surgeon.sendMessage(uiUpdater.getMessage("ailment-already-healed").replace("%ailment%", ailment));
            return;
        }

        executeCompletionCommand(surgeon, true);
        stateManager.cleanup(surgeonId);
        closeMenuNextTick(surgeon);

        surgeon.playSound(surgeon.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        surgeon.sendMessage(uiUpdater.getMessage("surgery-successful"));
        surgeon.sendMessage(uiUpdater.getMessage("surgery-successful-subtitle")
            .replace("%ailment%", ailment).replace("%patient%", patient.getName()));
        patient.sendMessage(uiUpdater.getMessage("patient-surgery-successful")
            .replace("%ailment%", ailment).replace("%surgeon%", surgeon.getName()));
    }

    // ==============================================
    // Fails the surgery with a message
    // ==============================================
    public void failSurgery(Player surgeon, String message) {
        UUID surgeonId = surgeon.getUniqueId();

        // Check if player is actually in surgery (prevent duplicate messages)
        if (!stateManager.hasSession(surgeonId)) { return; }

        executeCompletionCommand(surgeon, false);
        // Only a patient who was actually sedated or cut is left worse off
        String extra = stateManager.hasOperated(surgeonId) ? lengthenHealing(surgeon) : null;

        stateManager.cleanup(surgeonId);

        closeMenuNextTick(surgeon);
        surgeon.playSound(surgeon.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f);
        surgeon.sendMessage(uiUpdater.getMessage("surgery-failed"));
        surgeon.sendMessage(message);
        if (extra != null) {
            surgeon.sendMessage(uiUpdater.getMessage("surgery-failed-penalty").replace("%extra%", extra));
        }
    }

    // ==============================================
    // Adds the failure penalty to the patient's healing time
    // Returns the added time for messages, or null if nothing was added
    // ==============================================
    private String lengthenHealing(Player surgeon) {
        UUID surgeonId = surgeon.getUniqueId();
        Player patient = patientOf(surgeonId);
        String traitId = stateManager.getTraitId(surgeonId);
        if (patient == null || traitId == null) {
            return null;
        }
        long penalty = Durations.parseMs(plugin.getConfig().getString("failure-healing-penalty", "12h"));
        if (penalty < 0L) {
            plugin.getLogger().warning("Invalid failure-healing-penalty, using 12h");
            penalty = DEFAULT_FAILURE_PENALTY_MS;
        }
        if (penalty == 0L) {
            return null;
        }
        long remaining = Ailments.extend(patient, traitId, penalty);
        if (remaining < 0L) {
            return null;
        }
        String extra = Durations.formatHours(penalty);
        patient.sendMessage(uiUpdater.getMessage("patient-surgery-failed")
            .replace("%ailment%", stateManager.getAilmentName(surgeonId))
            .replace("%extra%", extra)
            .replace("%remaining%", Durations.formatHours(remaining)));
        return extra;
    }

    // ==============================================
    // Closes the menu on the next tick; calling closeInventory directly inside
    // an InventoryClickEvent handler is undefined behavior per the Bukkit docs
    // ==============================================
    private void closeMenuNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
    }

    // ==============================================
    // Handles the surgeon closing the menu before finishing
    // Stopping before the patient is sedated or cut does no harm; after that, the surgery fails
    // ==============================================
    public void handleAbandonment(Player player) {
        UUID playerId = player.getUniqueId();
        if (!stateManager.hasSession(playerId)) {
            return;
        }
        if (stateManager.hasOperated(playerId)) {
            failSurgery(player, uiUpdater.getMessage("failure-gave-up"));
        } else {
            stateManager.cleanup(playerId);
            player.sendMessage(uiUpdater.getMessage("surgery-cancelled"));
        }
    }

    // ==============================================
    // Handles the surgeon disconnecting mid-surgery
    // Runs the failure command and penalty once treatment has begun (no
    // free escape by logging out) but skips the surgeon's messages, sounds, and
    // menu close, which are pointless for a quitting player
    // ==============================================
    public void handleQuit(Player player) {
        UUID playerId = player.getUniqueId();
        if (stateManager.hasOperated(playerId)) {
            executeCompletionCommand(player, false);
            lengthenHealing(player);
        }
        stateManager.cleanup(playerId);
    }

    private Player patientOf(UUID surgeonId) {
        UUID patientId = stateManager.getPatientUuid(surgeonId);
        return patientId == null ? null : Bukkit.getPlayer(patientId);
    }

    // ==============================================
    // Executes the configured command for surgery completion (success or failure)
    // ==============================================
    private void executeCompletionCommand(Player surgeon, boolean success) {
        UUID surgeonId = surgeon.getUniqueId();
        String patientName = stateManager.getPatientName(surgeonId);

        String configKey = success ? "commands.surgery-success" : "commands.surgery-failure";
        String command = plugin.getConfig().getString(configKey, "");

        if (command != null && !command.isEmpty()) {
            command = command.replace("%surgeon%", surgeon.getName());
            command = command.replace("%player%", patientName);
            command = command.replace("%ailment%", stateManager.getAilmentName(surgeonId));

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }
}
