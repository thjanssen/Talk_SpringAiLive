package com.thorben.janssen.piratestore.support.tools;

import com.thorben.janssen.piratestore.cart.Cart;
import com.thorben.janssen.piratestore.cart.CartItem;
import com.thorben.janssen.piratestore.cart.CartItemNotFoundException;
import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.InsufficientStockException;
import com.thorben.janssen.piratestore.cart.InvalidQuantityException;
import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductService;
import com.thorben.janssen.piratestore.product.ProductUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Translates a paused cart tool call into a {@link CartChangePreview}, without
 * changing the cart. A call that cannot succeed fails here with the same
 * exception the cart would throw, so the user is never asked to confirm it.
 */
@Service
@Transactional
public class CartChangePreviewService {

    private record AddArguments(long productId, int quantity) {
    }

    private record ChangeArguments(long productId, int newQuantity) {
    }

    private record RemoveArguments(long productId) {
    }

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final CartService cartService;
    private final ProductService productService;

    public CartChangePreviewService(CartService cartService, ProductService productService) {
        this.cartService = cartService;
        this.productService = productService;
    }

    public CartChangePreview preview(UUID ownerKey, String toolName, String toolArguments) {
        return switch (toolName) {
            case CartAssistantTools.ADD_TO_CART -> {
                var arguments = jsonMapper.readValue(toolArguments, AddArguments.class);
                yield previewAdd(ownerKey, arguments.productId(), arguments.quantity());
            }
            case CartAssistantTools.CHANGE_QUANTITY -> {
                var arguments = jsonMapper.readValue(toolArguments, ChangeArguments.class);
                yield previewChange(ownerKey, arguments.productId(), arguments.newQuantity());
            }
            case CartAssistantTools.REMOVE_FROM_CART -> {
                var arguments = jsonMapper.readValue(toolArguments, RemoveArguments.class);
                yield previewRemoval(ownerKey, arguments.productId());
            }
            default -> throw new IllegalArgumentException("Unknown cart tool: " + toolName);
        };
    }

    private CartChangePreview previewAdd(UUID ownerKey, long productId, int quantity) {
        if (quantity < 1) {
            throw new InvalidQuantityException(quantity);
        }
        Product product = requireAvailableProduct(productId);
        int currentQuantity = activeCart(ownerKey).findItem(productId).map(CartItem::getQuantity).orElse(0);
        requireWithinStock(product, currentQuantity + quantity);
        return new CartChangePreview("Add " + quantity + "x \"" + product.getName() + "\"", priceOf(product, quantity));
    }

    private CartChangePreview previewChange(UUID ownerKey, long productId, int newQuantity) {
        if (newQuantity < 1) {
            throw new InvalidQuantityException(newQuantity);
        }
        Product product = requireAvailableProduct(productId);
        int currentQuantity = requireItemInCart(ownerKey, productId).getQuantity();
        requireWithinStock(product, newQuantity);
        return new CartChangePreview(
                "Change quantity of \"" + product.getName() + "\" from " + currentQuantity + " to " + newQuantity,
                priceOf(product, newQuantity - currentQuantity));
    }

    private CartChangePreview previewRemoval(UUID ownerKey, long productId) {
        Product product = productService.getRequiredProduct(productId);
        int currentQuantity = requireItemInCart(ownerKey, productId).getQuantity();
        return new CartChangePreview("Remove \"" + product.getName() + "\" from the cart",
                priceOf(product, -currentQuantity));
    }

    private Cart activeCart(UUID ownerKey) {
        return cartService.getActiveCart(ownerKey);
    }

    private CartItem requireItemInCart(UUID ownerKey, long productId) {
        return activeCart(ownerKey).findItem(productId)
                .orElseThrow(() -> new CartItemNotFoundException(productId));
    }

    private Product requireAvailableProduct(long productId) {
        Product product = productService.getRequiredProduct(productId);
        if (!product.isAvailable()) {
            throw new ProductUnavailableException(product.getName());
        }
        return product;
    }

    private void requireWithinStock(Product product, int requestedQuantity) {
        if (requestedQuantity > product.getStockQuantity()) {
            throw new InsufficientStockException(product.getName(), product.getStockQuantity());
        }
    }

    private BigDecimal priceOf(Product product, int quantity) {
        return product.getPrice().multiply(BigDecimal.valueOf(quantity));
    }
}
