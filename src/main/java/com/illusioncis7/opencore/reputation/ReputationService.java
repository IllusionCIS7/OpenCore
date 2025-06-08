package com.illusioncis7.opencore.reputation;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

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

    private int minScore = -500;
    private int maxScore = 500;
    private int maxGainPerAnalysis = 10;

    private static class Range {
        final int min;
        final int max;
        Range(int min, int max) { this.min = min; this.max = max; }
    }

    /** Simple DTO for listing player reputations. */
    public static class PlayerReputation {
        public final UUID uuid;
        public final int score;

        public PlayerReputation(UUID uuid, int score) {
            this.uuid = uuid;
            this.score = score;
        }
    }

    private final java.util.Map<String, Range> ranges = new java.util.HashMap<>();

    public ReputationService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.logger = plugin.getLogger();
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "reputation.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection rep = cfg.getConfigurationSection("reputation");
        if (rep != null) {
            minScore = rep.getInt("min-score", -500);
            maxScore = rep.getInt("max-score", 500);
            maxGainPerAnalysis = rep.getInt("maxReputationPerAnalysis", 10);
            ConfigurationSection changes = rep.getConfigurationSection("changes");
            if (changes != null) {
                for (String key : changes.getKeys(false)) {
                    ConfigurationSection cs = changes.getConfigurationSection(key);
                    if (cs != null) {
                        int min = cs.getInt("min", 0);
                        int max = cs.getInt("max", 0);
                        ranges.put(key, new Range(min, max));
                    }
                }
            }
        }
    }

    /** Reload configuration values from reputation.yml. */
    public synchronized void reload() {
        ranges.clear();
        loadConfig();
    }

    public int computeChange(String key, double value) {
        Range r = ranges.get(key);
        if (r == null) return 0;
        value = Math.max(0.0, Math.min(1.0, value));
        double diff = r.max - r.min;
        return (int) Math.round(r.min + diff * value);
    }

    public int getMaxGainPerAnalysis() {
        return maxGainPerAnalysis;
    }

    public boolean hasRange(String key) {
        return ranges.containsKey(key);
    }

    public synchronized void adjustReputation(UUID playerUuid, int delta, String reason, String source, String detailsJson) {
        if (!database.isConnected()) {
            return;
        }
        if (delta == 0) {
            return;
        }
        delta = Math.max(-100, Math.min(100, delta));
        try (Connection conn = database.getConnection()) {
            ensurePlayer(conn, playerUuid);
            String updateSql = "UPDATE player_registry SET reputation_score = GREATEST(?, LEAST(?, reputation_score + ?)) WHERE uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, minScore);
                ps.setInt(2, maxScore);
                ps.setInt(3, delta);
                ps.setString(4, playerUuid.toString());
                ps.executeUpdate();
            }
            insertEvent(conn, playerUuid, delta, reason, source, detailsJson);
            logger.info("Reputation for " + playerUuid + " adjusted by " + delta + " due to " + reason + " from " + source);
        } catch (SQLException e) {
            logger.severe("Failed to adjust reputation: " + e.getMessage());
        }
    }

    /**
     * Set a player's reputation directly.
     *
     * @param playerUuid player id
     * @param newValue   new reputation value
     */
    public synchronized void setReputation(UUID playerUuid, int newValue) {
        if (!database.isConnected()) {
            return;
        }
        newValue = Math.max(minScore, Math.min(maxScore, newValue));
        try (Connection conn = database.getConnection()) {
            ensurePlayer(conn, playerUuid);
            String sql = "UPDATE player_registry SET reputation_score = ? WHERE uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, newValue);
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Failed to set reputation: " + e.getMessage());
        }
    }

    /**
     * Apply inactivity decay based on last active time.
     *
     * @param playerUuid player id
     * @param lastActive last seen timestamp
     */
    public void applyInactivityDecay(UUID playerUuid, java.time.Instant lastActive) {
        if (lastActive == null) return;
        long days = java.time.Duration.between(lastActive, java.time.Instant.now()).toDays();
        if (days <= 14) return;
        int weeks = (int) ((days - 14) / 7);
        if (weeks <= 0) return;
        adjustReputation(playerUuid, -weeks, "inactivity", "system", null);
    }

    /**
     * Fetch a specific reputation event.
     */
    public synchronized ReputationEvent getEvent(UUID eventId) {
        if (!database.isConnected()) return null;
        String sql = "SELECT timestamp, player_uuid, `change`, reason_summary, source_module, details FROM reputation_events WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.time.Instant ts = rs.getTimestamp(1).toInstant();
                    UUID player = UUID.fromString(rs.getString(2));
                    int change = rs.getInt(3);
                    String reason = rs.getString(4);
                    String source = rs.getString(5);
                    String details = rs.getString(6);
                    return new ReputationEvent(eventId, ts, player, change, reason, source, details);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to fetch reputation event: " + e.getMessage());
        }
        return null;
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
        String eventSql = "INSERT INTO reputation_events (id, timestamp, player_uuid, `change`, reason_summary, source_module, details) VALUES (?, ?, ?, ?, ?, ?, ?)";
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
        if (!database.isConnected()) {
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
        if (!database.isConnected()) {
            return list;
        }
        String sql = "SELECT id, timestamp, `change`, reason_summary, source_module, details FROM reputation_events WHERE player_uuid = ? ORDER BY timestamp";
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
     * Retrieve all player reputations from the registry.
     */
    public synchronized List<PlayerReputation> listReputations() {
        List<PlayerReputation> list = new ArrayList<>();
        if (!database.isConnected()) {
            return list;
        }
        String sql = "SELECT uuid, reputation_score FROM player_registry";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString(1));
                int score = rs.getInt(2);
                list.add(new PlayerReputation(uuid, score));
            }
        } catch (SQLException e) {
            logger.warning("Failed to list reputations: " + e.getMessage());
        }
        return list;
    }

    /**
     * Ensure that a player exists in the registry.
     * This is used on player login to create the initial entry
     * with a random alias and reputation score of 0.
     */
    public synchronized void registerPlayer(UUID playerUuid) {
        if (!database.isConnected()) {
            return;
        }
        try (Connection conn = database.getConnection()) {
            ensurePlayer(conn, playerUuid);
        } catch (SQLException e) {
            logger.severe("Failed to register player: " + e.getMessage());
        }
    }
}
