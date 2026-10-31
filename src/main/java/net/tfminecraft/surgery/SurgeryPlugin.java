package net.tfminecraft.surgery;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import net.tfminecraft.surgery.managers.SurgeryItemsConfig;
import net.tfminecraft.surgery.managers.SurgeryMenuHolder;
import net.tfminecraft.surgery.managers.SurgeryMenuManager;
import net.tfminecraft.surgery.listeners.PlayerListener;
import net.tfminecraft.surgery.commands.SurgeryCommand;

public class SurgeryPlugin extends JavaPlugin {

    private static SurgeryPlugin instance;
    private SurgeryMenuManager surgeryMenuManager;

    @Override
    public void onEnable() {
        try {
            net.tfminecraft.tlibs.character.Characters.available();
        } catch (LinkageError error) {
            getLogger().severe("Surgery requires TLibs 2.3.0 or newer. Update TLibs and restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        instance = this;

        saveDefaultConfig();

        // Load surgery items config
        SurgeryItemsConfig itemsConfig = new SurgeryItemsConfig(this);

        surgeryMenuManager = new SurgeryMenuManager(this, itemsConfig);
        surgeryMenuManager.initialize();

        // initialize() disables the plugin if the TLibs API could not be loaded
        if (!isEnabled()) {
            return;
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(surgeryMenuManager), this);
        SurgeryCommand surgeryCommand = new SurgeryCommand(surgeryMenuManager, this);
        getCommand("surgery").setExecutor(surgeryCommand);
        getCommand("surgery").setTabCompleter(surgeryCommand);

        // The first tick runs once every plugin has enabled.
        getServer().getScheduler().runTask(this, this::warnIfNoCharacterProvider);

        getLogger().info("surgery has been enabled!");
    }

    /** RPCharacters before 2.16.0 registers no TLibs character provider, so every offer would fail. */
    void warnIfNoCharacterProvider() {
        if (getServer().getPluginManager().isPluginEnabled("RPCharacters")
                && !net.tfminecraft.tlibs.character.Characters.available()) {
            getLogger().warning("RPCharacters provides no character identity, so every surgery offer will fail."
                    + " Surgery requires RPCharacters 2.16.0 or newer.");
        }
    }

    @Override
    public void onDisable() {
        // Unregister listeners first: closing menus below would otherwise fire
        // the abandonment handler, which tries to schedule a task on a
        // disabling plugin (IllegalPluginAccessException)
        HandlerList.unregisterAll(this);
        if (surgeryMenuManager != null) {
            surgeryMenuManager.shutdown();
        }

        // Close open surgery menus. After a reload the holder class comes from
        // a new classloader, so the instanceof check in the listener no longer
        // matches and players could pull items out of stale menus
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof SurgeryMenuHolder) {
                player.closeInventory();
            }
        }

        getLogger().info("surgery has been disabled!");
    }

    public static SurgeryPlugin getInstance() {
        return instance;
    }

}
