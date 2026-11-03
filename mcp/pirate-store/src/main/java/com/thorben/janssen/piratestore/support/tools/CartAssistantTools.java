package com.thorben.janssen.piratestore.support.tools;

import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.CartSummary;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tools that change the shopping cart, exposed through the MCP server. The
 * pirate-store-ai application only calls them after the user confirmed the call
 * in the chat window (see {@code spring.ai.tools.confirmation.tools} there). The
 * {@code ownerKey} comes from {@link McpOwnerKeyContext}, never from a tool
 * parameter, so the model can neither see nor supply it.
 */
@Component
public class CartAssistantTools {

    public static final String ADD_TO_CART = "addToCart";
    public static final String CHANGE_QUANTITY = "changeQuantity";
    public static final String REMOVE_FROM_CART = "removeFromCart";

    private static final Logger logger = LoggerFactory.getLogger(CartAssistantTools.class);

    private final CartService cartService;

    public CartAssistantTools(CartService cartService) {
        this.cartService = cartService;
    }

    @McpTool(name = ADD_TO_CART, description = "Add the product with the given id in the given quantity to the shopping cart.")
    public CartSummary addToCart(
            @McpToolParam(description = "The id of the product to add.", required = true) long productId,
            @McpToolParam(description = "The quantity to add.", required = true) int quantity,
            McpSchema.CallToolRequest request) {
        logger.info("Tool addToCart called with productId={}, quantity={}", productId, quantity);
        UUID ownerKey = McpOwnerKeyContext.require(request);
        cartService.addProduct(ownerKey, productId, quantity);
        return cartService.getCartSummary(ownerKey);
    }

    @McpTool(name = CHANGE_QUANTITY, description = "Change the quantity of the product with the given id in the shopping cart.")
    public CartSummary changeQuantity(
            @McpToolParam(description = "The id of the product in the cart.", required = true) long productId,
            @McpToolParam(description = "The new quantity.", required = true) int newQuantity,
            McpSchema.CallToolRequest request) {
        logger.info("Tool changeQuantity called with productId={}, newQuantity={}", productId, newQuantity);
        UUID ownerKey = McpOwnerKeyContext.require(request);
        cartService.updateQuantity(ownerKey, productId, newQuantity);
        return cartService.getCartSummary(ownerKey);
    }

    @McpTool(name = REMOVE_FROM_CART, description = "Remove the product with the given id from the shopping cart.")
    public CartSummary removeFromCart(
            @McpToolParam(description = "The id of the product to remove.", required = true) long productId,
            McpSchema.CallToolRequest request) {
        logger.info("Tool removeFromCart called with productId={}", productId);
        UUID ownerKey = McpOwnerKeyContext.require(request);
        cartService.removeProduct(ownerKey, productId);
        return cartService.getCartSummary(ownerKey);
    }
}
