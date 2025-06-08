package com.illusioncis7.opencore.voting;

import com.illusioncis7.opencore.config.ConfigService;
import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.gpt.GptService;
import com.illusioncis7.opencore.reputation.ReputationService;
import com.illusioncis7.opencore.rules.RuleService;
import com.illusioncis7.opencore.voting.SuggestionType;
import com.illusioncis7.opencore.plan.PlanHook;
import com.illusioncis7.opencore.OpenCore;
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
    private String webhookUrl;
    private int minVoteRep = -5;
    private int maxRep = 500;
    private double negWeightMin = 0.0;
    private double negWeightMax = 1.0;
    private double posWeightMin = 1.0;
    private double posWeightMax = 20.0;
    private int barLength = 20;
    private Duration voteLifetime = Duration.ofDays(2);
    private String colorYes = org.bukkit.ChatColor.GREEN.toString();
    private String colorNo = org.bukkit.ChatColor.RED.toString();
    private String colorMarker = org.bukkit.ChatColor.YELLOW.toString();
    private String colorFrame = org.bukkit.ChatColor.GRAY.toString();
    private String voteBroadcast;

    public static class VoteWeights {
        public final double yesWeight;
        public final double noWeight;
        public final double requiredWeight;

        public VoteWeights(double yesWeight, double noWeight, double requiredWeight) {
            this.yesWeight = yesWeight;
            this.noWeight = noWeight;
            this.requiredWeight = requiredWeight;
        }
    }

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
        this.classifier = new GptSuggestionClassifier(gptService, database, ruleService, logger);
        loadConfig();
    }

    private void loadConfig() {
        java.io.File cfgFile = new java.io.File(plugin.getDataFolder(), "voting.yml");
        org.bukkit.configuration.file.FileConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfgFile);
        this.webhookUrl = cfg.getString("webhook-url", "");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            logger.info("Discord webhook disabled (no webhook-url configured)");
        } else {
            logger.info("Discord webhook enabled");
        }
        minVoteRep = cfg.getInt("min-reputation", -5);
        maxRep = cfg.getInt("max-reputation", 500);
        negWeightMin = cfg.getDouble("negative-weight-min", 0.0);
        negWeightMax = cfg.getDouble("negative-weight-max", 1.0);
        posWeightMin = cfg.getDouble("positive-weight-min", 1.0);
        posWeightMax = cfg.getDouble("positive-weight-max", 20.0);
        barLength = cfg.getInt("bar-length", 20);
        voteLifetime = java.time.Duration.ofMinutes(cfg.getInt("duration-minutes", 2880));
        voteBroadcast = cfg.getString("broadcast-message", "vote.start");
        org.bukkit.configuration.ConfigurationSection col = cfg.getConfigurationSection("colors");
        if (col != null) {
            colorYes = org.bukkit.ChatColor.translateAlternateColorCodes('&', col.getString("yes", "&a"));
            colorNo = org.bukkit.ChatColor.translateAlternateColorCodes('&', col.getString("no", "&c"));
            colorMarker = org.bukkit.ChatColor.translateAlternateColorCodes('&', col.getString("marker", "&e"));
            colorFrame = org.bukkit.ChatColor.translateAlternateColorCodes('&', col.getString("frame", "&7"));
        }
    }

    public double computeVoteWeight(int reputation) {
        if (reputation < minVoteRep) {
            return 0.0;
        }
        if (reputation < 0) {
            double ratio = (double) (reputation - minVoteRep) / (0.0 - minVoteRep);
            return negWeightMin + ratio * (negWeightMax - negWeightMin);
        }
        int capped = Math.min(reputation, maxRep);
        double ratio = capped / (double) Math.max(1, maxRep);
        return posWeightMin + ratio * (posWeightMax - posWeightMin);
    }

    public String buildVoteBar(double yes, double no) {
        double total = yes + no;
        double ratio = total > 0 ? yes / total : 0.5;
        int markerPos = (int) Math.round(ratio * barLength);
        StringBuilder sb = new StringBuilder();
        sb.append(colorNo).append("Ablehnung ");
        sb.append(colorFrame).append("<");
        for (int i = 0; i < barLength; i++) {
            if (i == markerPos) sb.append(colorMarker).append("|");
            if (i < markerPos) sb.append(colorYes).append("I");
            else sb.append(colorNo).append("I");
        }
        if (markerPos == barLength) sb.append(colorMarker).append("|");
        sb.append(colorFrame).append(">");
        sb.append(colorYes).append(" Zustimmung");
        return sb.toString();
    }

    public int submitSuggestion(UUID player, String text) {
        int id = insertBaseSuggestion(player, text);
        if (id == -1) {
            return -1;
        }

        int delta = reputationService.computeChange("suggestion-submitted", 1.0);
        if (delta != 0) {
            reputationService.adjustReputation(player, delta, "suggestion submitted", "suggestion", String.valueOf(id));
        }

        classifier.classify(id, text,
                () -> mapConfigChange(id, player, text),
                () -> mapRuleChange(id, player, text),
                type -> {
                    postWebhook(id, type, text);
                    if (type != SuggestionType.CONFIG_CHANGE && type != SuggestionType.RULE_CHANGE) {
                        markOpen(id);
                        broadcastStart(id);
                    }
                });
        return id;
    }

    private int insertBaseSuggestion(UUID player, String text) {
        if (!database.isConnected()) return -1;
        String sql = "INSERT INTO suggestions (player_uuid, parameter_id, new_value, text, created, open) VALUES (?, ?, ?, ?, ?, 0)";
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
        if (countParameterChanges(text) > 5) {
            storeMappingError(suggestionId, "Too many parameter changes (>5)");
            markClosed(suggestionId);
            return;
        }
        gptService.submitTemplate("suggest_map", text, player, response -> {
            if (response == null) {
                logger.warning("GPT mapping failed for suggestion: " + text);
                storeMappingError(suggestionId, "GPT returned no response");
                return;
            }
            if (!com.illusioncis7.opencore.gpt.GptSchemas.validate("suggest_map", response)) {
                logger.warning("Invalid GPT mapping schema for suggestion " + suggestionId);
                storeMappingError(suggestionId, "Invalid schema");
                return;
            }
            try {
                JSONObject obj = new JSONObject(response);
                int paramId = obj.getInt("id");
                String value = obj.getString("value");
                if (!isEditableParam(paramId)) {
                    String error = "Config parameter " + paramId + " not editable";
                    logger.warning(error);
                    storeMappingError(suggestionId, error);
                    return;
                }
                updateMapping(suggestionId, paramId, value);
                markOpen(suggestionId);
                broadcastStart(suggestionId);
            } catch (Exception e) {
                logger.warning("Invalid GPT mapping response: " + e.getMessage());
                storeMappingError(suggestionId, "Parse error: " + e.getMessage());
            }
        });
    }

    private void mapRuleChange(int suggestionId, UUID player, String text) {
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("s", text);
        vars.put("rules", joinRules());
        gptService.submitPolicyRequest("rule_map", vars, player, response -> {
            if (response == null) {
                logger.warning("GPT mapping failed for rule suggestion: " + text);
                storeMappingError(suggestionId, "GPT returned no response");
                return;
            }
            if (!com.illusioncis7.opencore.gpt.GptSchemas.validate("rule_map", response)) {
                logger.warning("Invalid GPT rule_map schema for suggestion " + suggestionId);
                storeMappingError(suggestionId, "Invalid schema");
                return;
            }
            try {
                JSONObject obj = new JSONObject(response);
                int ruleId = obj.getInt("id");
                String newText = obj.getString("text");
                String summary = obj.optString("summary", "");
                int impact = obj.optInt("impact", 5);
                updateMapping(suggestionId, ruleId, newText);
                storeRuleInfo(suggestionId, summary, impact);
                markOpen(suggestionId);
                broadcastStart(suggestionId);
            } catch (Exception e) {
                logger.warning("Invalid GPT rule mapping response: " + e.getMessage());
                storeMappingError(suggestionId, "Parse error: " + e.getMessage());
            }
        });
    }

    private void storeRuleInfo(int suggestionId, String summary, int impact) {
        if (!database.isConnected()) return;
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

    private String joinRules() {
        StringBuilder sb = new StringBuilder();
        for (com.illusioncis7.opencore.rules.Rule r : ruleService.getRules()) {
            sb.append(r.text).append("\n");
        }
        return sb.toString();
    }

    private boolean isEditableParam(int paramId) {
        if (!database.isConnected()) return false;
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
        if (!database.isConnected()) return;
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

    private void updateMapping(int suggestionId, int paramId, String value) {
        if (!database.isConnected()) return;
        String sql = "UPDATE suggestions SET parameter_id = ?, new_value = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paramId);
            ps.setString(2, value);
            ps.setInt(3, suggestionId);
            ps.executeUpdate();
            logger.info("Stored suggestion " + suggestionId + " for target " + paramId);
        } catch (SQLException e) {
            logger.severe("Failed to update suggestion: " + e.getMessage());
        }
    }


    public List<Suggestion> getOpenSuggestions() {
        List<Suggestion> list = new ArrayList<>();
        if (!database.isConnected()) return list;
        String sql = "SELECT s.id, s.player_uuid, s.parameter_id, s.new_value, s.text, s.created, s.open, " +
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
                String t = rs.getString(5);
                Instant created = rs.getTimestamp(6).toInstant();
                boolean open = rs.getBoolean(7);
                String desc = rs.getString(8);
                list.add(new Suggestion(id, player, paramId, value, desc, t, created, open));
            }
        } catch (SQLException e) {
            logger.severe("Failed to fetch suggestions: " + e.getMessage());
        }
        return list;
    }

    public boolean isSuggestionOpen(int suggestionId) {
        if (!database.isConnected()) return false;
        String sql = "SELECT open FROM suggestions WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, suggestionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to check suggestion state: " + e.getMessage());
        }
        return false;
    }

    public List<Suggestion> getClosedSuggestions() {
        List<Suggestion> list = new ArrayList<>();
        if (!database.isConnected()) return list;
        String sql = "SELECT s.id, s.player_uuid, s.parameter_id, s.new_value, s.text, s.created, s.open, " +
                "COALESCE(c.description, r.rule_text) " +
                "FROM suggestions s " +
                "LEFT JOIN config_params c ON s.parameter_id = c.id " +
                "LEFT JOIN server_rules r ON s.parameter_id = r.id " +
                "WHERE s.open = 0";
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
                String desc = rs.getString(8);
                list.add(new Suggestion(id, player, paramId, value, desc, t, created, open));
            }
        } catch (SQLException e) {
            logger.severe("Failed to fetch closed suggestions: " + e.getMessage());
        }
        return list;
    }

    public VoteWeights getVoteWeights(int suggestionId) {
        if (!database.isConnected()) return new VoteWeights(0, 0, 0);

        double yesWeight = 0.0;
        double noWeight = 0.0;
        int highRepYes = 0;
        String voteSql = "SELECT player_uuid, vote_yes, weight FROM votes WHERE suggestion_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(voteSql)) {
            ps.setInt(1, suggestionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID voter = UUID.fromString(rs.getString(1));
                    boolean yes = rs.getBoolean(2);
                    double weight = rs.getDouble(3);
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
            return new VoteWeights(yesWeight, noWeight, 0);
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

        double totalActiveWeight = 0.0;
        for (UUID active : planHook.getActivePlayers(Duration.ofDays(30))) {
            int rep = reputationService.getReputation(active);
            totalActiveWeight += computeVoteWeight(rep);
        }
        double requiredWeight = Math.max(3.0, Math.ceil((impact / 10.0) * totalActiveWeight));
        return new VoteWeights(yesWeight, noWeight, requiredWeight);
    }

    public boolean castVote(UUID player, int suggestionId, boolean yes) {
        if (!database.isConnected()) return false;
        if (!isSuggestionOpen(suggestionId)) {
            return false;
        }
        int rep = reputationService.getReputation(player);
        double weight = computeVoteWeight(rep);
        if (weight <= 0.0) {
            return false;
        }
        String sql = "INSERT INTO votes (suggestion_id, player_uuid, vote_yes, weight) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE vote_yes = VALUES(vote_yes), weight = VALUES(weight)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, suggestionId);
            ps.setString(2, player.toString());
            ps.setBoolean(3, yes);
            ps.setDouble(4, weight);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to cast vote: " + e.getMessage());
            return false;
        }
        evaluateVotes(suggestionId);
        return true;
    }

    public boolean hasPlayerVoted(UUID player, int suggestionId) {
        if (!database.isConnected()) return false;
        String sql = "SELECT 1 FROM votes WHERE suggestion_id = ? AND player_uuid = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, suggestionId);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warning("Failed to check existing vote: " + e.getMessage());
        }
        return false;
    }

    public boolean isSimilarSuggestionRecent(String text, Duration timeframe, int maxDistance) {
        if (!database.isConnected()) return false;
        String sql = "SELECT text FROM suggestions WHERE created >= ?";
        Instant since = Instant.now().minus(timeframe);
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(since));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String other = rs.getString(1);
                    if (levenshtein(text.toLowerCase(), other.toLowerCase()) < maxDistance) {
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to check similar suggestions: " + e.getMessage());
        }
        return false;
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    private int countParameterChanges(String text) {
        if (text == null || text.isEmpty()) return 0;
        String[] parts = text.split("[;\n]");
        int c = 0;
        for (String p : parts) {
            if (!p.trim().isEmpty()) c++;
        }
        return c;
    }

    public void checkOpenSuggestions() {
        for (Suggestion s : getOpenSuggestions()) {
            evaluateVotes(s.id);
            if (!isSuggestionOpen(s.id)) {
                continue;
            }
            if (Duration.between(s.created, Instant.now()).compareTo(voteLifetime) >= 0) {
                VoteWeights w = getVoteWeights(s.id);
                if (w.yesWeight > w.noWeight) {
                    applySuggestion(s.id);
                } else {
                    markClosed(s.id);
                }
            }
        }
    }

    private void evaluateVotes(int suggestionId) {
        if (!database.isConnected()) return;

        double yesWeight = 0.0;
        double noWeight = 0.0;
        int highRepYes = 0;
        String voteSql = "SELECT player_uuid, vote_yes, weight FROM votes WHERE suggestion_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(voteSql)) {
            ps.setInt(1, suggestionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID voter = UUID.fromString(rs.getString(1));
                    boolean yes = rs.getBoolean(2);
                    double weight = rs.getDouble(3);
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

        double totalActiveWeight = 0.0;
        for (UUID active : planHook.getActivePlayers(Duration.ofDays(30))) {
            int rep = reputationService.getReputation(active);
            totalActiveWeight += computeVoteWeight(rep);
        }
        double requiredWeight = Math.max(3.0, Math.ceil((impact / 10.0) * totalActiveWeight));
        int requiredHighRep = impact >= 8 ? 1 : 0;

        if (yesWeight > noWeight && yesWeight >= requiredWeight && highRepYes >= requiredHighRep) {
            applySuggestion(suggestionId);
        }
    }

    private void applySuggestion(int suggestionId) {
        if (!database.isConnected()) return;
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
            updated = configService.updateParameter(paramId, value, player);
        } else if (type == SuggestionType.RULE_CHANGE) {
            updated = ruleService.updateRule(paramId, value, player, suggestionId);
        }
        if (updated) {
            markClosed(suggestionId);
            logger.info("Applied suggestion " + suggestionId + " updating target " + paramId);
            int delta = reputationService.computeChange("suggestion-accepted", 1.0);
            if (delta != 0 && player != null) {
                reputationService.adjustReputation(player, delta, "suggestion accepted", "suggestion", String.valueOf(suggestionId));
            }

            if (player != null) {
                int count = getImplementedCount(player);
                if (count > 1) {
                    int extra = reputationService.computeChange("suggestion-multiple-implemented", 1.0);
                    if (extra != 0) {
                        reputationService.adjustReputation(player, extra, "multiple suggestions implemented", "suggestion", String.valueOf(count));
                    }
                }
            }
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

    private void markOpen(int suggestionId) {
        String sql = "UPDATE suggestions SET open = 1 WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, suggestionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to open suggestion: " + e.getMessage());
        }
    }

    private void broadcastStart(int suggestionId) {
        if (voteBroadcast == null || voteBroadcast.isEmpty()) {
            return;
        }
        Suggestion s = getSuggestion(suggestionId);
        if (s == null) return;
        String title = s.newValue != null && !s.newValue.isEmpty()
                ? s.newValue
                : (s.description != null && !s.description.isEmpty() ? s.description : s.text);
        java.util.Map<String, String> ph = new java.util.HashMap<>();
        ph.put("id", String.valueOf(s.id));
        ph.put("title", title);
        for (String line : com.illusioncis7.opencore.OpenCore.getInstance().getMessageService().getMessage(voteBroadcast, ph)) {
            plugin.getServer().broadcastMessage(line);
        }
    }

    private Suggestion getSuggestion(int id) {
        if (!database.isConnected()) return null;
        String sql = "SELECT s.id, s.player_uuid, s.parameter_id, s.new_value, s.text, s.created, s.open, " +
                "COALESCE(c.description, r.rule_text) " +
                "FROM suggestions s " +
                "LEFT JOIN config_params c ON s.parameter_id = c.id " +
                "LEFT JOIN server_rules r ON s.parameter_id = r.id " +
                "WHERE s.id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int sid = rs.getInt(1);
                    java.util.UUID player = java.util.UUID.fromString(rs.getString(2));
                    int paramId = rs.getInt(3);
                    String val = rs.getString(4);
                    String t = rs.getString(5);
                    java.time.Instant created = rs.getTimestamp(6).toInstant();
                    boolean open = rs.getBoolean(7);
                    String desc = rs.getString(8);
                    return new Suggestion(sid, player, paramId, val, desc, t, created, open);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to load suggestion: " + e.getMessage());
        }
        return null;
    }

    private int getImplementedCount(UUID player) {
        if (!database.isConnected()) return 0;
        String sql = "SELECT COUNT(*) FROM suggestions WHERE player_uuid = ? AND open = 0";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to count implementations: " + e.getMessage());
        }
        return 0;
    }

    private void postWebhook(int id, SuggestionType type, String text) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        try {
            org.json.JSONObject embed = new org.json.JSONObject();
            embed.put("title", "Suggestion #" + id);
            embed.put("description", text);
            embed.put("footer", new org.json.JSONObject().put("text", type.name()));
            org.json.JSONArray arr = new org.json.JSONArray();
            arr.put(embed);
            org.json.JSONObject payload = new org.json.JSONObject();
            payload.put("embeds", arr);

            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            java.net.http.HttpClient.newHttpClient().sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            logger.warning("Failed to send webhook: " + e.getMessage());
        }
    }

    public int getMinVoteReputation() {
        return minVoteRep;
    }

    public Duration getVoteLifetime() {
        return voteLifetime;
    }

    public long getRemainingMinutes(Instant created) {
        Duration age = Duration.between(created, Instant.now());
        Duration rem = voteLifetime.minus(age);
        if (rem.isNegative()) return 0;
        return rem.toMinutes();
    }
}
