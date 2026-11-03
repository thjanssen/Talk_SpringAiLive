package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.thorben.janssen.piratestore.support.cartaction.CartAssistantActionService;
import org.springframework.stereotype.Service;

/**
 * The support assistant used by {@link SupportChatPanel}.
 *
 * <p>Currently a stub. Once a {@code ChatClient} is wired in here, attach
 * {@code shoppingSession.getOwnerKey()} to every call via
 * {@code .toolContext(Map.of(com.thorben.janssen.piratestore.support.tools.OwnerKeyContext.CONTEXT_KEY,
 * shoppingSession.getOwnerKey().toString()))} -- see that class's Javadoc for
 * how an {@code @Tool} method then reads it back out, never as a declared
 * tool parameter.
 */
@Service
public class SupportAssisstantService {

    private final ShoppingSession shoppingSession;
    private final CartAssistantActionService cartAssistantActionService;

    public SupportAssisstantService(CartAssistantActionService cartAssistantActionService, ShoppingSession shoppingSession) {
        this.cartAssistantActionService = cartAssistantActionService;
        this.shoppingSession = shoppingSession;
    }

    public SupportResponse respond(SupportConversationContext context, String userMessage) {
        var response = "The support crew hasn't entered the ship.";

        // If a tool proposed a cart change during this call, attach its view so
        // the chat panel renders the Confirm/Cancel controls (FR-12/FR-13)
        // instead of silently leaving the proposal unconfirmed.
        return shoppingSession.consumeProposedCartAction()
                .flatMap(cartAssistantActionService::findView)
                .map(view -> new SupportResponse(response, view))
                .orElseGet(() -> SupportResponse.text(response));
    }
}
