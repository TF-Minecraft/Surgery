package net.tfminecraft.surgery.commands;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.rpcharacters.api.HealingInjuries.HealingInjury;
import net.tfminecraft.surgery.SurgeryPlugin;
import net.tfminecraft.surgery.managers.SurgeryMenuManager;
import net.tfminecraft.surgery.managers.SurgeryRequestManager;
import net.tfminecraft.surgery.managers.SurgeryUIUpdater;
import net.tfminecraft.surgery.procedures.Ailments;
import net.tfminecraft.surgery.procedures.Durations;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SurgeryCommand implements TabExecutor {

    private final SurgeryMenuManager menuManager;
    private final SurgeryPlugin plugin;
    private final SurgeryUIUpdater uiUpdater;
    private final SurgeryRequestManager requests;

    public SurgeryCommand(SurgeryMenuManager menuManager, SurgeryPlugin plugin) {
        this.menuManager = menuManager;
        this.plugin = plugin;
        this.uiUpdater = menuManager.getUiUpdater();
        this.requests = menuManager.getRequestManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(uiUpdater.getMessage("command-console", "&cThis command can only be used by players."));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(uiUpdater.getMessage("command-usage", "&cUsage: /surgery <player> | accept | deny"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("accept")) {
            accept(player);
        } else if (sub.equals("deny")) {
            deny(player);
        } else {
            offer(player, args[0]);
        }
        return true;
    }

    // ==============================================
    // Surgeon offers to operate on a patient's worst healing ailment
    // ==============================================
    private void offer(Player surgeon, String patientName) {
        if (!canOperate(surgeon)) {
            surgeon.sendMessage(uiUpdater.getMessage("command-no-permission"));
            return;
        }

        Player patient = Bukkit.getPlayerExact(patientName);
        if (patient == null || !patient.isOnline()) {
            surgeon.sendMessage(uiUpdater.getMessage("command-player-not-found", "&cPlayer not found or not online!"));
            return;
        }
        if (!checkCanStart(surgeon, patient)) {
            return;
        }

        HealingInjury ailment = Ailments.mostSevere(patient);
        if (ailment == null) {
            surgeon.sendMessage(uiUpdater.getMessage("command-no-ailment").replace("%patient%", patient.getName()));
            return;
        }

        int timeoutSeconds = Math.max(10, plugin.getConfig().getInt("request-timeout-seconds", 60));
        requests.offer(patient.getUniqueId(), surgeon.getUniqueId(), ailment.traitId(),
            System.currentTimeMillis() + timeoutSeconds * 1000L);

        String ailmentName = ChatColor.stripColor(ailment.displayName());
        String penalty = plugin.getConfig().getString("failure-healing-penalty", "12h");
        long penaltyMs = Durations.parseMs(penalty);
        surgeon.sendMessage(uiUpdater.getMessage("request-sent")
            .replace("%patient%", patient.getName()).replace("%ailment%", ailmentName));
        patient.sendMessage(uiUpdater.getMessage("request-received")
            .replace("%surgeon%", surgeon.getName())
            .replace("%ailment%", ailmentName)
            .replace("%remaining%", Durations.formatHours(ailment.remainingMs()))
            .replace("%extra%", penaltyMs > 0 ? Durations.formatHours(penaltyMs) : penalty)
            .replace("%seconds%", String.valueOf(timeoutSeconds)));
        patient.sendMessage(LegacyComponentSerializer.legacySection()
            .deserialize(uiUpdater.getMessage("request-accept-button"))
            .clickEvent(ClickEvent.runCommand("/surgery accept"))
            .append(LegacyComponentSerializer.legacySection().deserialize("  "))
            .append(LegacyComponentSerializer.legacySection()
                .deserialize(uiUpdater.getMessage("request-deny-button"))
                .clickEvent(ClickEvent.runCommand("/surgery deny"))));
    }

    // ==============================================
    // Patient agrees; the surgeon's operating menu opens
    // ==============================================
    private void accept(Player patient) {
        SurgeryRequestManager.Request request = requests.take(patient.getUniqueId(), System.currentTimeMillis());
        if (request == null) {
            patient.sendMessage(uiUpdater.getMessage("request-none"));
            return;
        }
        Player surgeon = Bukkit.getPlayer(request.surgeonId());
        if (surgeon == null || !surgeon.isOnline() || !canOperate(surgeon)) {
            patient.sendMessage(uiUpdater.getMessage("request-surgeon-gone"));
            return;
        }
        if (!checkCanStart(surgeon, patient)) {
            patient.sendMessage(uiUpdater.getMessage("request-cannot-start").replace("%surgeon%", surgeon.getName()));
            return;
        }
        HealingInjury ailment = Ailments.find(patient, request.traitId());
        if (ailment == null) {
            patient.sendMessage(uiUpdater.getMessage("request-ailment-gone"));
            return;
        }

        patient.sendMessage(uiUpdater.getMessage("request-accepted-patient").replace("%surgeon%", surgeon.getName()));
        surgeon.sendMessage(uiUpdater.getMessage("request-accepted-surgeon").replace("%patient%", patient.getName()));
        menuManager.openSurgeryMenu(surgeon, patient,
            new HealingInjury(ailment.traitId(), ChatColor.stripColor(ailment.displayName()), ailment.remainingMs()));
    }

    private void deny(Player patient) {
        SurgeryRequestManager.Request request = requests.take(patient.getUniqueId(), System.currentTimeMillis());
        if (request == null) {
            patient.sendMessage(uiUpdater.getMessage("request-none"));
            return;
        }
        patient.sendMessage(uiUpdater.getMessage("request-denied-patient"));
        Player surgeon = Bukkit.getPlayer(request.surgeonId());
        if (surgeon != null) {
            surgeon.sendMessage(uiUpdater.getMessage("request-denied-surgeon").replace("%patient%", patient.getName()));
        }
    }

    private boolean canOperate(Player surgeon) {
        String permission = plugin.getConfig().getString("permission", "professions.physician");
        return permission == null || permission.isBlank() || surgeon.hasPermission(permission);
    }

    // ==============================================
    // Checks shared by offering and accepting; messages the surgeon on failure
    // ==============================================
    private boolean checkCanStart(Player surgeon, Player patient) {
        // Block re-entry: opening a second menu fires the old menu's close event,
        // which fails the surgery and wipes the freshly initialized session
        if (menuManager.getStateManager().hasSession(surgeon.getUniqueId())) {
            surgeon.sendMessage(uiUpdater.getMessage("command-already-in-surgery",
                "&cYou are already performing surgery! Close the current menu first."));
            return false;
        }

        if (patient.getUniqueId().equals(surgeon.getUniqueId())) {
            surgeon.sendMessage(uiUpdater.getMessage("command-self-surgery", "&cYou cannot perform surgery on yourself!"));
            return false;
        }

        // One surgery per patient: a second surgeon opening a menu on the same
        // patient would run two independent surgeries on one body
        if (menuManager.getStateManager().isPatientInSurgery(patient.getUniqueId())) {
            surgeon.sendMessage(uiUpdater.getMessage("command-patient-in-surgery",
                "&cThat player is already undergoing surgery!"));
            return false;
        }

        // A player mid-operation as a surgeon cannot also be a patient
        if (menuManager.getStateManager().hasSession(patient.getUniqueId())) {
            surgeon.sendMessage(uiUpdater.getMessage("command-patient-is-operating",
                "&cThat player is busy performing surgery themselves!"));
            return false;
        }

        // World check first: distance() throws across worlds
        double maxDistance = plugin.getConfig().getDouble("max-surgery-distance", 5.0);
        if (!surgeon.getWorld().equals(patient.getWorld())
                || surgeon.getLocation().distance(patient.getLocation()) > maxDistance) {
            // Trim "5.0" to "5" but keep fractional configs like "7.5"
            String distance = maxDistance == Math.floor(maxDistance)
                ? String.valueOf((long) maxDistance) : String.valueOf(maxDistance);
            surgeon.sendMessage(uiUpdater.getMessage("command-too-far", "&cThe patient must be within %distance% blocks of you!")
                .replace("%distance%", distance));
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length != 1) {
            return options;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (String sub : List.of("accept", "deny")) {
            if (sub.startsWith(prefix)) {
                options.add(sub);
            }
        }
        if (sender instanceof Player player && canOperate(player)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player) && player.canSee(online)
                        && online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    options.add(online.getName());
                }
            }
        }
        return options;
    }
}
