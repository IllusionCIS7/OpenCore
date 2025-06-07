package com.illusioncis7.opencore.gpt;

import java.util.UUID;
import java.util.function.Consumer;

public class GptRequest {

    public final UUID requestId;
    public final String prompt;
    public final UUID playerUuid;
    public final Consumer<String> callback;

    public GptRequest(UUID requestId, String prompt, UUID playerUuid, Consumer<String> callback) {
        this.requestId = requestId;
        this.prompt = prompt;
        this.playerUuid = playerUuid;
        this.callback = callback;
    }
}
