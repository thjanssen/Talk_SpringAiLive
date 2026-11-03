package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.cart.CartChangedEvent;
import com.thorben.janssen.piratestore.cart.CartSummary;
import com.thorben.janssen.piratestore.cart.CartUpdateBroadcaster;
import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.thorben.janssen.piratestore.support.cartaction.CartAssistantActionService;
import com.thorben.janssen.piratestore.support.cartaction.PendingCartActionView;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Reusable chat window shown in {@code MainLayout} on every view. It only
 * depends on {@link SupportAssisstantService} and {@link CartAssistantActionService}.
 */
public class SupportChatPanel extends Div {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ShoppingSession shoppingSession;
    private final SupportAssisstantService supportAssistantService;
    private final CartAssistantActionService cartAssistantActionService;
    private final CartUpdateBroadcaster broadcaster;
    private final Consumer<CartChangedEvent> cartListener = this::onCartChanged;

    private final Div messageList = new Div();
    private final Div errorArea = new Div();
    private final Span loadingIndicator = new Span("The Quartermaster is thinking...");
    private final TextField messageInput = new TextField();
    private final Button sendButton = new Button("Send");

    private CartSummary latestCartSummary;

    public SupportChatPanel(ShoppingSession shoppingSession, SupportAssisstantService supportAssistantService,
                             CartAssistantActionService cartAssistantActionService, CartUpdateBroadcaster broadcaster) {
        this.shoppingSession = shoppingSession;
        this.supportAssistantService = supportAssistantService;
        this.cartAssistantActionService = cartAssistantActionService;
        this.broadcaster = broadcaster;

        addClassName("chat-drawer");
        add(buildHeader());
        add(buildMessageArea());
        add(buildErrorArea());
        add(buildInputRow());

        shoppingSession.getChatHistory().forEach(this::renderMessage);
    }

    /** Opens or closes the drawer without affecting the currently displayed view. */
    public void toggleOpen() {
        if (getClassNames().contains("open")) {
            removeClassName("open");
        } else {
            addClassName("open");
            scrollToBottom();
        }
    }

    private Div buildHeader() {
        Div header = new Div();
        header.addClassName("chat-header");

        Span title = new Span("Ask the Quartermaster");
        title.addClassName("chat-title");
        Paragraph subtitle = new Paragraph("Shop support -- ask about products, your cart or an order.");
        subtitle.addClassName("chat-subtitle");

        Button closeButton = new Button(new Icon(VaadinIcon.CLOSE_SMALL));
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        closeButton.addClassName("chat-close-button");
        closeButton.addClickListener(event -> removeClassName("open"));

        Div titleBlock = new Div(title, subtitle);
        titleBlock.addClassName("chat-title-block");

        HorizontalLayout headerRow = new HorizontalLayout(titleBlock, closeButton);
        headerRow.addClassName("chat-header-row");
        header.add(headerRow);
        return header;
    }

    private Div buildMessageArea() {
        messageList.addClassName("chat-messages");
        loadingIndicator.addClassName("chat-loading");
        loadingIndicator.setVisible(false);
        Div wrapper = new Div(messageList, loadingIndicator);
        wrapper.addClassName("chat-messages-wrapper");
        return wrapper;
    }

    private Div buildErrorArea() {
        errorArea.addClassName("chat-error");
        errorArea.setVisible(false);
        return errorArea;
    }

    private Div buildInputRow() {
        messageInput.setPlaceholder("Type a message...");
        messageInput.addClassName("chat-input");
        messageInput.setWidthFull();
        messageInput.addKeyDownListener(Key.ENTER, event -> send());

        sendButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sendButton.addClickListener(event -> send());

        Div row = new Div(messageInput, sendButton);
        row.addClassName("chat-input-row");
        return row;
    }

