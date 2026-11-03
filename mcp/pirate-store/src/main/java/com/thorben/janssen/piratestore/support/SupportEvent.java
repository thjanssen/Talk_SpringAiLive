package com.thorben.janssen.piratestore.support;

import java.util.List;

/**
 * One event of the answer streamed by pirate-store-ai: either a piece of the
 * answer's text, or, as the last event, the cart tool calls that wait for the
 * user to confirm them. The field names must match pirate-store-ai's
 * {@code ChatEvent}.
 */
public record SupportEvent(String text, List<CartToolCall> awaitingConfirmation) {

    public record CartToolCall(String name, String arguments) {
    }

    public boolean isPaused() {
        return awaitingConfirmation != null && !awaitingConfirmation.isEmpty();
    }
}
