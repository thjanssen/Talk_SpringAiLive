package com.thorben.janssen.piratestore.support.tools;

import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.CartSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tools that change the shopping cart. Spring AI only runs them after the user
 * confirmed the call in the chat window ({@code requiresConfirmation}).
 */
@Component
public class CartAssistantTools {

    private static final Logger logger = LoggerFactory.getLogger(CartAssistantTools.class);

    public static final String ADD_TO_CART = "addToCart";
    public static final String CHANGE_QUANTITY = "changeQuantity";
    public static final String REMOVE_FROM_CART = "removeFromCart";

    private final CartService cartService;

    public CartAssistantTools(CartService cartService) {
        this.cartService = cartService;
    }

    @Tool(name = ADD_TO_CART, description = "Add the product with the given id in the given quantity to the shopping cart.",
            requiresConfirmation = true)
    public CartSummary addToCart(long productId, int quantity, ToolContext toolContext) {
        logger.info("Tool addToCart called with productId={}, quantity={}", productId, quantity);
        UUID ownerKey = OwnerKeyContext.require(toolContext.getContext());
        cartService.addProduct(ownerKey, productId, quantity);
        return cartService.getCartSummary(ownerKey);
    }

    @Tool(name = CHANGE_QUANTITY, description = "Change the quantity of the product with the given id in the shopping cart.",
            requiresConfirmation = true)
    public CartSummary changeQuantity(long productId, int newQuantity, ToolContext toolContext) {
        logger.info("Tool changeQuantity called with productId={}, newQuantity={}", productId, newQuantity);
        UUID ownerKey = OwnerKeyContext.require(toolContext.getContext());
        cartService.updateQuantity(ownerKey, productId, newQuantity);
        return cartService.getCartSummary(ownerKey);
    }

    @Tool(name = REMOVE_FROM_CART, description = "Remove the product with the given id from the shopping cart.",
            requiresConfirmation = true)
    public CartSummary removeFromCart(long productId, ToolContext toolContext) {
        logger.info("Tool removeFromCart called with productId={}", productId);
        UUID ownerKey = OwnerKeyContext.require(toolContext.getContext());
        cartService.removeProduct(ownerKey, productId);
        return cartService.getCartSummary(ownerKey);
    }
}