    private void send() {
        String text = messageInput.getValue() == null ? "" : messageInput.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        hideError();
        messageInput.clear();

        ChatMessage userMessage = ChatMessage.user(text);
        shoppingSession.addChatMessage(userMessage);
        renderMessage(userMessage);

        setBusy(true);
        try {
            SupportConversationContext context = new SupportConversationContext(
                    shoppingSession.getOwnerKey(), shoppingSession.getConversationId());
            SupportResponse response = supportAssistantService.respond(context, text);

            if (response.hasPendingAction()) {
                if (response.message() != null && !response.message().isEmpty()) {
                    ChatMessage assistantMessage = ChatMessage.assistant(response.message());
                    shoppingSession.addChatMessage(assistantMessage);
                    renderMessage(assistantMessage);
                }
                renderPendingAction(response.pendingAction());
            } else {
                ChatMessage assistantMessage = ChatMessage.assistant(response.message());
                shoppingSession.addChatMessage(assistantMessage);
                renderMessage(assistantMessage);
            }
        } catch (Exception e) {
            showError("Sorry, the Quartermaster could not answer that: " + e.getMessage());
        } finally {
            setBusy(false);
        }
    }

    private void renderMessage(ChatMessage message) {
        Div bubble = new Div();
        bubble.addClassName("chat-message");
        bubble.addClassName(message.role() == ChatRole.USER ? "chat-message-user" : "chat-message-assistant");

        Span content = new Span(message.content());
        content.addClassName("chat-message-text");
        Span timestamp = new Span(TIME_FORMAT.format(message.timestamp()));
        timestamp.addClassName("chat-message-time");

        bubble.add(content, timestamp);
        messageList.add(bubble);
        scrollToBottom();
    }

    private void renderPendingAction(PendingCartActionView action) {
        Div card = new Div();
        card.addClassName("chat-action-card");

        Paragraph description = new Paragraph(action.description() + " (" + formatSignedPrice(action.priceImpact()) + ")");
        description.addClassName("chat-action-description");

        Button confirmButton = new Button("Confirm");
        confirmButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
        Button cancelButton = new Button("Cancel");
        cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        confirmButton.addClickListener(event -> {
            try {
                CartSummary summary = cartAssistantActionService.confirm(shoppingSession.getOwnerKey(), action.actionId());
                confirmButton.setEnabled(false);
                cancelButton.setEnabled(false);
                ChatMessage confirmation = ChatMessage.assistant("Done! Your cart now has "
                        + summary.totalQuantity() + " item(s) for a total of " + summary.totalPrice() + " gold coins.");
                shoppingSession.addChatMessage(confirmation);
                renderMessage(confirmation);
            } catch (Exception e) {
                showError(e.getMessage());
            }
        });
        cancelButton.addClickListener(event -> {
            try {
                cartAssistantActionService.cancel(shoppingSession.getOwnerKey(), action.actionId());
                confirmButton.setEnabled(false);
                cancelButton.setEnabled(false);
            } catch (Exception e) {
                showError(e.getMessage());
            }
        });

        HorizontalLayout actions = new HorizontalLayout(confirmButton, cancelButton);
        actions.addClassName("chat-action-buttons");

        card.add(description, actions);
        messageList.add(card);
        scrollToBottom();
    }

    private String formatSignedPrice(java.math.BigDecimal amount) {
        String sign = amount.signum() > 0 ? "+" : "";
        return sign + amount + " gold coins";
    }

    private void setBusy(boolean busy) {
        loadingIndicator.setVisible(busy);
        messageInput.setEnabled(!busy);
        sendButton.setEnabled(!busy);
    }

    private void showError(String message) {
        errorArea.setText(message);
        errorArea.setVisible(true);
    }

    private void hideError() {
        errorArea.setVisible(false);
    }

    private void scrollToBottom() {
        messageList.getElement().executeJs("this.scrollTop = this.scrollHeight;");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        broadcaster.register(shoppingSession.getOwnerKey(), cartListener);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        broadcaster.remove(shoppingSession.getOwnerKey(), cartListener);
    }

    private void onCartChanged(CartChangedEvent event) {
        // Kept as context for a future AI implementation (e.g. to answer "what's in
        // my cart" without an extra round trip); the stub does not use it yet.
        this.latestCartSummary = event.summary();
    }
}
