package com.thorben.janssen.piratestore.support.cartaction;

/**
 * Thrown when a {@link PendingCartAction} cannot be confirmed or cancelled,
 * for example because it does not exist, belongs to a different session, or
 * was already resolved.
 */
public class PendingCartActionException extends RuntimeException {

    public PendingCartActionException(String message) {
        super(message);
    }
}
