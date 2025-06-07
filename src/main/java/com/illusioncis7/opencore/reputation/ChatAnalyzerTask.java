package com.illusioncis7.opencore.reputation;

import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.gpt.GptService;
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
    private final Logger logger;
    private Instant lastRun;

    public ChatAnalyzerTask(Database database, GptService gptService, ReputationService reputationService, Logger logger) {
        this.database = database;
        this.gptService = gptService;
        this.reputationService = reputationService;
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
        gptService.submitTemplate("chat_analysis", data.toString(), null, response -> {
            if (response == null || response.isEmpty()) {
                return;
            }
            if (!com.illusioncis7.opencore.gpt.GptSchemas.validate("chat_analysis", response)) {
                logger.warning("Invalid chat_analysis schema for GPT response");
                return;
            }
            try {
                JSONArray arr = new JSONArray(response);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String alias = obj.getString("alias_id");
                    UUID playerUuid = resolveAlias(alias);
                    if (playerUuid == null) continue;

                    int change = 0;
                    for (String key : obj.keySet()) {
                        if ("alias_id".equals(key) || "reason_summary".equals(key)) continue;
                        if (reputationService.hasRange(key)) {
                            double val = obj.optDouble(key, 0.0);
                            change += reputationService.computeChange(key, val);
                        }
                    }
                    if (change != 0) {
                        String reason = obj.optString("reason_summary", "chat analysis");
                        reputationService.adjustReputation(playerUuid, change, reason, "chat", obj.toString());
                    }
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
