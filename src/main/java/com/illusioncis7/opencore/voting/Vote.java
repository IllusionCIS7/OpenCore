package com.illusioncis7.opencore.voting;

import java.util.UUID;

public class Vote {
    public final int id;
    public final int suggestionId;
    public final UUID playerUuid;
    public final boolean voteYes;
    public final int weight;

    public Vote(int id, int suggestionId, UUID playerUuid, boolean voteYes, int weight) {
        this.id = id;
        this.suggestionId = suggestionId;
        this.playerUuid = playerUuid;
        this.voteYes = voteYes;
        this.weight = weight;
    }
}
