package com.illusioncis7.opencore.config;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Scans configuration files and allows updates of single parameters.
 */
public class ConfigService {

    private final JavaPlugin plugin;
    private final Database database;

    public ConfigService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Recursively scan a directory for configuration files and store
     * the contained parameter paths in the database.
     */
    public void scanAndStore(File root) {
        if (root == null || !root.exists()) {
            return;
        }

        if (root.isDirectory()) {
            File[] files = root.listFiles();
            if (files != null) {
                for (File file : files) {
                    scanAndStore(file);
                }
            }
        } else {
            String name = root.getName().toLowerCase();
            if (name.endsWith(".yml")) {
                storeYaml(root);
            } else if (name.endsWith(".conf")) {
                storeConf(root);
            }
        }
    }

    private void storeYaml(File file) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Map<String, Object> values = cfg.getValues(true);
        for (String key : values.keySet()) {
            insertParameter(file.getAbsolutePath(), key);
        }
    }

    private void storeConf(File file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                    continue;
                }
                String[] parts = line.split("[:=]", 2);
                if (parts.length == 2) {
                    insertParameter(file.getAbsolutePath(), parts[0].trim());
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to parse conf file " + file + ": " + e.getMessage());
        }
    }

    private void insertParameter(String path, String parameter) {
        if (database.getConnection() == null) {
            return;
        }

        String sql = "INSERT INTO config_params (path, parameter_path) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE path = VALUES(path)";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setString(2, parameter);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to store config parameter " + parameter + ": " + e.getMessage());
        }
    }

    /**
     * Update a configuration parameter by ID with a new value.
     */
    public boolean updateParameter(int id, String newValue) {
        if (database.getConnection() == null) {
            return false;
        }

        String query = "SELECT path, parameter_path FROM config_params WHERE id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String path = rs.getString(1);
                    String param = rs.getString(2);
                    return updateFile(new File(path), param, newValue);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching config parameter: " + e.getMessage());
        }
        return false;
    }

    private boolean updateFile(File file, String parameterPath, String value) {
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
                map.put(parameterPath, value);
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
}

