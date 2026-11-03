package com.thorben.janssen.piratestore.session;

import com.thorben.janssen.piratestore.support.ChatMessage;
import org.springframework.ai.chat.model.ChatResponse;
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
    private ChatResponse pausedReply;

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

    /** Keeps the reply that waits for the user to confirm a cart change, out of the client's reach. */
    public void setPausedReply(ChatResponse pausedReply) {
        this.pausedReply = pausedReply;
    }

    public Optional<ChatResponse> getPausedReply() {
        return Optional.ofNullable(pausedReply);
    }

    /** Returns and clears the paused reply, so its tool calls can only run once. */
    public Optional<ChatResponse> takePausedReply() {
        ChatResponse reply = this.pausedReply;
        this.pausedReply = null;
        return Optional.ofNullable(reply);
    }
}
