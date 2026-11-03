package com.thorben.janssen.piratestore.support.tools;

import com.thorben.janssen.piratestore.cart.Cart;
import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.support.cartaction.CartAssistantActionService;
import com.thorben.janssen.piratestore.support.cartaction.PendingCartActionView;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP-facing wrapper around {@link CartAssistantActionService}, exposed as an
 * MCP tool to the (separate) pirate-store-ai application; {@code @McpTool}-annotated
 * methods are discovered automatically by Spring AI's MCP annotation scanner
 * (enabled by default, see {@code spring.ai.mcp.server.annotation-scanner.enabled}).
 * Every mutating method only ever creates a {@code PendingCartAction} (never
 * applies it) and returns its {@link PendingCartActionView} directly as the
 * tool result, so the caller can render Confirm/Cancel controls without a
 * second round trip -- applying it still requires a separate, explicit
 * {@code CartAssistantActionService#confirm} call triggered by the user in
 * the chat UI (FR-13).
 *
 * <p>{@code ownerKey} is resolved per call from {@link McpOwnerKeyContext},
 * never accepted as a tool parameter -- see its Javadoc.
 */
@Component
public class CartAssisstantTools {

    private final CartAssistantActionService cartAssistantActionService;
    private final CartService cartService;

    public CartAssisstantTools(CartAssistantActionService cartAssistantActionService, CartService cartService) {
        this.cartAssistantActionService = cartAssistantActionService;
        this.cartService = cartService;
    }

    @McpTool(name = "getActiveCart", description = "Returns the active cart for the given owner, creating one if none exists yet.")
    public Cart getActiveCart(McpSchema.CallToolRequest request) {
        return cartService.getActiveCart(McpOwnerKeyContext.require(request));
    }

    @McpTool(name = "addProduct", description = "Adds the product with the given id in the given quantity to the shopping cart")
    public PendingCartActionView proposeAdd(
            @McpToolParam(description = "The id of the product to add.", required = true) long productId,
            @McpToolParam(description = "The quantity to add.", required = true) int quantity,
            McpSchema.CallToolRequest request) {
        return cartAssistantActionService.proposeAdd(McpOwnerKeyContext.require(request), productId, quantity).toView();
    }

    @McpTool(name = "changeQuantity", description = "Change the quantity of the given product in the shopping cart")
    public PendingCartActionView proposeQuantityChange(
            @McpToolParam(description = "The id of the product in the cart.", required = true) long productId,
            @McpToolParam(description = "The new quantity.", required = true) int newQuantity,
            McpSchema.CallToolRequest request) {
        return cartAssistantActionService.proposeQuantityChange(McpOwnerKeyContext.require(request), productId, newQuantity).toView();
    }

    @McpTool(name = "removeProduct", description = "Removes the product with the given id from the shopping cart")
    public PendingCartActionView proposeRemoval(
            @McpToolParam(description = "The id of the product to remove.", required = true) long productId,
            McpSchema.CallToolRequest request) {
        return cartAssistantActionService.proposeRemoval(McpOwnerKeyContext.require(request), productId).toView();
    }
}
