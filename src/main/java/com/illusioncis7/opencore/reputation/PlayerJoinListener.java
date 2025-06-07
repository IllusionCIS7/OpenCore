package com.illusioncis7.opencore.reputation;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.util.logging.Logger;

public class PlayerJoinListener implements Listener {
    private final ReputationService reputationService;
    private final Logger logger;

    public PlayerJoinListener(ReputationService reputationService, Logger logger) {
        this.reputationService = reputationService;
        this.logger = logger;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        try {
            reputationService.registerPlayer(uuid);
        } catch (Exception e) {
            logger.warning("Failed to register player on join: " + e.getMessage());
        }
    }
}
