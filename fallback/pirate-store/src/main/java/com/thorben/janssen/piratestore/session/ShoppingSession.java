package com.thorben.janssen.piratestore.session;

import com.thorben.janssen.piratestore.support.ChatMessage;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-Vaadin-session context. This is the single source of truth for the
 * session's {@code ownerKey} and {@code conversationId}: views and the
 * support chat component read them directly, instead of generating or
 * receiving them from the client, the model or a URL parameter. Once the AI
 * part of this demo is wired in, {@code SupportAssisstantService} reads
 * {@code ownerKey} from here too, but only to attach it to the
 * {@code ChatClient} call's {@code toolContext(...)} (see
 * {@code support.tools.OwnerKeyContext}) -- the tool implementation itself
 * never calls {@link #getOwnerKey()}, it resolves the value already carried
 * in the {@code ToolContext} instead.
 *
 * <p>Uses an explicit scoped proxy (rather than the plain
 * {@code @VaadinSessionScope}) for the same reason as
 * {@code PendingCartActionStore}: it is injected into {@code SupportAssisstantService}
 * and will also be injected into the future AI tool bean (e.g.
 * {@code CartAssisstantTools}, to call {@link #recordProposedCartAction} once
 * it has proposed a change) -- both singletons wired while the application
 * starts up, before any {@code VaadinSession} exists. The proxy defers the
 * actual session lookup to when a method is called, at which point a session
 * is active.
 */
@Component
@Scope(scopeName = "vaadin-session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ShoppingSession {

    private final UUID ownerKey = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private final List<String> orderNumbers = new ArrayList<>();
    private UUID proposedCartActionId;

    public UUID getOwnerKey() {
        return ownerKey;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public List<ChatMessage> getChatHistory() {
        return Collections.unmodifiableList(chatHistory);
    }

    public void addChatMessage(ChatMessage message) {
        chatHistory.add(message);
    }

    public List<String> getOrderNumbers() {
        return Collections.unmodifiableList(orderNumbers);
    }

    public void addOrderNumber(String orderNumber) {
        orderNumbers.add(orderNumber);
    }

    public boolean ownsOrder(String orderNumber) {
        return orderNumbers.contains(orderNumber);
    }

    /**
     * Records the id of a {@code PendingCartAction} an AI tool just proposed
     * during the current chat turn, so {@code SupportAssisstantService} can
     * attach it to the {@code SupportResponse} after the model call returns
     * (see {@code support.CartAssisstantTools}).
     */
    public void recordProposedCartAction(UUID actionId) {
        this.proposedCartActionId = actionId;
    }

    /**
     * Returns and clears the id recorded by {@link #recordProposedCartAction},
     * if any. Cleared on read so a stale id from an earlier turn is never
     * attached to an unrelated response.
     */
    public Optional<UUID> consumeProposedCartAction() {
        UUID actionId = this.proposedCartActionId;
        this.proposedCartActionId = null;
        return Optional.ofNullable(actionId);
    }
}
