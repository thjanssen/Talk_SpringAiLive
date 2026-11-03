package com.thorben.janssen.piratestoreai.chat;

import java.util.List;

/**
 * One event of the streamed answer: either a piece of the answer's text, or, as
 * the last event, the tool calls that wait for the user to confirm them. The
 * field names must match pirate-store's {@code SupportEvent}.
 */
public record ChatEvent(String text, List<ToolCallRequest> awaitingConfirmation) {

    public record ToolCallRequest(String name, String arguments) {
    }

    static ChatEvent text(String text) {
        return new ChatEvent(text, null);
    }

    static ChatEvent paused(List<ToolCallRequest> toolCalls) {
        return new ChatEvent(null, toolCalls);
    }
}
