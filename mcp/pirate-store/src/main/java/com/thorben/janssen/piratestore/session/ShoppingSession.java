package com.thorben.janssen.piratestore.session;

import com.thorben.janssen.piratestore.support.ChatMessage;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Per-Vaadin-session context. This is the single source of truth for the
 * session's {@code ownerKey} and {@code conversationId}: every view and the
 * support chat component read these values from here instead of generating
 * or receiving them from the client or the model. {@code ownerKey} is handed
 * to the (separate) pirate-store-ai application only as part of the request
 * body of {@code SupportAssisstantService}'s outbound HTTP call -- never
 * accepted as a tool parameter the model could supply (see
 * {@code support.tools.McpOwnerKeyContext}).
 *
 * <p>Only Vaadin UI components inject this bean, and those are always
 * resolved within an active session, so the plain {@code @VaadinSessionScope}
 * (no scoped proxy) is sufficient here.
 */
@Component
@VaadinSessionScope
public class ShoppingSession {

    private final UUID ownerKey = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private final List<String> orderNumbers = new ArrayList<>();

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
}
