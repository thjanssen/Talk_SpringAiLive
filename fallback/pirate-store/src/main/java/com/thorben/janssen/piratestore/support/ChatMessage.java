package com.thorben.janssen.piratestore.support;

import java.time.Instant;

/** A single message in the support chat history of one Vaadin session. */
public record ChatMessage(ChatRole role, String content, Instant timestamp) {

    public static ChatMessage user(String content) {
        return new ChatMessage(ChatRole.USER, content, Instant.now());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ChatRole.ASSISTANT, content, Instant.now());
    }
}
