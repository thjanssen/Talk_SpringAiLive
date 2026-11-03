package com.thorben.janssen.piratestoreai.chat;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * The user's decision on the cart changes of a paused reply. {@code reason} is
 * optional and replaces the default message when the changes are declined. The
 * tool calls themselves stay on this server, so the caller cannot change them.
 */
public record DecisionRequest(
        @NotNull UUID ownerKey,
        @NotNull UUID conversationId,
        boolean approved,
        String reason
) {
}
