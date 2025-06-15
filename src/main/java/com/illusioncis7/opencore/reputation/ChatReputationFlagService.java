package com.illusioncis7.opencore.reputation;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Loads reputation flags for chat analysis from <code>chat_flags.yml</code>.
 */
public class ChatReputationFlagService {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final File configFile;

    private final Map<String, ReputationFlag> allFlags = new HashMap<>();
    private final Map<String, ReputationFlag> activeFlags = new HashMap<>();

    public ChatReputationFlagService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "chat_flags.yml");
        reload();
    }

    /**
     * Reload flags from disk. Invalid entries are ignored and logged.
     */
    public synchronized void reload() {
        if (!configFile.exists()) {
            plugin.saveResource("chat_flags.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        allFlags.clear();
        activeFlags.clear();
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec == null) {
                logger.warning("Invalid flag entry: " + key);
                continue;
            }
            boolean active = sec.getBoolean("active", true);
            String desc = sec.getString("description", "");
            if (!sec.isInt("min_change") || !sec.isInt("max_change")) {
                logger.warning("Flag " + key + " has no valid min/max values");
                continue;
            }
            int min = sec.getInt("min_change");
            int max = sec.getInt("max_change");
            ReputationFlag f = new ReputationFlag(key, desc, min, max, active);
            allFlags.put(key, f);
            if (active) {
                activeFlags.put(key, f);
            }
        }
    }

    /**
     * List all flags defined in the configuration.
     */
    public List<ReputationFlag> listFlags() {
        return new ArrayList<>(allFlags.values());
    }

    /**
     * @return list of active flags only.
     */
    public List<ReputationFlag> getActiveFlags() {
        return new ArrayList<>(activeFlags.values());
    }

    /**
     * @return map of active flags keyed by code.
     */
    public Map<String, ReputationFlag> getFlagMap() {
        return new HashMap<>(activeFlags);
    }

    /**
     * Update the min/max range for a flag and persist the file.
     */
    public synchronized boolean setRange(String code, int min, int max) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection sec = cfg.getConfigurationSection(code);
        if (sec == null) return false;
        sec.set("min_change", min);
        sec.set("max_change", max);
        return save(cfg);
    }

    /**
     * Update all fields of a flag and persist the file.
     */
    public synchronized boolean updateFlag(String code, String desc, int min, int max, boolean active) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection sec = cfg.getConfigurationSection(code);
        if (sec == null) return false;
        sec.set("description", desc);
        sec.set("min_change", min);
        sec.set("max_change", max);
        sec.set("active", active);
        boolean ok = save(cfg);
        if (ok) reload();
        return ok;
    }

    /**
     * Add a new flag and persist the file.
     */
    public synchronized boolean createFlag(String code, String desc, int min, int max, boolean active) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        if (cfg.isConfigurationSection(code)) return false;
        ConfigurationSection sec = cfg.createSection(code);
        sec.set("description", desc);
        sec.set("min_change", min);
        sec.set("max_change", max);
        sec.set("active", active);
        boolean ok = save(cfg);
        if (ok) reload();
        return ok;
    }

    private boolean save(FileConfiguration cfg) {
        try {
            cfg.save(configFile);
            return true;
        } catch (IOException e) {
            logger.warning("Failed to save chat_flags.yml: " + e.getMessage());
            return false;
        }
    }
}
