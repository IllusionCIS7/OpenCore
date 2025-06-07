package com.illusioncis7.opencore.rules;

import java.time.Instant;
import java.util.UUID;

public class RuleChange {
    public final int id;
    public final int ruleId;
    public final String oldText;
    public final String newText;
    public final Instant changedAt;
    public final UUID changedBy;
    public final Integer suggestionId;

    public RuleChange(int id, int ruleId, String oldText, String newText,
                      Instant changedAt, UUID changedBy, Integer suggestionId) {
        this.id = id;
        this.ruleId = ruleId;
        this.oldText = oldText;
        this.newText = newText;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
        this.suggestionId = suggestionId;
    }
}
