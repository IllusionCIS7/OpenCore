package com.illusioncis7.opencore.web;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SuggestionCommentService {
    public static class Comment {
        public final int id;
        public final int suggestionId;
        public final UUID player;
        public final String content;
        public final Instant created;
        public Comment(int id, int suggestionId, UUID player, String content, Instant created) {
            this.id = id;
            this.suggestionId = suggestionId;
            this.player = player;
            this.content = content;
            this.created = created;
        }
    }

    private final JavaPlugin plugin;
    private final Database database;

    public SuggestionCommentService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void addComment(int suggestionId, UUID player, String content) {
        if (!database.isConnected()) return;
        String sql = "INSERT INTO suggestion_comments (suggestion_id, player_uuid, content, timestamp) VALUES (?, ?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, suggestionId);
            ps.setString(2, player.toString());
            ps.setString(3, content);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to add comment: " + e.getMessage());
        }
    }

    public List<Comment> getComments(int suggestionId) {
        List<Comment> list = new ArrayList<>();
        if (!database.isConnected()) return list;
        String sql = "SELECT id, player_uuid, content, timestamp FROM suggestion_comments WHERE suggestion_id = ? ORDER BY timestamp";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, suggestionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    UUID player = UUID.fromString(rs.getString(2));
                    String content = rs.getString(3);
                    Instant ts = rs.getTimestamp(4).toInstant();
                    list.add(new Comment(id, suggestionId, player, content, ts));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load comments: " + e.getMessage());
        }
        return list;
    }
}
