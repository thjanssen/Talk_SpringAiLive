package com.thorben.janssen.piratestore.cart;

import java.math.BigDecimal;

/**
 * Immutable, UI-facing view of a single cart line. The UI reads
 * {@link #lineTotal()} instead of computing it itself.
 */
public record CartItemView(
        long productId,
        String sku,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        int availableStock
) {
}
