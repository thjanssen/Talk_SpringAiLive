package com.thorben.janssen.piratestore.cart;

import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductCategory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** JPA mapping tests for {@link Cart} and {@link CartItem}: cascade and orphan removal. */
@DataJpaTest
class CartJpaMappingIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CartRepository cartRepository;

    @Test
    void cartItemsAreSavedAndReloadedTogetherWithTheCart() {
        Product product = entityManager.persistAndFlush(new Product("TEST-SKU-CART-01", "Test Item", "desc",
                ProductCategory.EQUIPMENT, new BigDecimal("12.50"), 10, "🧭"));

        UUID ownerKey = UUID.randomUUID();
        Cart cart = new Cart(ownerKey);
        cart.addItem(new CartItem(product, 3));
        Cart savedCart = entityManager.persistFlushFind(cart);

        assertThat(savedCart.getItems()).hasSize(1);
        assertThat(savedCart.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(savedCart.getItems().get(0).getProduct().getSku()).isEqualTo("TEST-SKU-CART-01");
    }

    @Test
    void removingAnItemFromTheCartDeletesItFromTheDatabase() {
        Product product = entityManager.persistAndFlush(new Product("TEST-SKU-CART-02", "Test Item 2", "desc",
                ProductCategory.EQUIPMENT, new BigDecimal("12.50"), 10, "🧭"));

        Cart cart = new Cart(UUID.randomUUID());
        CartItem item = new CartItem(product, 1);
        cart.addItem(item);
        cart = entityManager.persistFlushFind(cart);
        Long itemId = cart.getItems().get(0).getId();

        cart.removeItem(cart.getItems().get(0));
        entityManager.persistAndFlush(cart);
        entityManager.clear();

        assertThat(entityManager.find(CartItem.class, itemId)).isNull();
    }

    @Test
    void findByOwnerKeyAndStatusOnlyReturnsTheMatchingCart() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        entityManager.persistAndFlush(new Cart(ownerA));
        entityManager.persistAndFlush(new Cart(ownerB));

        var found = cartRepository.findByOwnerKeyAndStatus(ownerA, CartStatus.ACTIVE);

        assertThat(found).isPresent();
        assertThat(found.get().getOwnerKey()).isEqualTo(ownerA);
    }
}
