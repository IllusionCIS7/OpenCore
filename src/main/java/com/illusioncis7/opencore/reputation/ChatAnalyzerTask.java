package com.illusioncis7.opencore.reputation;

import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.gpt.GptService;
import com.illusioncis7.opencore.rules.RuleService;
import java.util.Map;

import com.illusioncis7.opencore.reputation.ChatReputationFlagService;
import com.illusioncis7.opencore.reputation.ReputationFlag;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class ChatAnalyzerTask extends BukkitRunnable {
    private final Database database;
    private final GptService gptService;
    private final ReputationService reputationService;
    private final ChatReputationFlagService flagService;
    private final RuleService ruleService;
    private final Logger logger;
    private Instant lastRun;

    public ChatAnalyzerTask(Database database, GptService gptService, ReputationService reputationService,
                           ChatReputationFlagService flagService, RuleService ruleService, Logger logger) {
        this.database = database;
        this.gptService = gptService;
        this.reputationService = reputationService;
        this.flagService = flagService;
        this.ruleService = ruleService;
        this.logger = logger;
        this.lastRun = Instant.now().minusSeconds(45 * 60);
    }

    @Override
    public void run() {
        Instant since = lastRun;
        lastRun = Instant.now();
        List<ChatMessage> messages = loadMessages(since);
        if (messages.isEmpty()) {
            return;
        }
        StringBuilder data = new StringBuilder();
        for (ChatMessage msg : messages) {
            data.append("[" + msg.id + "] " + msg.aliasId + ": " + msg.message + "\n");
        }
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("message", data.toString());
        vars.put("rules", joinRules());
        vars.put("flags", formatFlags());
        gptService.submitPolicyRequest("chat_analysis", vars, null, response -> {
            if (response == null || response.isEmpty()) {
                return;
            }
            if (!com.illusioncis7.opencore.gpt.GptSchemas.validate("chat_analysis", response)) {
                logger.warning("Invalid chat_analysis schema for GPT response");
                return;
            }
            try {
                JSONObject obj = new JSONObject(response);
                JSONArray arr = obj.optJSONArray("evaluations");
                if (arr == null) return;
                Map<String, ReputationFlag> map = flagService.getFlagMap();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    String alias = item.getString("player");
                    String flag = item.getString("flag");
                    int change = item.getInt("change");
                    String reason = item.optString("reason", "chat analysis");
                    ReputationFlag def = map.get(flag);
                    if (def == null) {
                        logger.warning("Unknown flag " + flag);
                        continue;
                    }
                    if (change < def.minChange || change > def.maxChange) {
                        logger.warning("Change out of bounds for flag " + flag + ": " + change);
                        continue;
                    }
                    UUID playerUuid = resolveAlias(alias);
                    if (playerUuid == null) continue;
                    reputationService.adjustReputation(playerUuid, change, reason, "chat", item.toString());
                }
            } catch (Exception e) {
                logger.warning("Failed to parse GPT chat analysis: " + e.getMessage());
            }
        });
    }

    private UUID resolveAlias(String alias) {
        if (database.getConnection() == null) return null;
        String sql = "SELECT uuid FROM player_registry WHERE alias_id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alias);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString(1));
                }
            }
        } catch (Exception e) {
            logger.warning("Alias lookup failed: " + e.getMessage());
        }
        return null;
    }

    private String joinRules() {
        StringBuilder sb = new StringBuilder();
        for (com.illusioncis7.opencore.rules.Rule r : ruleService.getRules()) {
            sb.append(r.text).append("\n");
        }
        return sb.toString();
    }

    private String formatFlags() {
        StringBuilder sb = new StringBuilder();
        for (ReputationFlag f : flagService.getActiveFlags()) {
            sb.append("[").append(f.code).append("]: ")
                    .append(f.minChange).append(" bis ")
                    .append(f.maxChange).append(" Punkte, ")
                    .append(f.description).append("\n");
        }
        return sb.toString();
    }

    private List<ChatMessage> loadMessages(Instant since) {
        List<ChatMessage> list = new ArrayList<>();
        if (database.getConnection() == null) {
            return list;
        }
        String sql = "SELECT id, player_uuid, message, message_time FROM chat_log WHERE message_time > ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(since));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    UUID player = UUID.fromString(rs.getString(2));
                    String message = rs.getString(3);
                    Timestamp time = rs.getTimestamp(4);
                    String alias = getAlias(player);
                    list.add(new ChatMessage(id, player, alias, message, time.toInstant()));
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to load chat messages: " + e.getMessage());
        }
        return list;
    }

    private String getAlias(UUID uuid) {
        if (database.getConnection() == null) return null;
        String sql = "SELECT alias_id FROM player_registry WHERE uuid = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to fetch alias: " + e.getMessage());
        }
        return null;
    }

    private static class ChatMessage {
        final long id;
        final UUID uuid;
        final String aliasId;
        final String message;
        final Instant time;

        ChatMessage(long id, UUID uuid, String aliasId, String message, Instant time) {
            this.id = id;
            this.uuid = uuid;
            this.aliasId = aliasId;
            this.message = message;
            this.time = time;
        }
    }
}
