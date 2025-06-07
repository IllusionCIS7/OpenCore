package com.illusioncis7.opencore.gpt;

import java.time.Instant;

public class GptResponseRecord {
    public final String module;
    public final String response;
    public final Instant timestamp;

    public GptResponseRecord(String module, String response, Instant timestamp) {
        this.module = module;
        this.response = response;
        this.timestamp = timestamp;
    }
}
