package com.illusioncis7.opencore;

import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final OpenCore plugin;
    private FileConfiguration config;

    public ConfigManager(OpenCore plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getLogger().info("ConfigManager initialisiert.");
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        plugin.getLogger().info("Konfiguration geladen.");
    }

    public void saveConfig() {
        plugin.saveConfig();
        plugin.getLogger().info("Konfiguration gespeichert.");
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
