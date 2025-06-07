package com.illusioncis7.opencore.rules;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class RuleService {
    private final JavaPlugin plugin;
    private final Database database;
    private final Logger logger;

    public RuleService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.logger = plugin.getLogger();
    }

    public List<Rule> getRules() {
        List<Rule> list = new ArrayList<>();
        if (database.getConnection() == null) return list;
        String sql = "SELECT id, rule_text, category FROM server_rules ORDER BY id";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Rule(rs.getInt(1), rs.getString(2), rs.getString(3))); 
            }
        } catch (SQLException e) {
            logger.warning("Failed to load rules: " + e.getMessage());
        }
        return list;
    }

    public Rule getRule(int id) {
        if (database.getConnection() == null) return null;
        String sql = "SELECT id, rule_text, category FROM server_rules WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Rule(rs.getInt(1), rs.getString(2), rs.getString(3));
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to load rule: " + e.getMessage());
        }
        return null;
    }

    public boolean updateRule(int id, String newText, UUID changedBy, Integer suggestionId) {
        if (database.getConnection() == null) return false;
        String selectSql = "SELECT rule_text FROM server_rules WHERE id = ?";
        String updateSql = "UPDATE server_rules SET rule_text = ? WHERE id = ?";
        String logSql = "INSERT INTO rule_changes (rule_id, old_text, new_text, changed_at, changed_by, suggestion_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = database.getConnection()) {
            String oldText = null;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        oldText = rs.getString(1);
                    } else {
                        return false;
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, newText);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(logSql)) {
                ps.setInt(1, id);
                ps.setString(2, oldText);
                ps.setString(3, newText);
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
                if (changedBy != null) {
                    ps.setString(5, changedBy.toString());
                } else {
                    ps.setNull(5, Types.VARCHAR);
                }
                if (suggestionId != null) {
                    ps.setInt(6, suggestionId);
                } else {
                    ps.setNull(6, Types.INTEGER);
                }
                ps.executeUpdate();
            }
            logger.info("Rule " + id + " updated");
            return true;
        } catch (SQLException e) {
            logger.severe("Failed to update rule: " + e.getMessage());
            return false;
        }
    }
}
