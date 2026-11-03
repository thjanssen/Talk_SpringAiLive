package com.thorben.janssen.piratestore.support.cartaction;

/** Lifecycle of a {@link PendingCartAction}. Each action may leave {@code PENDING} exactly once. */
public enum PendingActionStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
