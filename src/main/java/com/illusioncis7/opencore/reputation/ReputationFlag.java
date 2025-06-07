package com.illusioncis7.opencore.reputation;

/** Simple DTO describing a reputation flag for chat analysis. */
public class ReputationFlag {
    public final String code;
    public final String description;
    public final int minChange;
    public final int maxChange;
    public final boolean active;

    public ReputationFlag(String code, String description, int minChange, int maxChange, boolean active) {
        this.code = code;
        this.description = description;
        this.minChange = minChange;
        this.maxChange = maxChange;
        this.active = active;
    }
}
