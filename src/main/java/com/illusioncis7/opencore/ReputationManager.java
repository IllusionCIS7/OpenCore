package com.illusioncis7.opencore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReputationManager {
    private final OpenCore plugin;
    private final Map<UUID, Integer> reputationMap = new HashMap<>();

    public ReputationManager(OpenCore plugin) {
        this.plugin = plugin;
        plugin.getLogger().info("ReputationManager initialisiert.");
    }

    public void addReputation(UUID playerId, int points) {
        reputationMap.merge(playerId, points, Integer::sum);
        plugin.getLogger().info("Reputation für " + playerId + " um " + points + " erhöht.");
    }

    public int getReputation(UUID playerId) {
        return reputationMap.getOrDefault(playerId, 0);
    }
}
