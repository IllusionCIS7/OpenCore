package com.illusioncis7.opencore.voting;

import java.util.UUID;

public class VoteStatus {
    public int suggestionId;
    public String shortName;
    public UUID initiator;
    public int yesWeight;
    public int noWeight;
    public int highRepYes;
    public int requiredWeight;
    public int requiredHighRep;
}
