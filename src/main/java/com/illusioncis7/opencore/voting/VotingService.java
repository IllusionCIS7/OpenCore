package com.illusioncis7.opencore.voting;

import com.illusioncis7.opencore.config.ConfigService;
import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.gpt.GptService;
import com.illusioncis7.opencore.reputation.ReputationService;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class VotingService {
    private final JavaPlugin plugin;
    private final Database database;
    private final GptService gptService;
    private final ConfigService configService;
    private final ReputationService reputationService;
    private final Logger logger;

    public VotingService(JavaPlugin plugin, Database database, GptService gptService,
                         ConfigService configService, ReputationService reputationService) {
        this.plugin = plugin;
        this.database = database;
        this.gptService = gptService;
        this.configService = configService;
        this.reputationService = reputationService;
        this.logger = plugin.getLogger();
    }

    public void submitSuggestion(UUID player, String text) {
        String prompt = "Map the following suggestion to a config parameter ID and value as JSON {id, value}:\n" + text;
        gptService.submitRequest(prompt, player, response -> {
            if (response == null) {
                logger.warning("GPT mapping failed for suggestion: " + text);
                return;
            }
            try {
                JSONObject obj = new JSONObject(response);
                int paramId = obj.getInt("id");
                String value = obj.getString("value");
                storeSuggestion(player, text, paramId, value);
            } catch (Exception e) {
                logger.warning("Invalid GPT mapping response: " + e.getMessage());
            }
        });
    }

    private void storeSuggestion(UUID player, String text, int paramId, String value) {
        if (database.getConnection() == null) return;
        String sql = "INSERT INTO suggestions (player_uuid, parameter_id, new_value, text, created, open) VALUES (?, ?, ?, ?, ?, 1)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setInt(2, paramId);
            ps.setString(3, value);
            ps.setString(4, text);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
            logger.info("Stored suggestion for parameter " + paramId);
        } catch (SQLException e) {
            logger.severe("Failed to store suggestion: " + e.getMessage());
        }
    }

    public List<Suggestion> getOpenSuggestions() {
        List<Suggestion> list = new ArrayList<>();
        if (database.getConnection() == null) return list;
        String sql = "SELECT id, player_uuid, parameter_id, new_value, text, created, open FROM suggestions WHERE open = 1";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt(1);
                UUID player = UUID.fromString(rs.getString(2));
                int paramId = rs.getInt(3);
                String value = rs.getString(4);
                String t = rs.getString(5);
                Instant created = rs.getTimestamp(6).toInstant();
                boolean open = rs.getBoolean(7);
                list.add(new Suggestion(id, player, paramId, value, t, created, open));
            }
        } catch (SQLException e) {
            logger.severe("Failed to fetch suggestions: " + e.getMessage());
        }
        return list;
    }

    public void castVote(UUID player, int suggestionId, boolean yes) {
        if (database.getConnection() == null) return;
        int rep = reputationService.getReputation(player);
        int weight = Math.max(1, rep / 10 + 1);
        String sql = "INSERT INTO votes (suggestion_id, player_uuid, vote_yes, weight) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE vote_yes = VALUES(vote_yes), weight = VALUES(weight)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, suggestionId);
            ps.setString(2, player.toString());
            ps.setBoolean(3, yes);
            ps.setInt(4, weight);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to cast vote: " + e.getMessage());
            return;
        }
        evaluateVotes(suggestionId);
    }

    private void evaluateVotes(int suggestionId) {
        if (database.getConnection() == null) return;
        String sql = "SELECT SUM(CASE WHEN vote_yes THEN weight ELSE 0 END)," +
                " SUM(CASE WHEN vote_yes THEN 0 ELSE weight END), COUNT(*) FROM votes WHERE suggestion_id = ?";
        int yes = 0;
        int no = 0;
        int count = 0;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, suggestionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    yes = rs.getInt(1);
                    no = rs.getInt(2);
                    count = rs.getInt(3);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to tally votes: " + e.getMessage());
            return;
        }
        if (count >= 3 && yes > no) {
            applySuggestion(suggestionId);
        }
    }

    private void applySuggestion(int suggestionId) {
        if (database.getConnection() == null) return;
        String sql = "SELECT parameter_id, new_value FROM suggestions WHERE id = ? AND open = 1";
        int paramId = 0;
        String value = null;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, suggestionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    paramId = rs.getInt(1);
                    value = rs.getString(2);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to load suggestion: " + e.getMessage());
            return;
        }
        if (value == null) return;
        boolean updated = configService.updateParameter(paramId, value);
        if (updated) {
            markClosed(suggestionId);
            logger.info("Applied suggestion " + suggestionId + " updating parameter " + paramId);
        }
    }

    private void markClosed(int suggestionId) {
        String sql = "UPDATE suggestions SET open = 0 WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, suggestionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to close suggestion: " + e.getMessage());
        }
    }
}
