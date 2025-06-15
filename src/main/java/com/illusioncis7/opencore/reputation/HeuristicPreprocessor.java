package com.illusioncis7.opencore.reputation;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replace player name references in chat messages with their alias UUIDs.
 */
public class HeuristicPreprocessor {
    private final Database database;
    private final Logger logger;

    public HeuristicPreprocessor(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /**
     * Apply heuristic pseudonymization for a list of chat messages.
     * Updates the underlying chat_log table if replacements are made.
     */
    public List<ChatAnalyzerTask.ChatMessage> preprocess(List<ChatAnalyzerTask.ChatMessage> chatLog) {
        Map<String, String> map = buildAliasMap(chatLog);
        List<ChatAnalyzerTask.ChatMessage> result = new ArrayList<>();
        for (ChatAnalyzerTask.ChatMessage msg : chatLog) {
            String newText = replaceAliases(msg.message, map);
            if (!newText.equals(msg.message)) {
                updateMessage(msg.id, newText);
                logger.fine("HeuristicPreprocessor changed chat " + msg.id + " from '" + msg.message + "' to '" + newText + "'");
            }
            result.add(new ChatAnalyzerTask.ChatMessage(msg.id, msg.uuid, msg.aliasId, newText, msg.time));
        }
        return result;
    }

    private Map<String, String> buildAliasMap(List<ChatAnalyzerTask.ChatMessage> chatLog) {
        Map<UUID, String> playerAliases = new HashMap<>();

        // gather alias IDs for all players in the provided messages
        for (ChatAnalyzerTask.ChatMessage msg : chatLog) {
            if (msg.aliasId != null && !msg.aliasId.isEmpty()) {
                playerAliases.put(msg.uuid, msg.aliasId);
            }
        }

        // fetch aliases from DB if missing
        if (database.isConnected()) {
            for (ChatAnalyzerTask.ChatMessage msg : chatLog) {
                if (!playerAliases.containsKey(msg.uuid)) {
                    String alias = fetchAlias(msg.uuid);
                    if (alias != null) {
                        playerAliases.put(msg.uuid, alias);
                    }
                }
            }
        }

        Map<String, String> map = new HashMap<>();
        for (Map.Entry<UUID, String> entry : playerAliases.entrySet()) {
            UUID uuid = entry.getKey();
            String alias = entry.getValue();
            if (alias == null) continue;
            String id = alias.trim();
            map.put(id.toLowerCase(Locale.ROOT), id);

            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            if (off.getName() != null) {
                String name = off.getName().toLowerCase(Locale.ROOT);
                map.put(name, id);

                // simple variation: remove trailing digits and underscores
                String variant = name.replaceAll("[_0-9]+$", "");
                if (!variant.equals(name) && !variant.isEmpty()) {
                    map.put(variant, id);
                }
            }
        }
        return map;
    }

    private String fetchAlias(UUID uuid) {
        if (!database.isConnected()) return null;
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

    private String replaceAliases(String text, Map<String, String> aliases) {
        String result = text;
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            String name = e.getKey();
            String id = e.getValue();
            Pattern p = Pattern.compile("\\b" + Pattern.quote(name) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(result);
            result = m.replaceAll(id);
        }
        return result;
    }

    private void updateMessage(long id, String newText) {
        if (!database.isConnected()) return;
        String sql = "UPDATE chat_log SET message = ?, message_time = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newText);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("Failed to update chat message: " + e.getMessage());
        }
    }
}
