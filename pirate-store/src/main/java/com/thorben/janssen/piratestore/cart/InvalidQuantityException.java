package com.thorben.janssen.piratestore.cart;

/** Thrown when a requested quantity is not a positive number. */
public class InvalidQuantityException extends RuntimeException {

    public InvalidQuantityException(int quantity) {
        super("Quantity must be at least 1, but was " + quantity);
    }
}
