package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.cart.Cart;
import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.InsufficientStockException;
import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductCategory;
import com.thorben.janssen.piratestore.product.ProductService;
import com.thorben.janssen.piratestore.support.cartaction.CartAssistantActionService;
import com.thorben.janssen.piratestore.support.cartaction.PendingCartAction;
import com.thorben.janssen.piratestore.support.cartaction.PendingCartActionException;
import com.thorben.janssen.piratestore.support.cartaction.PendingCartActionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the application-controlled confirmation gate. These exercise
 * exactly the rules from FR-12/FR-13 and AC-17 through AC-21, independent of
 * any chat UI or AI implementation.
 */
class CartAssistantActionServiceTest {

    private CartService cartService;
    private ProductService productService;
    private CartAssistantActionService actionService;

    @BeforeEach
    void setUp() {
        cartService = Mockito.mock(CartService.class);
        productService = Mockito.mock(ProductService.class);
        actionService = new CartAssistantActionService(cartService, productService, new PendingCartActionStore());
    }

    private Product product(long id, int stock) {
        Product product = new Product("EYE-PATCH-01", "Classic Black Eye Patch", "desc", ProductCategory.CLOTHING,
                new BigDecimal("9.99"), stock, "🏴‍☠️");
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return product;
    }

    @Test
    void proposingAnAdditionDoesNotMutateTheCart() {
        UUID ownerKey = UUID.randomUUID();
        Product product = product(1L, 25);
        when(productService.getRequiredProduct(1L)).thenReturn(product);
        when(cartService.getActiveCart(ownerKey)).thenReturn(new Cart(ownerKey));

        PendingCartAction action = actionService.proposeAdd(ownerKey, 1L, 2);

        assertThat(action.isPending()).isTrue();
        assertThat(action.getPriceImpact()).isEqualByComparingTo("19.98");
        verify(cartService, never()).addProduct(any(UUID.class), anyLong(), anyInt());
    }

    @Test
    void confirmedActionAppliesExactlyOnce() {
        UUID ownerKey = UUID.randomUUID();
        Product product = product(1L, 25);
        when(productService.getRequiredProduct(1L)).thenReturn(product);
        when(cartService.getActiveCart(ownerKey)).thenReturn(new Cart(ownerKey));
        PendingCartAction action = actionService.proposeAdd(ownerKey, 1L, 2);

        actionService.confirm(ownerKey, action.getActionId());

        assertThatThrownBy(() -> actionService.confirm(ownerKey, action.getActionId()))
                .isInstanceOf(PendingCartActionException.class);
        verify(cartService, times(1)).addProduct(ownerKey, 1L, 2);
    }

    @Test
    void cancelledActionCannotBeConfirmedAfterwards() {
        UUID ownerKey = UUID.randomUUID();
        Product product = product(1L, 25);
        when(productService.getRequiredProduct(1L)).thenReturn(product);
        when(cartService.getActiveCart(ownerKey)).thenReturn(new Cart(ownerKey));
        PendingCartAction action = actionService.proposeAdd(ownerKey, 1L, 2);

        actionService.cancel(ownerKey, action.getActionId());

        assertThatThrownBy(() -> actionService.confirm(ownerKey, action.getActionId()))
                .isInstanceOf(PendingCartActionException.class);
        verify(cartService, never()).addProduct(any(UUID.class), anyLong(), anyInt());
    }

    @Test
    void actionCannotBeConfirmedByADifferentOwnerKey() {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        Product product = product(1L, 25);
        when(productService.getRequiredProduct(1L)).thenReturn(product);
        when(cartService.getActiveCart(owner)).thenReturn(new Cart(owner));
        PendingCartAction action = actionService.proposeAdd(owner, 1L, 2);

        assertThatThrownBy(() -> actionService.confirm(otherOwner, action.getActionId()))
                .isInstanceOf(PendingCartActionException.class);
        verify(cartService, never()).addProduct(any(UUID.class), anyLong(), anyInt());
    }

    @Test
    void stockIsReCheckedAtConfirmationTime() {
        UUID ownerKey = UUID.randomUUID();
        Product product = product(1L, 5);
        when(productService.getRequiredProduct(1L)).thenReturn(product);
        when(cartService.getActiveCart(ownerKey)).thenReturn(new Cart(ownerKey));
        PendingCartAction action = actionService.proposeAdd(ownerKey, 1L, 5);

        // Stock drops between proposal and confirmation, e.g. because another order was placed.
        product.reduceStock(4);

        assertThatThrownBy(() -> actionService.confirm(ownerKey, action.getActionId()))
                .isInstanceOf(InsufficientStockException.class);
        verify(cartService, never()).addProduct(any(UUID.class), anyLong(), anyInt());
    }

}
