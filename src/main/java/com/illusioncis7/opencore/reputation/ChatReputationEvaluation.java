package com.illusioncis7.opencore.reputation;

/** Result item from GPT chat reputation analysis. */
public class ChatReputationEvaluation {
    public final String player;
    public final String flag;
    public final int change;
    public final String reason;

    public ChatReputationEvaluation(String player, String flag, int change, String reason) {
        this.player = player;
        this.flag = flag;
        this.change = change;
        this.reason = reason;
    }
}
