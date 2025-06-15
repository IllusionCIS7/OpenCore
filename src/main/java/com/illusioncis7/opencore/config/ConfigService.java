package com.illusioncis7.opencore.config;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.sql.Timestamp;

import com.illusioncis7.opencore.config.ConfigType;

/**
 * Scans configuration files and allows updates of single parameters.
 */
public class ConfigService {

    private final JavaPlugin plugin;
    private final Database database;
    private final Set<String> excluded = new HashSet<>();
    private boolean enabled = true;
    private Path serverRoot;

    public ConfigService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;

        File cfgFile = new File(plugin.getDataFolder(), "config-scan.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(cfgFile);
        this.enabled = config.getBoolean("enabled", true);
        this.excluded.addAll(config.getStringList("excluded"));
    }

    /**
     * Recursively scan a directory for configuration files and store
     * the contained parameter paths in the database.
     */
    public void scanAndStore(File root) {
        if (!enabled) {
            plugin.getLogger().info("Config scan disabled via configuration.");
            return;
        }
        if (root == null || !root.exists()) {
            return;
        }

        this.serverRoot = root.getAbsoluteFile().toPath();
        scanInternal(root.getAbsoluteFile());
    }

    private void scanInternal(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        String relative = serverRoot.relativize(file.getAbsoluteFile().toPath()).toString().replace(File.separatorChar, '/');
        for (String ex : excluded) {
            if (relative.equals(ex) || relative.startsWith(ex + "/")) {
                return;
            }
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    scanInternal(child);
                }
            }
        } else {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".yml")) {
                storeYaml(file);
            } else if (name.endsWith(".conf")) {
                storeConf(file);
            }
        }
    }

    private void storeYaml(File file) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Map<String, Object> values = cfg.getValues(true);
        String relative = serverRoot.relativize(file.getAbsoluteFile().toPath()).toString().replace(File.separatorChar, '/');
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object val = entry.getValue();
            insertParameter(relative, entry.getKey(), val != null ? val.toString() : null);
        }
    }

    private void storeConf(File file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            String relative = serverRoot.relativize(file.getAbsoluteFile().toPath()).toString().replace(File.separatorChar, '/');
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    continue;
                }
                String[] parts = line.split("[:=]", 2);
                if (parts.length == 2) {
                    insertParameter(relative, parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to parse conf file " + file + ": " + e.getMessage());
        }
    }

    private void insertParameter(String path, String parameter, String value) {
        if (!database.isConnected()) {
            return;
        }

        String sql;
        if (database.isSQLite()) {
            sql = "INSERT INTO config_params (path, parameter_path, description, value_type, current_value) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON CONFLICT(path, parameter_path) DO UPDATE SET " +
                    "current_value=excluded.current_value, description=excluded.description";
        } else {
            sql = "INSERT INTO config_params (path, parameter_path, description, value_type, current_value) VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE path = VALUES(path), current_value = VALUES(current_value), description = VALUES(description)";
        }

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setString(2, parameter);
            ps.setNull(3, Types.VARCHAR);
            ps.setString(4, ConfigType.STRING.name());
            if (value != null) {
                ps.setString(5, value);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to store config parameter " + parameter + ": " + e.getMessage());
        }
    }

    /** Register a parameter manually via the web interface. */
    public boolean registerParameter(String configFilePath, String yamlPath, boolean editable, String description,
                                     String currentValue, Integer minValue, Integer maxValue) {
        if (!database.isConnected()) return false;
        String sql = "INSERT INTO config_params (path, parameter_path, editable, description, current_value, min_value, max_value) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, configFilePath);
            ps.setString(2, yamlPath);
            ps.setBoolean(3, editable);
            ps.setString(4, description);
            if (currentValue != null) ps.setString(5, currentValue); else ps.setNull(5, Types.VARCHAR);
            if (minValue != null) ps.setInt(6, minValue); else ps.setNull(6, Types.INTEGER);
            if (maxValue != null) ps.setInt(7, maxValue); else ps.setNull(7, Types.INTEGER);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to register parameter: " + e.getMessage());
            return false;
        }
    }

    /**
     * Update a configuration parameter by ID with a new value.
     */
    public boolean updateParameter(int id, String newValue, UUID changedBy) {
        if (!database.isConnected()) {
            return false;
        }

        String query = "SELECT path, parameter_path, value_type, min_value, max_value FROM config_params WHERE id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String path = rs.getString(1);
                    String param = rs.getString(2);
                    String typeStr = rs.getString(3);
                    int min = rs.getInt(4);
                    int max = rs.getInt(5);
                    ConfigType type = ConfigType.STRING;
                    if (typeStr != null) {
                        try { type = ConfigType.valueOf(typeStr.toUpperCase()); } catch (IllegalArgumentException ignore) {}
                    }
                    Object typed = parseValue(type, newValue);
                    if (!validateValue(type, typed, min, max)) {
                        plugin.getLogger().warning("Invalid value for parameter " + id);
                        return false;
                    }
                    File file = serverRoot.resolve(path).toFile();
                    Object oldVal = loadValue(file, param);
                    boolean ok = updateFile(file, param, typed);
                    if (ok) {
                        updateStoredValue(id, newValue);
                        logChange(id, oldVal, newValue, changedBy);
                    }
                    return ok;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching config parameter: " + e.getMessage());
        }
        return false;
    }

    private boolean updateFile(File file, String parameterPath, Object value) {
        if (file.getName().toLowerCase().endsWith(".yml")) {
            try {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                cfg.set(parameterPath, value);
                cfg.save(file);
                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to update YAML file " + file + ": " + e.getMessage());
            }
        } else if (file.getName().toLowerCase().endsWith(".conf")) {
            try {
                Map<String, String> map = new HashMap<>();
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                            map.put(line, null);
                            continue;
                        }
                        String[] parts = trimmed.split("[:=]", 2);
                        if (parts.length == 2) {
                            map.put(parts[0].trim(), parts[1]);
                        } else {
                            map.put(line, null);
                        }
                    }
                }
                map.put(parameterPath, String.valueOf(value));
                try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        if (entry.getValue() == null) {
                            pw.println(entry.getKey());
                        } else {
                            pw.println(entry.getKey() + "=" + entry.getValue());
                        }
                    }
                }
                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to update conf file " + file + ": " + e.getMessage());
            }
        }
        return false;
    }

    private Object parseValue(ConfigType type, String value) {
        if (value == null) return null;
        switch (type) {
            case BOOLEAN:
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    return Boolean.parseBoolean(value);
                }
                return null;
            case INTEGER:
                try { return Integer.parseInt(value); } catch (NumberFormatException e) { return null; }
            case LIST:
            case STRING:
            default:
                return value;
        }
    }

    private boolean validateValue(ConfigType type, Object value, int min, int max) {
        if (value == null) return false;
        if (type == ConfigType.INTEGER) {
            int v = (Integer) value;
            return v >= min && v <= max;
        }
        return true;
    }

    private Object loadValue(File file, String parameterPath) {
        try {
            if (file.getName().toLowerCase().endsWith(".yml")) {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                return cfg.get(parameterPath);
            } else if (file.getName().toLowerCase().endsWith(".conf")) {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith(parameterPath + "=")) {
                            return line.substring(parameterPath.length() + 1).trim();
                        }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read config value: " + e.getMessage());
        }
        return null;
    }

    private void logChange(int paramId, Object oldVal, Object newVal, UUID player) {
        if (!database.isConnected()) return;
        String sql = "INSERT INTO config_change_history (change_id, player_uuid, param_key, old_value, new_value, changed_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            if (player != null) {
                ps.setString(2, player.toString());
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, String.valueOf(paramId));
            ps.setString(4, oldVal != null ? oldVal.toString() : null);
            ps.setString(5, newVal != null ? newVal.toString() : null);
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to log config change: " + e.getMessage());
        }
    }

    private void updateStoredValue(int id, String value) {
        if (!database.isConnected()) return;
        String sql = "UPDATE config_params SET current_value = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (value != null) {
                ps.setString(1, value);
            } else {
                ps.setNull(1, Types.VARCHAR);
            }
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to update stored value: " + e.getMessage());
        }
    }

    /**
     * Rollback a change by applying the stored old value.
     */
    public boolean rollbackChange(UUID changeId) {
        if (!database.isConnected()) return false;
        String sql = "SELECT param_key, old_value FROM config_change_history WHERE change_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, changeId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int paramId = Integer.parseInt(rs.getString(1));
                    String oldVal = rs.getString(2);
                    return updateParameter(paramId, oldVal, null);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to rollback config: " + e.getMessage());
        }
        return false;
    }

    /**
     * Fetch all configuration parameters with their current value.
     */
    public java.util.List<ConfigParameter> listParameters() {
        java.util.List<ConfigParameter> list = new java.util.ArrayList<>();
        if (!database.isConnected()) return list;
        String sql = "SELECT id, path, parameter_path, editable, impact_rating, value_type, current_value FROM config_params";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ConfigParameter p = new ConfigParameter();
                p.setId(rs.getInt(1));
                String path = rs.getString(2);
                String param = rs.getString(3);
                p.setPath(path);
                p.setParameterPath(param);
                p.setEditable(rs.getBoolean(4));
                p.setImpactRating(rs.getInt(5));
                String vt = rs.getString(6);
                if (vt != null) {
                    try { p.setValueType(ConfigType.valueOf(vt.toUpperCase())); } catch (IllegalArgumentException ignore) {}
                }
                String currentVal = rs.getString(7);
                if (currentVal == null) {
                    File file = serverRoot.resolve(path).toFile();
                    Object val = loadValue(file, param);
                    currentVal = val != null ? val.toString() : null;
                }
                p.setCurrentValue(currentVal);
                list.add(p);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load config parameters: " + e.getMessage());
        }
        return list;
    }

    /**
     * Count parameters that currently have a value in their underlying files.
     */
    public int countParametersWithValue() {
        int count = 0;
        for (ConfigParameter p : listParameters()) {
            if (p.getCurrentValue() != null) {
                count++;
            }
        }
        return count;
    }
}

