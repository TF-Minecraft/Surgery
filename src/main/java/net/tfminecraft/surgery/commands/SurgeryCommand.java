package net.tfminecraft.surgery.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.tfminecraft.surgery.managers.SurgeryMenuManager;
import net.tfminecraft.surgery.managers.SurgeryUIUpdater;
import net.tfminecraft.surgery.SurgeryPlugin;

public class SurgeryCommand implements CommandExecutor {

    private final SurgeryMenuManager menuManager;
    private final SurgeryPlugin plugin;
    private final SurgeryUIUpdater uiUpdater;

    public SurgeryCommand(SurgeryMenuManager menuManager, SurgeryPlugin plugin) {
        this.menuManager = menuManager;
        this.plugin = plugin;
        this.uiUpdater = menuManager.getUiUpdater();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(uiUpdater.getMessage("command-console", "&cThis command can only be used by players."));
            return true;
        }

        Player surgeon = (Player) sender;

        // Block re-entry: opening a second menu fires the old menu's close event,
        // which fails the surgery and wipes the freshly initialized session
        if (menuManager.getStateManager().hasSession(surgeon.getUniqueId())) {
            surgeon.sendMessage(uiUpdater.getMessage("command-already-in-surgery",
                "&cYou are already performing surgery! Close the current menu first."));
            return true;
        }

        // Check if player name is provided in command
        if (args.length == 0) {
            surgeon.sendMessage(uiUpdater.getMessage("command-usage", "&cUsage: /surgery <player_name>"));
            return true;
        }

        // Get the target player (patient)
        Player patient = Bukkit.getPlayer(args[0]);
        if (patient == null || !patient.isOnline()) {
            surgeon.sendMessage(uiUpdater.getMessage("command-player-not-found", "&cPlayer not found or not online!"));
            return true;
        }

        // Check if trying to "operate" on self
        if (patient.getUniqueId().equals(surgeon.getUniqueId())) {
            surgeon.sendMessage(uiUpdater.getMessage("command-self-surgery", "&cYou cannot perform surgery on yourself!"));
            return true;
        }

        // One surgery per patient: a second surgeon opening a menu on the same
        // patient would run two independent surgeries on one body
        if (menuManager.getStateManager().isPatientInSurgery(patient.getUniqueId())) {
            surgeon.sendMessage(uiUpdater.getMessage("command-patient-in-surgery",
                "&cThat player is already undergoing surgery!"));
            return true;
        }

        // A player mid-operation as a surgeon cannot also be a patient
        if (menuManager.getStateManager().hasSession(patient.getUniqueId())) {
            surgeon.sendMessage(uiUpdater.getMessage("command-patient-is-operating",
                "&cThat player is busy performing surgery themselves!"));
            return true;
        }

        // Check if patient is within config distance
        // World check first: distance() throws across worlds
        double maxDistance = plugin.getConfig().getDouble("max-surgery-distance", 5.0);
        if (!surgeon.getWorld().equals(patient.getWorld())
                || surgeon.getLocation().distance(patient.getLocation()) > maxDistance) {
            // Trim "5.0" to "5" but keep fractional configs like "7.5"
            String distance = maxDistance == Math.floor(maxDistance)
                ? String.valueOf((long) maxDistance) : String.valueOf(maxDistance);
            surgeon.sendMessage(uiUpdater.getMessage("command-too-far", "&cThe patient must be within %distance% blocks of you!")
                .replace("%distance%", distance));
            return true;
        }

        // Open surgery menu for the player, operating on the specified "patient"
        menuManager.openSurgeryMenu(surgeon, patient);

        return true;
    }
}
