package com.thorben.janssen.piratestore.cart;

/** Thrown when a cart operation targets a product that is not in the cart. */
public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(long productId) {
        super("Product " + productId + " is not in the cart");
    }
}
