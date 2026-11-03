package com.thorben.janssen.piratestore.product;

/** Thrown when an operation targets a product that is inactive or out of stock. */
public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(String productName) {
        super("\"" + productName + "\" is currently not available");
    }
}
