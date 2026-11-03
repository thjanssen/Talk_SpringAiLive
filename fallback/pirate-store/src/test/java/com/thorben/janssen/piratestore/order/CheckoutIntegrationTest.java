package com.thorben.janssen.piratestore.order;

import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.EmptyCartException;
import com.thorben.janssen.piratestore.cart.InsufficientStockException;
import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full-stack checkout tests against a real (in-memory) database. The test
 * class is transactional so every test starts from the demo catalog seeded
 * once at context startup and rolls back its own mutations afterwards.
 */
@SpringBootTest
@Transactional
class CheckoutIntegrationTest {

    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("PIR-\\d{8}-\\d{4}");

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CartService cartService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;

    private Product eyePatch() {
        return productRepository.findBySku("EYE-PATCH-01").orElseThrow();
    }

    private CheckoutCommand validCommand() {
        return new CheckoutCommand("Long John Silver", "silver@example.com", "Treasure Lane 1",
                "12345", "Tortuga", "Germany");
    }

    @Test
    void successfulCheckoutCreatesOrderReducesStockAndClosesCart() {
        UUID ownerKey = UUID.randomUUID();
        Product product = eyePatch();
        int stockBefore = product.getStockQuantity();
        cartService.addProduct(ownerKey, product.getId(), 2);

        CustomerOrder order = orderService.placeOrder(ownerKey, validCommand());

        assertThat(order.getOrderNumber()).matches(ORDER_NUMBER_PATTERN);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotal()).isEqualByComparingTo(product.getPrice().multiply(java.math.BigDecimal.valueOf(2)));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(stockBefore - 2);
        assertThat(cartService.getCartSummary(ownerKey).isEmpty()).isTrue();
    }

    @Test
    void checkoutWithEmptyCartIsRejected() {
        UUID ownerKey = UUID.randomUUID();

        assertThatThrownBy(() -> orderService.placeOrder(ownerKey, validCommand()))
                .isInstanceOf(EmptyCartException.class);
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void checkoutRollsBackCompletelyWhenStockBecameInsufficient() {
        UUID ownerKey = UUID.randomUUID();
        Product wantedProduct = eyePatch();
        Product otherProduct = productRepository.findBySku("SPYGLASS-BRASS-01").orElseThrow();
        int otherStockBefore = otherProduct.getStockQuantity();

        cartService.addProduct(ownerKey, wantedProduct.getId(), 2);
        cartService.addProduct(ownerKey, otherProduct.getId(), 1);

        // Simulate the eye patch selling out via another session between adding to cart and checkout.
        wantedProduct.setStockQuantity(1);
        productRepository.saveAndFlush(wantedProduct);

        long ordersBefore = orderRepository.count();

        assertThatThrownBy(() -> orderService.placeOrder(ownerKey, validCommand()))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        assertThat(productRepository.findById(otherProduct.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(otherStockBefore);
        assertThat(cartService.getCartSummary(ownerKey).isEmpty()).isFalse();
    }

    @Test
    void orderNumbersAreUniqueAcrossOrders() {
        Product product = eyePatch();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        cartService.addProduct(ownerA, product.getId(), 1);
        cartService.addProduct(ownerB, product.getId(), 1);

        CustomerOrder orderA = orderService.placeOrder(ownerA, validCommand());
        CustomerOrder orderB = orderService.placeOrder(ownerB, validCommand());

        assertThat(orderA.getOrderNumber()).isNotEqualTo(orderB.getOrderNumber());
        assertThat(orderA.getOrderNumber()).matches(ORDER_NUMBER_PATTERN);
        assertThat(orderB.getOrderNumber()).matches(ORDER_NUMBER_PATTERN);
    }

    @Test
    void cartsOfDifferentOwnerKeysAreIsolated() {
        Product product = eyePatch();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();

        cartService.addProduct(ownerA, product.getId(), 1);

        assertThat(cartService.getCartSummary(ownerA).isEmpty()).isFalse();
        assertThat(cartService.getCartSummary(ownerB).isEmpty()).isTrue();
    }

    @Test
    void laterProductChangesDoNotAffectAnAlreadyPlacedOrder() {
        UUID ownerKey = UUID.randomUUID();
        Product product = eyePatch();
        cartService.addProduct(ownerKey, product.getId(), 1);

        CustomerOrder order = orderService.placeOrder(ownerKey, validCommand());
        String originalName = order.getItems().get(0).getProductName();
        var originalPrice = order.getItems().get(0).getUnitPrice();

        product.setStockQuantity(product.getStockQuantity() + 100); // unrelated later change
        productRepository.saveAndFlush(product);

        CustomerOrder reloaded = orderService.getRequiredOrder(ownerKey, order.getOrderNumber());
        assertThat(reloaded.getItems().get(0).getProductName()).isEqualTo(originalName);
        assertThat(reloaded.getItems().get(0).getUnitPrice()).isEqualByComparingTo(originalPrice);
    }
}
