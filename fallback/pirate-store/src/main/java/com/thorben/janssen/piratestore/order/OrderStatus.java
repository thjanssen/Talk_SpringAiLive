package com.thorben.janssen.piratestore.order;

/**
 * Status of a {@link CustomerOrder}. The first version only ever reaches
 * {@code PLACED}; cancellation and later status transitions are out of scope
 * (see product requirements, FR-15).
 */
public enum OrderStatus {
    PLACED
}
