package com.illusioncis7.opencore.web;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class WebTokenService {
    private final JavaPlugin plugin;
    private final Database database;

    private String internalHost = "127.0.0.1";
    private int internalPort = 8081;
    private String publicUrl = "http://localhost:8081";
    private int votePingIntervalMinutes = 60;
    private int tokenValidityMinutes = 30;

    public WebTokenService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        loadConfig();
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "opencore.yml");
        if (!file.exists()) {
            plugin.saveResource("opencore.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("web_interface");
        if (sec != null) {
            internalHost = sec.getString("internal_host", internalHost);
            internalPort = sec.getInt("internal_port", internalPort);
            publicUrl = sec.getString("public_url", publicUrl);
            votePingIntervalMinutes = sec.getInt("vote_ping_interval_minutes", votePingIntervalMinutes);
            tokenValidityMinutes = sec.getInt("token_validity_minutes", tokenValidityMinutes);
        }
    }

    public String issueToken(UUID player, String type) {
        if (!database.isConnected()) return null;
        String id = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        Instant expires = now.plus(Duration.ofMinutes(tokenValidityMinutes));
        String sql = "INSERT INTO web_access_tokens (id, player_uuid, token, type, issued_at, expires_at, used) VALUES (?, ?, ?, ?, ?, ?, 0)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, player.toString());
            ps.setString(3, token);
            ps.setString(4, type);
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setTimestamp(6, Timestamp.from(expires));
            ps.executeUpdate();
            return token;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to store access token: " + e.getMessage());
            return null;
        }
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public int getTokenValidityMinutes() {
        return tokenValidityMinutes;
    }

    public int getVotePingIntervalMinutes() {
        return votePingIntervalMinutes;
    }
}
