package com.illusioncis7.opencore.voting;

import com.illusioncis7.opencore.config.ConfigService;
import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.gpt.GptService;
import com.illusioncis7.opencore.reputation.ReputationService;
import com.illusioncis7.opencore.rules.RuleService;
import com.illusioncis7.opencore.voting.SuggestionType;
import com.illusioncis7.opencore.plan.PlanHook;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.sql.*;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class VotingService {
    private final JavaPlugin plugin;
    private final Database database;
    private final GptService gptService;
    private final ConfigService configService;
    private final RuleService ruleService;
    private final ReputationService reputationService;
    private final Logger logger;
    private final GptSuggestionClassifier classifier;
    private final PlanHook planHook;

    public VotingService(JavaPlugin plugin, Database database, GptService gptService,
                         ConfigService configService, RuleService ruleService,
                         ReputationService reputationService, PlanHook planHook) {
        this.plugin = plugin;
        this.database = database;
        this.gptService = gptService;
        this.configService = configService;
        this.ruleService = ruleService;
        this.reputationService = reputationService;
        this.planHook = planHook;
        this.logger = plugin.getLogger();
        this.classifier = new GptSuggestionClassifier(gptService, database, logger);
    }

    public void submitSuggestion(UUID player, String text) {
        int id = insertBaseSuggestion(player, text);
        if (id == -1) {
            return;
        }

        classifier.classify(id, text,
                () -> mapConfigChange(id, player, text),
                () -> mapRuleChange(id, player, text));
    }

    private int insertBaseSuggestion(UUID player, String text) {
        if (database.getConnection() == null) return -1;
        String sql = "INSERT INTO suggestions (player_uuid, parameter_id, new_value, text, created, open) VALUES (?, ?, ?, ?, ?, 1)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, player.toString());
            ps.setNull(2, Types.INTEGER);
            ps.setNull(3, Types.VARCHAR);
            ps.setString(4, text);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to store suggestion: " + e.getMessage());
        }
        return -1;
    }

    private void mapConfigChange(int suggestionId, UUID player, String text) {
        // Expected GPT JSON: {"id":<int>,"value":"<new>","name":"<short>"}
        gptService.submitTemplate("suggest_map", text, player, response -> {
            if (response == null) {
                logger.warning("GPT mapping failed for suggestion: " + text);
                storeMappingError(suggestionId, "GPT returned no response");
                return;
            }
            try {
                JSONObject obj = new JSONObject(response);
                int paramId = obj.getInt("id");
                String value = obj.getString("value");
                String name = obj.optString("name", "s" + suggestionId);
                if (!isEditableParam(paramId)) {
                    String error = "Config parameter " + paramId + " not editable";
                    logger.warning(error);
                    storeMappingError(suggestionId, error);
                    return;
                }
                updateMapping(suggestionId, paramId, value, name);
            } catch (Exception e) {
                logger.warning("Invalid GPT mapping response: " + e.getMessage());
                storeMappingError(suggestionId, "Parse error: " + e.getMessage());
            }
        });
    }

    private void mapRuleChange(int suggestionId, UUID player, String text) {
        // Expected GPT JSON: {"id":<int>,"text":"<new>","summary":"...","impact":<int>,"name":"<short>"}
        gptService.submitTemplate("rule_map", text, player, response -> {
            if (response == null) {
                logger.warning("GPT mapping failed for rule suggestion: " + text);
                storeMappingError(suggestionId, "GPT returned no response");
                return;
            }
            try {
                JSONObject obj = new JSONObject(response);
                int ruleId = obj.getInt("id");
                String newText = obj.getString("text");
                String summary = obj.optString("summary", "");
                int impact = obj.optInt("impact", 5);
                String name = obj.optString("name", "s" + suggestionId);
                updateMapping(suggestionId, ruleId, newText, name);
                storeRuleInfo(suggestionId, summary, impact);
            } catch (Exception e) {
                logger.warning("Invalid GPT rule mapping response: " + e.getMessage());
                storeMappingError(suggestionId, "Parse error: " + e.getMessage());
            }
        });
    }

    private void storeRuleInfo(int suggestionId, String summary, int impact) {
        if (database.getConnection() == null) return;
        String sql = "UPDATE suggestions SET gpt_reasoning = ?, gpt_confidence = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, summary);
            ps.setInt(2, impact);
            ps.setInt(3, suggestionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to store rule info: " + e.getMessage());
        }
    }

    private boolean isEditableParam(int paramId) {
        if (database.getConnection() == null) return false;
        String sql = "SELECT editable FROM config_params WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paramId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to validate config parameter: " + e.getMessage());
        }
        return false;
    }

    private void storeMappingError(int suggestionId, String error) {
        if (database.getConnection() == null) return;
        String sql = "UPDATE suggestions SET gpt_reasoning = ?, classified_at = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, error);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setInt(3, suggestionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to log mapping error: " + e.getMessage());
        }
    }

    private void updateMapping(int suggestionId, int paramId, String value, String name) {
        if (database.getConnection() == null) return;
        String sql = "UPDATE suggestions SET parameter_id = ?, new_value = ?, short_name = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paramId);
            ps.setString(2, value);
            ps.setString(3, name);
            ps.setInt(4, suggestionId);
            ps.executeUpdate();
            logger.info("Stored suggestion " + suggestionId + " for target " + paramId);
        } catch (SQLException e) {
            logger.severe("Failed to update suggestion: " + e.getMessage());
        }
    }


    public List<Suggestion> getOpenSuggestions() {
        List<Suggestion> list = new ArrayList<>();
        if (database.getConnection() == null) return list;
        String sql = "SELECT s.id, s.player_uuid, s.parameter_id, s.new_value, s.short_name, s.text, s.created, s.open, " +
                "COALESCE(c.description, r.rule_text) " +
                "FROM suggestions s " +
                "LEFT JOIN config_params c ON s.parameter_id = c.id " +
                "LEFT JOIN server_rules r ON s.parameter_id = r.id " +
                "WHERE s.open = 1";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt(1);
                UUID player = UUID.fromString(rs.getString(2));
                int paramId = rs.getInt(3);
                String value = rs.getString(4);
                String shortName = rs.getString(5);
                String t = rs.getString(6);
                Instant created = rs.getTimestamp(7).toInstant();
                boolean open = rs.getBoolean(8);
                String desc = rs.getString(9);
                list.add(new Suggestion(id, player, paramId, value, shortName, desc, t, created, open));
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

    public VoteStatus getVoteStatus(String shortName) {
        if (database.getConnection() == null) return null;
        String sql = "SELECT id, player_uuid FROM suggestions WHERE short_name = ? AND open = 1";
        int suggestionId = 0;
        UUID initiator = null;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, shortName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    suggestionId = rs.getInt(1);
                    initiator = UUID.fromString(rs.getString(2));
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to load suggestion for status: " + e.getMessage());
            return null;
        }

        VoteStatus status = computeStatus(suggestionId);
        if (status != null) {
            status.shortName = shortName;
            status.initiator = initiator;
        }
        return status;
    }

    private VoteStatus computeStatus(int suggestionId) {
        if (database.getConnection() == null) return null;

        int yesWeight = 0;
        int noWeight = 0;
        int highRepYes = 0;
        String voteSql = "SELECT player_uuid, vote_yes, weight FROM votes WHERE suggestion_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(voteSql)) {
            ps.setInt(1, suggestionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID voter = UUID.fromString(rs.getString(1));
                    boolean yes = rs.getBoolean(2);
                    int weight = rs.getInt(3);
                    if (yes) {
                        yesWeight += weight;
                        if (reputationService.getReputation(voter) >= 50) {
                            highRepYes++;
                        }
                    } else {
                        noWeight += weight;
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to tally votes: " + e.getMessage());
            return null;
        }

        SuggestionType type = SuggestionType.CONFIG_CHANGE;
        int paramId = 0;
        double impact = 5.0;
        String infoSql = "SELECT suggestion_type, parameter_id, gpt_confidence FROM suggestions WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(infoSql)) {
            ps.setInt(1, suggestionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    type = SuggestionType.valueOf(rs.getString(1));
                    paramId = rs.getInt(2);
                    impact = rs.getDouble(3);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to load suggestion info: " + e.getMessage());
            return null;
        }

        if (type == SuggestionType.CONFIG_CHANGE) {
            String impactSql = "SELECT impact_rating FROM config_params WHERE id = ?";
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(impactSql)) {
                ps.setInt(1, paramId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        impact = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                logger.warning("Failed to fetch config impact: " + e.getMessage());
            }
        }

        int totalActiveWeight = 0;
        for (UUID active : planHook.getActivePlayers(Duration.ofDays(30))) {
            int rep = reputationService.getReputation(active);
            totalActiveWeight += Math.max(1, rep / 10 + 1);
        }
        int requiredWeight = Math.max(3, (int) Math.ceil((impact / 10.0) * totalActiveWeight));
        int requiredHighRep = impact >= 8 ? 1 : 0;

        VoteStatus vs = new VoteStatus();
        vs.suggestionId = suggestionId;
        vs.yesWeight = yesWeight;
        vs.noWeight = noWeight;
        vs.highRepYes = highRepYes;
        vs.requiredWeight = requiredWeight;
        vs.requiredHighRep = requiredHighRep;
        return vs;
    }

    private void evaluateVotes(int suggestionId) {
        if (database.getConnection() == null) return;

        int yesWeight = 0;
        int noWeight = 0;
        int highRepYes = 0;
        String voteSql = "SELECT player_uuid, vote_yes, weight FROM votes WHERE suggestion_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(voteSql)) {
            ps.setInt(1, suggestionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID voter = UUID.fromString(rs.getString(1));
                    boolean yes = rs.getBoolean(2);
                    int weight = rs.getInt(3);
                    if (yes) {
                        yesWeight += weight;
                        if (reputationService.getReputation(voter) >= 50) {
                            highRepYes++;
                        }
                    } else {
                        noWeight += weight;
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to tally votes: " + e.getMessage());
            return;
        }

        SuggestionType type = SuggestionType.CONFIG_CHANGE;
        int paramId = 0;
        double impact = 5.0;
        String infoSql = "SELECT suggestion_type, parameter_id, gpt_confidence FROM suggestions WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(infoSql)) {
            ps.setInt(1, suggestionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    type = SuggestionType.valueOf(rs.getString(1));
                    paramId = rs.getInt(2);
                    impact = rs.getDouble(3);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to load suggestion info: " + e.getMessage());
            return;
        }

        if (type == SuggestionType.CONFIG_CHANGE) {
            String impactSql = "SELECT impact_rating FROM config_params WHERE id = ?";
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(impactSql)) {
                ps.setInt(1, paramId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        impact = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                logger.warning("Failed to fetch config impact: " + e.getMessage());
            }
        }

        int totalActiveWeight = 0;
        for (UUID active : planHook.getActivePlayers(Duration.ofDays(30))) {
            int rep = reputationService.getReputation(active);
            totalActiveWeight += Math.max(1, rep / 10 + 1);
        }
        int requiredWeight = Math.max(3, (int) Math.ceil((impact / 10.0) * totalActiveWeight));
        int requiredHighRep = impact >= 8 ? 1 : 0;

        if (yesWeight > noWeight && yesWeight >= requiredWeight && highRepYes >= requiredHighRep) {
            applySuggestion(suggestionId);
        }
    }

    private void applySuggestion(int suggestionId) {
        if (database.getConnection() == null) return;
        String sql = "SELECT suggestion_type, parameter_id, new_value, player_uuid FROM suggestions WHERE id = ? AND open = 1";
        SuggestionType type = null;
        int paramId = 0;
        String value = null;
        UUID player = null;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, suggestionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    type = SuggestionType.valueOf(rs.getString(1));
                    paramId = rs.getInt(2);
                    value = rs.getString(3);
                    player = UUID.fromString(rs.getString(4));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to load suggestion: " + e.getMessage());
            return;
        }
        if (value == null) return;
        boolean updated = false;
        if (type == SuggestionType.CONFIG_CHANGE) {
            updated = configService.updateParameter(paramId, value);
        } else if (type == SuggestionType.RULE_CHANGE) {
            updated = ruleService.updateRule(paramId, value, player, suggestionId);
        }
        if (updated) {
            markClosed(suggestionId);
            logger.info("Applied suggestion " + suggestionId + " updating target " + paramId);
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
