package net.tfminecraft.surgery.managers;

import net.tfminecraft.surgery.procedures.Ailments;
import net.tfminecraft.surgery.procedures.Durations;
import net.tfminecraft.surgery.managers.SurgeryStateManager.Outcome;
import net.tfminecraft.tlibs.character.CharacterSession;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent.Reason;
import net.tfminecraft.tlibs.character.Characters;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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

    public void handleSuccess(Player surgeon) {
        Outcome outcome = stateManager.finish(surgeon.getUniqueId());
        if (outcome == null) {
            return;
        }
        Inventory menu = surgeryMenu(surgeon, outcome);
        Player patient = currentPatient(outcome);
        if (!isCurrent(surgeon, outcome.surgeonSession()) || patient == null || outcome.traitId() == null
                || !Ailments.cure(patient, outcome.traitId())) {
            closeMenuNextTick(surgeon, outcome, menu);
            if (isCurrent(surgeon, outcome.surgeonSession())) {
                surgeon.sendMessage(uiUpdater.getMessage("ailment-already-healed")
                        .replace("%ailment%", ailmentName(outcome)));
            }
            return;
        }

        executeCompletionCommand(surgeon, outcome, true);
        closeMenuNextTick(surgeon, outcome, menu);
        if (isCurrent(surgeon, outcome.surgeonSession())) {
            surgeon.playSound(surgeon.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            surgeon.sendMessage(uiUpdater.getMessage("surgery-successful"));
            surgeon.sendMessage(uiUpdater.getMessage("surgery-successful-subtitle")
                    .replace("%ailment%", ailmentName(outcome)).replace("%patient%", patientName(outcome)));
        }
        if (isCurrent(patient, outcome.patientSession())) {
            patient.sendMessage(uiUpdater.getMessage("patient-surgery-successful")
                    .replace("%ailment%", ailmentName(outcome)).replace("%surgeon%", surgeon.getName()));
        }
    }

    public void failSurgery(Player surgeon, String message) {
        Outcome outcome = stateManager.finish(surgeon.getUniqueId());
        if (outcome == null) {
            return;
        }
        Inventory menu = surgeryMenu(surgeon, outcome);
        String extra = outcome.operated() ? lengthenHealing(outcome, null) : null;
        executeCompletionCommand(surgeon, outcome, false);
        closeMenuNextTick(surgeon, outcome, menu);
        reportFailure(surgeon, message, extra);
    }

    public void handleAbandonment(Player surgeon) {
        if (stateManager.hasOperated(surgeon.getUniqueId())) {
            failSurgery(surgeon, uiUpdater.getMessage("failure-gave-up"));
            return;
        }
        Outcome outcome = stateManager.finish(surgeon.getUniqueId());
        if (outcome != null && isCurrent(surgeon, outcome.surgeonSession())) {
            surgeon.sendMessage(uiUpdater.getMessage("surgery-cancelled"));
        }
    }

    /** Ends only the operation involving this exact outgoing session. */
    public void handleEnd(Player surgeon, CharacterSession ending, Reason reason) {
        UUID surgeonId = surgeon.getUniqueId();
        if (ending == null || (!ending.equals(stateManager.getSurgeonSession(surgeonId))
                && !ending.equals(stateManager.getPatientSession(surgeonId)))) {
            return;
        }
        Outcome outcome = stateManager.finish(surgeonId);
        if (outcome == null || reason == Reason.SHUTDOWN) {
            return;
        }
        Inventory menu = surgeryMenu(surgeon, outcome);
        boolean patientEnded = ending.equals(outcome.patientSession());
        String extra = null;
        // A patient who quits or switches mid-operation pays the penalty; a permakill does not.
        if (outcome.operated() && (!patientEnded || reason != Reason.DEATH)) {
            extra = lengthenHealing(outcome, patientEnded ? ending : null);
        }
        // Account-named commands never cross a session-end boundary.
        closeMenuNextTick(surgeon, outcome, menu);
        reportFailure(surgeon, uiUpdater.getMessage(patientEnded ? "failure-patient-left" : "failure-gave-up"), extra);
    }

    private String lengthenHealing(Outcome outcome, CharacterSession leavingPatient) {
        Player patient = currentPatient(outcome);
        if (leavingPatient != null && leavingPatient.equals(outcome.patientSession())) {
            patient = Bukkit.getPlayer(leavingPatient.ref().ownerId());
            // During QUIT or SWITCH the outgoing character is still loaded, but its session is already invalid.
            if (patient == null || !patient.isOnline() || Characters.currentSession(patient).isPresent()) {
                return null;
            }
        }
        if (patient == null || outcome.traitId() == null) {
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
        long remaining = Ailments.extend(patient, outcome.traitId(), penalty);
        if (remaining < 0L) {
            return null;
        }
        String extra = Durations.formatHours(penalty);
        if (isCurrent(patient, outcome.patientSession())) {
            patient.sendMessage(uiUpdater.getMessage("patient-surgery-failed")
                    .replace("%ailment%", ailmentName(outcome))
                    .replace("%extra%", extra)
                    .replace("%remaining%", Durations.formatHours(remaining)));
        }
        return extra;
    }

    /** Tells the account, so a physician who switched away or was permakilled still learns the outcome. */
    private void reportFailure(Player surgeon, String message, String extra) {
        if (!surgeon.isOnline() || Bukkit.getPlayer(surgeon.getUniqueId()) != surgeon) {
            return;
        }
        surgeon.playSound(surgeon.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f);
        surgeon.sendMessage(uiUpdater.getMessage("surgery-failed"));
        surgeon.sendMessage(message);
        if (extra != null) {
            surgeon.sendMessage(uiUpdater.getMessage("surgery-failed-penalty").replace("%extra%", extra));
        }
    }

    private Inventory surgeryMenu(Player surgeon, Outcome outcome) {
        Inventory menu = surgeon.getOpenInventory().getTopInventory();
        return menu.getHolder(false) instanceof SurgeryMenuHolder holder && holder.matches(surgeon, outcome.operationId())
                ? menu : null;
    }

    /** Defer closing out of click handlers, retaining the exact operation's inventory and actor. */
    private void closeMenuNextTick(Player surgeon, Outcome outcome, Inventory menu) {
        if (menu == null || !plugin.isEnabled() || !isCurrent(surgeon, outcome.surgeonSession())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled() && isCurrent(surgeon, outcome.surgeonSession())
                    && surgeon.getOpenInventory().getTopInventory() == menu) {
                surgeon.closeInventory();
            }
        });
    }

    private Player currentPatient(Outcome outcome) {
        CharacterSession token = outcome.patientSession();
        if (token == null) {
            return null;
        }
        Player patient = Bukkit.getPlayer(token.ref().ownerId());
        return patient != null && isCurrent(patient, token) ? patient : null;
    }

    private boolean isCurrent(Player player, CharacterSession token) {
        return token != null && token.ref().ownerId().equals(player.getUniqueId()) && player.isOnline()
                && Bukkit.getPlayer(player.getUniqueId()) == player && Characters.isCurrent(token);
    }

    private void executeCompletionCommand(Player surgeon, Outcome outcome, boolean success) {
        if (!isCurrent(surgeon, outcome.surgeonSession()) || currentPatient(outcome) == null) {
            return;
        }
        String key = success ? "commands.surgery-success" : "commands.surgery-failure";
        String command = plugin.getConfig().getString(key, "");
        if (command != null && !command.isEmpty()) {
            command = command.replace("%surgeon%", surgeon.getName())
                    .replace("%player%", patientName(outcome)).replace("%ailment%", ailmentName(outcome));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private String patientName(Outcome outcome) {
        return outcome.patientName() == null ? "Unknown" : outcome.patientName();
    }

    private String ailmentName(Outcome outcome) {
        return outcome.ailmentName() == null ? "Unknown ailment" : outcome.ailmentName();
    }
}
