package com.thorben.janssen.piratestore.support;

import java.util.UUID;

/**
 * Server-generated context passed to {@link SupportAssisstantService}. Both
 * values are read from {@code ShoppingSession}; neither the UI nor a future
 * model is ever allowed to supply or override them.
 */
public record SupportConversationContext(UUID ownerKey, UUID conversationId) {
}
