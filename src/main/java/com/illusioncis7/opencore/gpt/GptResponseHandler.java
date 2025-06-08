package com.illusioncis7.opencore.gpt;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.UUID;
import java.util.logging.Logger;

import com.illusioncis7.opencore.gpt.GptResponseRecord;

/**
 * Stores GPT responses for offline players and delivers them on login.
 */
public class GptResponseHandler implements Listener {

    private final JavaPlugin plugin;
    private final Database database;
    private final Logger logger;

    public GptResponseHandler(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.logger = plugin.getLogger();
    }

    /**
     * Handle a response for the given request.
     */
    public void handleResponse(GptRequest request, String response) {
        if (response == null || request.playerUuid == null) {
            return;
        }
        Player player = Bukkit.getPlayer(request.playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(response);
        } else {
            storeResponse(request.playerUuid, request.module, response);
        }
    }

    private void storeResponse(UUID uuid, String module, String response) {
        if (!database.isConnected()) {
            return;
        }
        String sql = "INSERT INTO gpt_responses (player_uuid, module, response, created, delivered) VALUES (?, ?, ?, ?, 0)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            if (module != null) {
                ps.setString(2, module);
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, response);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("Failed to store GPT response: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        deliverPending(uuid, event.getPlayer());
    }

    private void deliverPending(UUID uuid, Player player) {
        if (!database.isConnected()) return;
        String sql = "SELECT id, module, response FROM gpt_responses WHERE player_uuid = ? AND delivered = 0";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String module = rs.getString(2);
                    String text = rs.getString(3);
                    if (module != null && !module.isEmpty()) {
                        player.sendMessage("[" + module + "] " + text);
                    } else {
                        player.sendMessage(text);
                    }
                    markDelivered(id);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to deliver GPT responses: " + e.getMessage());
        }
    }

    private void markDelivered(int id) {
        if (!database.isConnected()) return;
        String sql = "UPDATE gpt_responses SET delivered = 1 WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("Failed to mark response delivered: " + e.getMessage());
        }
    }

    /**
     * Fetch recent GPT responses for a player.
     */
    public java.util.List<GptResponseRecord> getRecentResponses(java.util.UUID uuid, int limit) {
        java.util.List<GptResponseRecord> list = new java.util.ArrayList<>();
        if (!database.isConnected()) return list;
        String sql = "SELECT module, response, created FROM gpt_responses WHERE player_uuid = ? ORDER BY created DESC LIMIT ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String module = rs.getString(1);
                    String text = rs.getString(2);
                    java.time.Instant time = rs.getTimestamp(3).toInstant();
                    list.add(new GptResponseRecord(module, text, time));
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to fetch GPT responses: " + e.getMessage());
        }
        return list;
    }
}
