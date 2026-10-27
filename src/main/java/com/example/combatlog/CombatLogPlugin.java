package com.example.combatlog;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatLogPlugin extends JavaPlugin {

    private CombatManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new CombatManager(this, new Settings(getConfig(), getLogger()));

        getServer().getPluginManager().registerEvents(new CombatListener(manager), this);

        PluginCommand command = getCommand("combatlog");
        if (command != null) {
            CombatCommand handler = new CombatCommand(this, manager);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        manager.start();
        getLogger().info("CombatLog aktiviert.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();
        manager.applySettings(new Settings(getConfig(), getLogger()));
    }
}
