package com.illusioncis7.opencore.reputation;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;
import java.util.logging.Logger;

/**
 * Service providing access to reputation flags used for chat analysis.
 */
public class ChatReputationFlagService {
    private final JavaPlugin plugin;
    private final Database database;
    private final Logger logger;

    public ChatReputationFlagService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.logger = plugin.getLogger();
        initTable();
    }

    private void initTable() {
        if (database.getConnection() == null) return;
        String sql = "CREATE TABLE IF NOT EXISTS chat_reputation_flags (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "code VARCHAR(50) NOT NULL," +
                "description TEXT," +
                "min_change INT NOT NULL," +
                "max_change INT NOT NULL," +
                "active BOOLEAN DEFAULT 1," +
                "last_updated TIMESTAMP NOT NULL" +
                ")";
        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (Exception e) {
            logger.severe("Failed to create chat_reputation_flags: " + e.getMessage());
        }
    }

    /** Retrieve all flags. */
    public List<ReputationFlag> listFlags() {
        return loadFlags(false);
    }

    /** Retrieve only active flags. */
    public List<ReputationFlag> getActiveFlags() {
        return loadFlags(true);
    }

    private List<ReputationFlag> loadFlags(boolean activeOnly) {
        List<ReputationFlag> list = new ArrayList<>();
        if (database.getConnection() == null) return list;
        String sql = "SELECT code, description, min_change, max_change, active FROM chat_reputation_flags";
        if (activeOnly) {
            sql += " WHERE active = 1";
        }
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString(1);
                String desc = rs.getString(2);
                int min = rs.getInt(3);
                int max = rs.getInt(4);
                boolean act = rs.getBoolean(5);
                list.add(new ReputationFlag(code, desc, min, max, act));
            }
        } catch (Exception e) {
            logger.warning("Failed to load reputation flags: " + e.getMessage());
        }
        return list;
    }

    /** Find a flag by code. */
    public Optional<ReputationFlag> getFlagByCode(String code) {
        if (database.getConnection() == null) return Optional.empty();
        String sql = "SELECT code, description, min_change, max_change, active FROM chat_reputation_flags WHERE code = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ReputationFlag(
                            rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getBoolean(5)));
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load reputation flag: " + e.getMessage());
        }
        return Optional.empty();
    }

    /** Map of active flags by code. */
    public Map<String, ReputationFlag> getFlagMap() {
        Map<String, ReputationFlag> map = new HashMap<>();
        for (ReputationFlag f : getActiveFlags()) {
            map.put(f.code, f);
        }
        return map;
    }

    /** Update min/max values for a flag. */
    public boolean setRange(String code, int min, int max) {
        if (database.getConnection() == null) return false;
        String sql = "UPDATE chat_reputation_flags SET min_change = ?, max_change = ?, last_updated = ? WHERE code = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, min);
            ps.setInt(2, max);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setString(4, code);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            logger.warning("Failed to update reputation flag: " + e.getMessage());
        }
        return false;
    }
}
