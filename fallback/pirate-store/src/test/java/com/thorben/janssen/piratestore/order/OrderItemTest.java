package com.thorben.janssen.piratestore.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for the order line snapshot (FR-06, AC-11: it never references {@code Product}). */
class OrderItemTest {

    @Test
    void snapshotStoresPriceAndNameIndependentlyOfProduct() {
        OrderItem item = new OrderItem("EYE-PATCH-01", "Classic Black Eye Patch", new BigDecimal("9.99"), 2);

        assertThat(item.getSku()).isEqualTo("EYE-PATCH-01");
        assertThat(item.getProductName()).isEqualTo("Classic Black Eye Patch");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("9.99");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getLineTotal()).isEqualByComparingTo("19.98");
    }
}
