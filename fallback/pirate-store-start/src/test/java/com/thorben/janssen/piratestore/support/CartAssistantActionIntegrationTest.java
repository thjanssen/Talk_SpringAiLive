package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.InsufficientStockException;
import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductRepository;
import com.thorben.janssen.piratestore.product.ProductService;
import com.thorben.janssen.piratestore.support.cartaction.CartAssistantActionService;
import com.thorben.janssen.piratestore.support.cartaction.PendingCartAction;
import com.thorben.janssen.piratestore.support.cartaction.PendingCartActionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for AC-21: stock must be re-checked against the real
 * database at confirmation time, not only against the snapshot taken when
 * the proposal was created. {@link PendingCartActionStore} is created
 * directly here since it is normally a {@code @VaadinSessionScope} bean and
 * no Vaadin session exists in this test.
 */
@SpringBootTest
@Transactional
class CartAssistantActionIntegrationTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CartService cartService;
    @Autowired
    private ProductService productService;

    @Test
    void confirmingAProposalAfterStockDroppedBelowTheRequestedAmountFails() {
        CartAssistantActionService actionService = new CartAssistantActionService(cartService, productService,
                new PendingCartActionStore());
        UUID ownerKey = UUID.randomUUID();
        Product product = productRepository.findBySku("EYE-PATCH-01").orElseThrow();

        PendingCartAction action = actionService.proposeAdd(ownerKey, product.getId(), product.getStockQuantity());

        // Stock drops (but does not reach zero) between proposal and confirmation, e.g. another session
        // just checked out; zero stock would make the product unavailable instead of merely insufficient.
        product.setStockQuantity(1);
        productRepository.saveAndFlush(product);

        assertThatThrownBy(() -> actionService.confirm(ownerKey, action.getActionId()))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(cartService.getCartSummary(ownerKey).isEmpty()).isTrue();
    }

    @Test
    void confirmedProposalIsReflectedInTheRealCart() {
        CartAssistantActionService actionService = new CartAssistantActionService(cartService, productService,
                new PendingCartActionStore());
        UUID ownerKey = UUID.randomUUID();
        Product product = productRepository.findBySku("EYE-PATCH-01").orElseThrow();

        PendingCartAction action = actionService.proposeAdd(ownerKey, product.getId(), 2);
        actionService.confirm(ownerKey, action.getActionId());

        var summary = cartService.getCartSummary(ownerKey);
        assertThat(summary.totalQuantity()).isEqualTo(2);
    }
}
