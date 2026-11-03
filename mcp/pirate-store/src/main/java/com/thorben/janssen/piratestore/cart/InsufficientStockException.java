package com.thorben.janssen.piratestore.cart;

/** Thrown when a requested quantity exceeds the available stock of a product. */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productName, int available) {
        super("Only " + available + " unit(s) of \"" + productName + "\" are available");
    }
}
