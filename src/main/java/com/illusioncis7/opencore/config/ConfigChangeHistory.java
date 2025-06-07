package com.illusioncis7.opencore.config;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Represents an applied configuration change for audit and rollback purposes.
 */
public class ConfigChangeHistory {
    public final UUID changeId;
    public final UUID playerUuid;
    public final String paramKey;
    public final Object oldValue;
    public final Object newValue;
    public final Timestamp changedAt;

    public ConfigChangeHistory(UUID changeId, UUID playerUuid, String paramKey, Object oldValue, Object newValue, Timestamp changedAt) {
        this.changeId = changeId;
        this.playerUuid = playerUuid;
        this.paramKey = paramKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedAt = changedAt;
    }
}
