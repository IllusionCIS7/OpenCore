package com.illusioncis7.opencore.voting;

import java.time.Instant;
import java.util.UUID;

public class Suggestion {
    public final int id;
    public final UUID playerUuid;
    public final int parameterId;
    public final String newValue;
    public final String description;
    public final String text;
    public final Instant created;
    public final boolean open;
    public final boolean expired;

    public Suggestion(int id, UUID playerUuid, int parameterId, String newValue, String description, String text,
                      Instant created, boolean open, boolean expired) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.parameterId = parameterId;
        this.newValue = newValue;
        this.description = description;
        this.text = text;
        this.created = created;
        this.open = open;
        this.expired = expired;
    }
}
