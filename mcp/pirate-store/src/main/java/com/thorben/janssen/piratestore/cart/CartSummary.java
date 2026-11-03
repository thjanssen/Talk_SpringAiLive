package com.thorben.janssen.piratestore.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Immutable DTO summarizing a cart for display purposes. All monetary and
 * quantity totals are computed by {@link CartService}; neither the UI nor the
 * support assistant tools sum prices themselves.
 */
public record CartSummary(
        UUID ownerKey,
        List<CartItemView> items,
        int totalQuantity,
        BigDecimal totalPrice
) {

    public static CartSummary empty(UUID ownerKey) {
        return new CartSummary(ownerKey, List.of(), 0, BigDecimal.ZERO.setScale(2));
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
