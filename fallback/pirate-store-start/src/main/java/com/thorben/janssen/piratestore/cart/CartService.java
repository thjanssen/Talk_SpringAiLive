package com.thorben.janssen.piratestore.cart;

import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductService;
import com.thorben.janssen.piratestore.product.ProductUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Business service that owns all cart mutations. Every public method takes
 * the caller's {@code ownerKey} explicitly; there is intentionally no method
 * that resolves a single "current" cart without it, so a session can never
 * read or change another session's cart.
 */
@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final ProductService productService;
    private final CartUpdateBroadcaster broadcaster;

    public CartService(CartRepository cartRepository, ProductService productService,
                        CartUpdateBroadcaster broadcaster) {
        this.cartRepository = cartRepository;
        this.productService = productService;
        this.broadcaster = broadcaster;
    }

    /** Returns the active cart for the given owner, creating one if none exists yet. */
    public Cart getActiveCart(UUID ownerKey) {
        return cartRepository.findByOwnerKeyAndStatus(ownerKey, CartStatus.ACTIVE)
                .orElseGet(() -> cartRepository.save(new Cart(ownerKey)));
    }

    @Transactional(readOnly = true)
    public CartSummary getCartSummary(UUID ownerKey) {
        return cartRepository.findByOwnerKeyAndStatus(ownerKey, CartStatus.ACTIVE)
                .map(this::toSummary)
                .orElseGet(() -> CartSummary.empty(ownerKey));
    }

    public void addProduct(UUID ownerKey, long productId, int quantity) {
        if (quantity < 1) {
            throw new InvalidQuantityException(quantity);
        }
        Cart cart = getActiveCart(ownerKey);
        Product product = productService.getRequiredProduct(productId);
        requireAvailable(product);

        int currentQuantity = cart.findItem(productId).map(CartItem::getQuantity).orElse(0);
        int requestedTotal = currentQuantity + quantity;
        requireWithinStock(product, requestedTotal);

        cart.addProduct(product, quantity);
        publishChange(cart);
    }

    public void updateQuantity(UUID ownerKey, long productId, int quantity) {
        if (quantity < 1) {
            throw new InvalidQuantityException(quantity);
        }
        Cart cart = getActiveCart(ownerKey);
        CartItem item = cart.findItem(productId)
                .orElseThrow(() -> new CartItemNotFoundException(productId));
        requireWithinStock(item.getProduct(), quantity);

        item.setQuantity(quantity);
        cart.touch();
        publishChange(cart);
    }

    public void removeProduct(UUID ownerKey, long productId) {
        Cart cart = getActiveCart(ownerKey);
        cart.findItem(productId).ifPresent(cart::removeItem);
        publishChange(cart);
    }

    /**
     * Marks the current active cart (if any) as {@link CartStatus#ORDERED} and
     * broadcasts an empty summary so badge and cart view reset immediately.
     * Called by {@code OrderService} as part of a successful checkout; a new
     * active cart is created lazily on the next cart operation.
     */
    public void closeActiveCart(UUID ownerKey) {
        cartRepository.findByOwnerKeyAndStatus(ownerKey, CartStatus.ACTIVE).ifPresent(cart -> {
            cart.markOrdered();
            broadcaster.publish(new CartChangedEvent(ownerKey, CartSummary.empty(ownerKey)));
        });
    }

    private void requireAvailable(Product product) {
        if (!product.isAvailable()) {
            throw new ProductUnavailableException(product.getName());
        }
    }

    private void requireWithinStock(Product product, int requestedQuantity) {
        if (requestedQuantity > product.getStockQuantity()) {
            throw new InsufficientStockException(product.getName(), product.getStockQuantity());
        }
    }

    private void publishChange(Cart cart) {
        broadcaster.publish(new CartChangedEvent(cart.getOwnerKey(), toSummary(cart)));
    }

    private CartSummary toSummary(Cart cart) {
        List<CartItemView> views = cart.getItems().stream()
                .map(item -> new CartItemView(
                        item.getProduct().getId(),
                        item.getProduct().getSku(),
                        item.getProduct().getName(),
                        item.getProduct().getPrice(),
                        item.getQuantity(),
                        item.getLineTotal(),
                        item.getProduct().getStockQuantity()))
                .toList();

        int totalQuantity = views.stream().mapToInt(CartItemView::quantity).sum();
        BigDecimal totalPrice = views.stream()
                .map(CartItemView::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new CartSummary(cart.getOwnerKey(), views, totalQuantity, totalPrice);
    }
}
