package com.illusioncis7.opencore.web;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    public String getInternalHost() {
        return internalHost;
    }

    public int getInternalPort() {
        return internalPort;
    }

    /** Validate token once and mark as used. */
    public UUID validateToken(String token, String type) {
        if (token == null) return null;
        cleanupExpiredTokens();
        if (!database.isConnected()) return null;
        String sql = "SELECT id, player_uuid, type, expires_at, used FROM web_access_tokens WHERE token = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean used = rs.getBoolean("used");
                    String t = rs.getString("type");
                    Timestamp exp = rs.getTimestamp("expires_at");
                    if (!used && (type == null || type.equals(t)) && exp.toInstant().isAfter(Instant.now())) {
                        String id = rs.getString("id");
                        try (PreparedStatement upd = conn.prepareStatement("UPDATE web_access_tokens SET used = 1 WHERE id = ?")) {
                            upd.setString(1, id);
                            upd.executeUpdate();
                        }
                        return UUID.fromString(rs.getString("player_uuid"));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to validate token: " + e.getMessage());
        }
        return null;
    }

    /** Check token without updating its used flag. */
    public UUID checkToken(String token) {
        if (token == null) return null;
        cleanupExpiredTokens();
        if (!database.isConnected()) return null;
        String sql = "SELECT player_uuid, expires_at FROM web_access_tokens WHERE token = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp exp = rs.getTimestamp("expires_at");
                    if (exp.toInstant().isAfter(Instant.now())) {
                        return UUID.fromString(rs.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to check token: " + e.getMessage());
        }
        return null;
    }

    /** Delete expired or used tokens. */
    public void cleanupExpiredTokens() {
        if (!database.isConnected()) return;
        String sql = "DELETE FROM web_access_tokens WHERE expires_at < ? OR used = 1";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to clean tokens: " + e.getMessage());
        }
    }
}
