package com.illusioncis7.opencore.reputation;

import java.time.Instant;
import java.util.UUID;

public class ReputationEvent {
    public final UUID id;
    public final Instant timestamp;
    public final UUID playerUuid;
    public final int change;
    public final String reasonSummary;
    public final String sourceModule;
    public final String detailsJson;

    public ReputationEvent(UUID id, Instant timestamp, UUID playerUuid, int change,
                           String reasonSummary, String sourceModule, String detailsJson) {
        this.id = id;
        this.timestamp = timestamp;
        this.playerUuid = playerUuid;
        this.change = change;
        this.reasonSummary = reasonSummary;
        this.sourceModule = sourceModule;
        this.detailsJson = detailsJson;
    }
}
