package com.illusioncis7.opencore.gpt;

import java.util.UUID;
import java.util.function.Consumer;

public class GptRequest {

    /** Unique request id. */
    public final UUID requestId;
    /** Optional module that created the request. */
    public final String module;
    /** Prompt text sent to GPT. */
    public final String prompt;
    /** Player related to the request or {@code null}. */
    public final UUID playerUuid;
    /** Callback for the response. */
    public final Consumer<String> callback;

    /**
     * Creates a new request.
     *
     * @param requestId unique id
     * @param module    module name (may be {@code null})
     * @param prompt    prompt text
     * @param playerUuid related player or {@code null}
     * @param callback  response callback
     */
    public GptRequest(UUID requestId, String module, String prompt, UUID playerUuid,
                      Consumer<String> callback) {
        this.requestId = requestId;
        this.module = module;
        this.prompt = prompt;
        this.playerUuid = playerUuid;
        this.callback = callback;
    }

    /**
     * Convenience constructor without module.
     */
    public GptRequest(UUID requestId, String prompt, UUID playerUuid, Consumer<String> callback) {
        this(requestId, null, prompt, playerUuid, callback);
    }
}
