package com.thorben.janssen.piratestore.session;

import com.thorben.janssen.piratestore.support.ChatMessage;
import com.thorben.janssen.piratestore.support.tools.CartChangePreview;
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
 * receiving them from the client, the model or a URL parameter.
 * {@code SupportAssisstantService} sends them to the pirate-store-ai application.
 *
 * <p>Uses an explicit scoped proxy (rather than the plain
 * {@code @VaadinSessionScope}) because it is injected into the singleton
 * {@code SupportAssisstantService}, which is wired while the application starts
 * up, before any {@code VaadinSession} exists. The proxy defers the actual
 * session lookup to when a method is called, at which point a session is active.
 */
@Component
@Scope(scopeName = "vaadin-session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ShoppingSession {

    private final UUID ownerKey = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private final List<String> orderNumbers = new ArrayList<>();
    private List<CartChangePreview> pendingCartChanges;

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

    /** Keeps the cart changes that wait for confirmation, so a reloaded page shows them again. */
    public void setPendingCartChanges(List<CartChangePreview> changes) {
        this.pendingCartChanges = changes;
    }

    public Optional<List<CartChangePreview>> getPendingCartChanges() {
        return Optional.ofNullable(pendingCartChanges);
    }

    /** Returns and clears the pending cart changes, so the user can only decide once. */
    public Optional<List<CartChangePreview>> takePendingCartChanges() {
        List<CartChangePreview> changes = this.pendingCartChanges;
        this.pendingCartChanges = null;
        return Optional.ofNullable(changes);
    }
}
