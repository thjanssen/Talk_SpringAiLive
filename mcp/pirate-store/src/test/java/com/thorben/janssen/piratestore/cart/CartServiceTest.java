package com.thorben.janssen.piratestore.cart;

import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductCategory;
import com.thorben.janssen.piratestore.product.ProductRepository;
import com.thorben.janssen.piratestore.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Unit tests for the core cart business rules, without a database. */
class CartServiceTest {

    private CartRepository cartRepository;
    private ProductRepository productRepository;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartRepository = Mockito.mock(CartRepository.class);
        productRepository = Mockito.mock(ProductRepository.class);
        ProductService productService = new ProductService(productRepository);
        cartService = new CartService(cartRepository, productService, new CartUpdateBroadcaster());

        // save() just returns whatever Cart it is given, like a real repository would for a new entity.
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Product eyePatch() {
        Product product = new Product("EYE-PATCH-01", "Classic Black Eye Patch", "desc",
                ProductCategory.CLOTHING, new BigDecimal("9.99"), 25, "🏴‍☠️");
        setId(product, 1L);
        return product;
    }

    private static void setId(Product product, long id) {
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void addingSameProductTwiceIncreasesQuantityInsteadOfCreatingSecondLine() {
        UUID ownerKey = UUID.randomUUID();
        Product product = eyePatch();
        Cart cart = new Cart(ownerKey);
        when(cartRepository.findByOwnerKeyAndStatus(ownerKey, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        cartService.addProduct(ownerKey, 1L, 1);
        cartService.addProduct(ownerKey, 1L, 1);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void cartSummaryComputesTotalsFromLineItems() {
        UUID ownerKey = UUID.randomUUID();
        Product product = eyePatch();
        Cart cart = new Cart(ownerKey);
        when(cartRepository.findByOwnerKeyAndStatus(ownerKey, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        cartService.addProduct(ownerKey, 1L, 2);
        CartSummary summary = cartService.getCartSummary(ownerKey);

        assertThat(summary.totalQuantity()).isEqualTo(2);
        assertThat(summary.totalPrice()).isEqualByComparingTo("19.98");
    }

    @Test
    void addProductBeyondAvailableStockIsRejected() {
        UUID ownerKey = UUID.randomUUID();
        Product product = new Product("PARROT-01", "Polly the Parrot", "desc", ProductCategory.COMPANIONS,
                new BigDecimal("499.00"), 2, "🦜");
        setId(product, 2L);
        Cart cart = new Cart(ownerKey);
        when(cartRepository.findByOwnerKeyAndStatus(ownerKey, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addProduct(ownerKey, 2L, 3))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void updateQuantityRejectsZeroOrNegativeValues() {
        UUID ownerKey = UUID.randomUUID();
        assertThatThrownBy(() -> cartService.updateQuantity(ownerKey, 1L, 0))
                .isInstanceOf(InvalidQuantityException.class);
        assertThatThrownBy(() -> cartService.updateQuantity(ownerKey, 1L, -1))
                .isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void removingCartItemDeletesTheLine() {
        UUID ownerKey = UUID.randomUUID();
        Product product = eyePatch();
        Cart cart = new Cart(ownerKey);
        when(cartRepository.findByOwnerKeyAndStatus(ownerKey, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        cartService.addProduct(ownerKey, 1L, 1);

        cartService.removeProduct(ownerKey, 1L);

        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void differentOwnerKeysGetIndependentCarts() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        when(cartRepository.findByOwnerKeyAndStatus(ownerA, CartStatus.ACTIVE)).thenReturn(Optional.empty());
        when(cartRepository.findByOwnerKeyAndStatus(ownerB, CartStatus.ACTIVE)).thenReturn(Optional.empty());

        Cart cartA = cartService.getActiveCart(ownerA);
        Cart cartB = cartService.getActiveCart(ownerB);

        assertThat(cartA).isNotSameAs(cartB);
        assertThat(cartA.getOwnerKey()).isEqualTo(ownerA);
        assertThat(cartB.getOwnerKey()).isEqualTo(ownerB);
    }
}
