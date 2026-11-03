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

    private ShoppingSession shoppingSession;

    public CartAssistantTools(CartAssistantActionService cartAssistantActionService, ShoppingSession shoppingSession) {
        this.shoppingSession = shoppingSession;
    }

    private PendingCartActionView propose(PendingCartAction action) {
        shoppingSession.recordProposedCartAction(action.getActionId());
        return action.toView();
    }
}
