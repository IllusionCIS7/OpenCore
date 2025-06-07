package com.illusioncis7.opencore.reputation;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class ReputationService {
    private final JavaPlugin plugin;
    private final Database database;
    private final Logger logger;

    public ReputationService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.logger = plugin.getLogger();
    }

    public synchronized void adjustReputation(UUID playerUuid, int delta, String reason, String source, String detailsJson) {
        if (database.getConnection() == null) {
            return;
        }
        if (delta == 0) {
            return;
        }
        delta = Math.max(-100, Math.min(100, delta));
        try (Connection conn = database.getConnection()) {
            ensurePlayer(conn, playerUuid);
            String updateSql = "UPDATE player_registry SET reputation_score = GREATEST(-100, LEAST(100, reputation_score + ?)) WHERE uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, delta);
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            }
            insertEvent(conn, playerUuid, delta, reason, source, detailsJson);
            logger.info("Reputation for " + playerUuid + " adjusted by " + delta + " due to " + reason + " from " + source);
        } catch (SQLException e) {
            logger.severe("Failed to adjust reputation: " + e.getMessage());
        }
    }

    private void ensurePlayer(Connection conn, UUID playerUuid) throws SQLException {
        String checkSql = "SELECT uuid FROM player_registry WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        String alias = UUID.randomUUID().toString();
        String insertSql = "INSERT INTO player_registry (uuid, alias_id, reputation_score, reputation_rank) VALUES (?, ?, 0, 'Neuling')";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, alias);
            ps.executeUpdate();
        }
    }

    private void insertEvent(Connection conn, UUID playerUuid, int delta, String reason, String source, String detailsJson) throws SQLException {
        String eventSql = "INSERT INTO reputation_events (id, timestamp, player_uuid, change, reason_summary, source_module, details) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(eventSql)) {
            UUID eventId = UUID.randomUUID();
            ps.setString(1, eventId.toString());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, playerUuid.toString());
            ps.setInt(4, delta);
            ps.setString(5, reason);
            ps.setString(6, source);
            ps.setString(7, detailsJson);
            ps.executeUpdate();
        }
    }

    public synchronized int getReputation(UUID playerUuid) {
        if (database.getConnection() == null) {
            return 0;
        }
        String sql = "SELECT reputation_score FROM player_registry WHERE uuid = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to fetch reputation: " + e.getMessage());
        }
        return 0;
    }

    public synchronized List<ReputationEvent> getHistory(UUID playerUuid) {
        List<ReputationEvent> list = new ArrayList<>();
        if (database.getConnection() == null) {
            return list;
        }
        String sql = "SELECT id, timestamp, change, reason_summary, source_module, details FROM reputation_events WHERE player_uuid = ? ORDER BY timestamp";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString(1));
                    Instant ts = rs.getTimestamp(2).toInstant();
                    int change = rs.getInt(3);
                    String reason = rs.getString(4);
                    String source = rs.getString(5);
                    String details = rs.getString(6);
                    list.add(new ReputationEvent(id, ts, playerUuid, change, reason, source, details));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to fetch reputation history: " + e.getMessage());
        }
        return list;
    }

    /**
     * Ensure that a player exists in the registry.
     * This is used on player login to create the initial entry
     * with a random alias and reputation score of 0.
     */
    public synchronized void registerPlayer(UUID playerUuid) {
        if (database.getConnection() == null) {
            return;
        }
        try (Connection conn = database.getConnection()) {
            ensurePlayer(conn, playerUuid);
        } catch (SQLException e) {
            logger.severe("Failed to register player: " + e.getMessage());
        }
    }
}
