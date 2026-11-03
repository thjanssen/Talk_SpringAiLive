package com.thorben.janssen.piratestore.cart;

/** Lifecycle of a {@link Cart}. */
public enum CartStatus {
    /** The cart can still be modified and checked out. */
    ACTIVE,
    /** The cart was converted into an order and is now immutable. */
    ORDERED
}
