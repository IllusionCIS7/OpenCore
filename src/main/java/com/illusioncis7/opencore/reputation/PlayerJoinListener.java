package com.illusioncis7.opencore.reputation;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import com.illusioncis7.opencore.message.MessageService;

import java.util.UUID;
import java.util.logging.Logger;
import com.illusioncis7.opencore.plan.PlanHook;

public class PlayerJoinListener implements Listener {
    private final ReputationService reputationService;
    private final Logger logger;
    private final PlanHook planHook;
    private final MessageService messageService;

    public PlayerJoinListener(ReputationService reputationService, Logger logger, PlanHook planHook, MessageService messageService) {
        this.reputationService = reputationService;
        this.logger = logger;
        this.planHook = planHook;
        this.messageService = messageService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        try {
            reputationService.registerPlayer(uuid);
            if (planHook != null && planHook.isAvailable()) {
                java.time.Instant last = planHook.getLastSeen(uuid);
                reputationService.applyInactivityDecay(uuid, last);
            }
        } catch (Exception e) {
            logger.warning("Failed to register player on join: " + e.getMessage());
        }
        messageService.send(event.getPlayer(), "join.privacy_warning", null);
    }
}
