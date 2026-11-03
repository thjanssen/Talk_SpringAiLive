package com.thorben.janssen.piratestore.order;

/** Thrown when an order number does not resolve to an order of the given session. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderNumber) {
        super("No order found for order number " + orderNumber);
    }
}
