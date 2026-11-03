package com.thorben.janssen.piratestore.cart;

/** Thrown when an operation requires a non-empty cart, such as checkout. */
public class EmptyCartException extends RuntimeException {

    public EmptyCartException() {
        super("The cart is empty");
    }
}
