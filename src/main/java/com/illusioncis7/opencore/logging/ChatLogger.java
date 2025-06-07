package com.illusioncis7.opencore.logging;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Logger;

public class ChatLogger implements Listener {

    private final Database database;
    private final Logger logger;

    public ChatLogger(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (database.getConnection() == null) {
            return;
        }
        String sql = "INSERT INTO chat_log (player_uuid, message_time, message) VALUES (?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.getPlayer().getUniqueId().toString());
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setString(3, event.getMessage());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to log chat message: " + e.getMessage());
        }
    }
}
