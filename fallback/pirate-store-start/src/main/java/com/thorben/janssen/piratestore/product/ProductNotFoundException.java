package com.thorben.janssen.piratestore.product;

/** Thrown when a product id does not refer to an existing product. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long productId) {
        super("Product with id " + productId + " does not exist");
    }
}
