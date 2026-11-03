package com.thorben.janssen.piratestore.support.tools;

import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.thorben.janssen.piratestore.support.cartaction.CartAssistantActionService;
import com.thorben.janssen.piratestore.support.cartaction.PendingCartAction;
import com.thorben.janssen.piratestore.support.cartaction.PendingCartActionView;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CartAssistantTools {

    private CartAssistantActionService cartAssistantActionService;
    private ShoppingSession shoppingSession;

    public CartAssistantTools(CartAssistantActionService cartAssistantActionService, ShoppingSession shoppingSession) {
        this.cartAssistantActionService = cartAssistantActionService;
        this.shoppingSession = shoppingSession;
    }

    @Tool(name = "proposeRemoval", description = "Remove the product with the given id from the shopping cart.", returnDirect = true)
    public String proposeRemoval(long productId, ToolContext toolContext) {
        propose(cartAssistantActionService.proposeRemoval(OwnerKeyContext.require(toolContext.getContext()), productId));
        return "";
    }

    @Tool(name = "proposeQuantityChange", description = "Change the quantity of the product with the given id in the shopping cart.")
    public PendingCartActionView proposeQuantityChange(long productId, int newQuantity, ToolContext toolContext) {
        return propose(cartAssistantActionService.proposeQuantityChange(OwnerKeyContext.require(toolContext.getContext()), productId, newQuantity));
    }

    @Tool(name = "proposeAdd", description = "Add the product with the give id in the given quantity to the shopping cart.")
    public PendingCartActionView proposeAdd(long productId, int quantity, ToolContext toolContext) {
        return propose(cartAssistantActionService.proposeAdd(OwnerKeyContext.require(toolContext.getContext()), productId, quantity));
    }

    private PendingCartActionView propose(PendingCartAction action) {
        shoppingSession.recordProposedCartAction(action.getActionId());
        return action.toView();
    }
}
