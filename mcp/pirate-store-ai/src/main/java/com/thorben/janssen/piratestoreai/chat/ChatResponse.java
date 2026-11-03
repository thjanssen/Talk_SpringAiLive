package com.thorben.janssen.piratestoreai.chat;

import java.util.Map;

/**
 * Response body consumed by pirate-store's {@code SupportAssisstantService},
 * which deserializes it directly into its own {@code SupportResponse} record
 * -- field names here must keep matching that record ({@code message},
 * {@code pendingAction}). {@code pendingAction} is left as a generic
 * {@code Map} (rather than a shared DTO type) because its shape already
 * comes straight from pirate-store's {@code PendingCartActionView}, returned
 * as-is by the MCP tool call that created it -- see {@code SupportChatService}.
 */
public record ChatResponse(String message, Map<String, Object> pendingAction) {

    public static ChatResponse text(String message) {
        return new ChatResponse(message, null);
    }
}
