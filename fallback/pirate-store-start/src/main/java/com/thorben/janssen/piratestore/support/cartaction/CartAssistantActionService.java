package com.thorben.janssen.piratestore.support.cartaction;

import com.thorben.janssen.piratestore.cart.Cart;
import com.thorben.janssen.piratestore.cart.CartItem;
import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.CartSummary;
import com.thorben.janssen.piratestore.cart.InsufficientStockException;
import com.thorben.janssen.piratestore.cart.InvalidQuantityException;
import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductService;
import com.thorben.janssen.piratestore.product.ProductUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Application-controlled confirmation gate for cart changes proposed by the
 * support assistant. Proposals ({@link #proposeAdd}, {@link #proposeQuantityChange},
 * {@link #proposeRemoval}) only ever read the cart and never mutate it
 * (FR-12, AC-17). A change is applied through the regular {@link CartService}
 * only after {@link #confirm} re-validates it (FR-13, AC-21) -- a system
 * prompt alone is never treated as sufficient authorization.
 */
@Service
@Transactional
public class CartAssistantActionService {

    private final CartService cartService;
    private final ProductService productService;
    private final PendingCartActionStore store;

    public CartAssistantActionService(CartService cartService, ProductService productService,
                                       PendingCartActionStore store) {
        this.cartService = cartService;
        this.productService = productService;
        this.store = store;
    }

    public PendingCartAction proposeAdd(UUID ownerKey, long productId, int quantity) {
        if (quantity < 1) {
            throw new InvalidQuantityException(quantity);
        }
        Product product = requireAvailableProduct(productId);
        int previousQuantity = currentQuantity(ownerKey, productId);
        int newQuantity = previousQuantity + quantity;
        requireWithinStock(product, newQuantity);

        BigDecimal priceImpact = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        PendingCartAction action = new PendingCartAction(ownerKey, PendingActionType.ADD_PRODUCT, productId,
                product.getName(), previousQuantity, newQuantity, priceImpact);
        store.save(action);
        return action;
    }

    public PendingCartAction proposeQuantityChange(UUID ownerKey, long productId, int newQuantity) {
        if (newQuantity < 1) {
            throw new InvalidQuantityException(newQuantity);
        }
        Product product = requireAvailableProduct(productId);
        CartItem item = requireItemInCart(ownerKey, productId);
        requireWithinStock(product, newQuantity);

        int previousQuantity = item.getQuantity();
        BigDecimal priceImpact = product.getPrice().multiply(BigDecimal.valueOf(newQuantity - previousQuantity));
        PendingCartAction action = new PendingCartAction(ownerKey, PendingActionType.CHANGE_QUANTITY, productId,
                product.getName(), previousQuantity, newQuantity, priceImpact);
        store.save(action);
        return action;
    }

    public PendingCartAction proposeRemoval(UUID ownerKey, long productId) {
        Product product = productService.getRequiredProduct(productId);
        CartItem item = requireItemInCart(ownerKey, productId);

        int previousQuantity = item.getQuantity();
        BigDecimal priceImpact = product.getPrice().multiply(BigDecimal.valueOf(previousQuantity)).negate();
        PendingCartAction action = new PendingCartAction(ownerKey, PendingActionType.REMOVE_PRODUCT, productId,
                product.getName(), previousQuantity, 0, priceImpact);
        store.save(action);
        return action;
    }

    /**
     * Re-validates and applies a pending action. Confirmation is rejected unless
     * the action exists, belongs to {@code ownerKey} and is still {@code PENDING}
     * (AC-19, AC-20); product availability, stock and the current cart quantity
     * are checked again against their live state, not against the snapshot taken
     * when the proposal was created (AC-21).
     */
    public CartSummary confirm(UUID ownerKey, UUID actionId) {
        PendingCartAction action = requireOwnedPendingAction(ownerKey, actionId);

        Product product = requireAvailableProduct(action.getProductId());
        switch (action.getType()) {
            case ADD_PRODUCT -> {
                int amountToAdd = action.getNewQuantity() - action.getPreviousQuantity();
                int currentQuantity = currentQuantity(ownerKey, action.getProductId());
                requireWithinStock(product, currentQuantity + amountToAdd);
                cartService.addProduct(ownerKey, action.getProductId(), amountToAdd);
            }
            case CHANGE_QUANTITY -> {
                requireWithinStock(product, action.getNewQuantity());
                cartService.updateQuantity(ownerKey, action.getProductId(), action.getNewQuantity());
            }
            case REMOVE_PRODUCT -> cartService.removeProduct(ownerKey, action.getProductId());
        }

        action.markConfirmed();
        return cartService.getCartSummary(ownerKey);
    }

    public void cancel(UUID ownerKey, UUID actionId) {
        PendingCartAction action = requireOwnedAction(ownerKey, actionId);
        if (action.isPending()) {
            action.markCancelled();
        }
    }

    private PendingCartAction requireOwnedPendingAction(UUID ownerKey, UUID actionId) {
        PendingCartAction action = requireOwnedAction(ownerKey, actionId);
        if (!action.isPending()) {
            throw new PendingCartActionException("This proposal was already resolved and cannot be applied again");
        }
        return action;
    }

    private PendingCartAction requireOwnedAction(UUID ownerKey, UUID actionId) {
        PendingCartAction action = store.find(actionId)
                .orElseThrow(() -> new PendingCartActionException("This proposal no longer exists"));
        if (!action.getOwnerKey().equals(ownerKey)) {
            throw new PendingCartActionException("This proposal does not belong to your session");
        }
        return action;
    }

    private Product requireAvailableProduct(long productId) {
        Product product = productService.getRequiredProduct(productId);
        if (!product.isAvailable()) {
            throw new ProductUnavailableException(product.getName());
        }
        return product;
    }

    private CartItem requireItemInCart(UUID ownerKey, long productId) {
        Cart cart = cartService.getActiveCart(ownerKey);
        return cart.findItem(productId)
                .orElseThrow(() -> new PendingCartActionException("This product is not in the cart"));
    }

    private int currentQuantity(UUID ownerKey, long productId) {
        Cart cart = cartService.getActiveCart(ownerKey);
        return cart.findItem(productId).map(CartItem::getQuantity).orElse(0);
    }

    private void requireWithinStock(Product product, int requestedQuantity) {
        if (requestedQuantity > product.getStockQuantity()) {
            throw new InsufficientStockException(product.getName(), product.getStockQuantity());
        }
    }

    /** Convenience accessor for the chat UI to look up a proposal's view representation. */
    public Optional<PendingCartActionView> findView(UUID actionId) {
        return store.find(actionId).map(PendingCartAction::toView);
    }
}
