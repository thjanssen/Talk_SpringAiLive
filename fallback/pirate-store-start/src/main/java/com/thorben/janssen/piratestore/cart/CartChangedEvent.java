package com.thorben.janssen.piratestore.cart;

import java.util.UUID;

/**
 * Published whenever a cart mutates. Every UI component reacts only to
 * events whose {@link #ownerKey()} matches its own {@code ShoppingSession},
 * so cart changes never leak across sessions.
 */
public record CartChangedEvent(UUID ownerKey, CartSummary summary) {
}
