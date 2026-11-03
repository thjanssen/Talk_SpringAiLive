package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.cart.CartChangedEvent;
import com.thorben.janssen.piratestore.cart.CartSummary;
import com.thorben.janssen.piratestore.cart.CartUpdateBroadcaster;
import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.thorben.janssen.piratestore.support.tools.CartChangePreview;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reusable chat window shown in {@code MainLayout} on every view. It only
 * depends on {@link SupportAssisstantService}.
 */
public class SupportChatPanel extends Div {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ShoppingSession shoppingSession;
    private final SupportAssisstantService supportAssistantService;
    private final CartUpdateBroadcaster broadcaster;
    private final Consumer<CartChangedEvent> cartListener = this::onCartChanged;

    private final Div messageList = new Div();
    private final Div errorArea = new Div();
    private final Span loadingIndicator = new Span("The Quartermaster is thinking...");
    private final TextField messageInput = new TextField();
    private final Button sendButton = new Button("Send");

    private CartSummary latestCartSummary;
    private Disposable activeResponse;
    private SupportEvent lastChunk;
    private Div streamingBubble;
    private Span streamingContent;
    private final StringBuilder streamedText = new StringBuilder();
    private Runnable closeConfirmationCard = () -> { };

    public SupportChatPanel(ShoppingSession shoppingSession, SupportAssisstantService supportAssistantService,
                             CartUpdateBroadcaster broadcaster) {
        this.shoppingSession = shoppingSession;
        this.supportAssistantService = supportAssistantService;
        this.broadcaster = broadcaster;

        addClassName("chat-drawer");
        add(buildHeader());
        add(buildMessageArea());
        add(buildErrorArea());
        add(buildInputRow());

        shoppingSession.getChatHistory().forEach(this::renderMessage);
        shoppingSession.getPendingCartChanges().ifPresent(this::renderConfirmationCard);
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

        closeConfirmationCard.run();
        shoppingSession.takePendingCartChanges();
        streamAnswer(() -> supportAssistantService.streamResponse(text));
    }

    /** Subscribes to an answer and renders it as it arrives, without blocking the UI thread. */
    private void streamAnswer(Supplier<Flux<SupportEvent>> answer) {
        setBusy(true);
        UI ui = UI.getCurrent();
        try {
            activeResponse = answer.get().subscribe(
                    chunk -> ui.access(() -> appendChunk(chunk)),
                    error -> ui.access(() -> finishResponse(error)),
                    () -> ui.access(() -> finishResponse(null)));
        } catch (Exception e) {
            finishResponse(e);
        }
    }

    /** Runs under the session lock for every chunk of the streamed answer. */
    private void appendChunk(SupportEvent chunk) {
        lastChunk = chunk;
        String text = chunk.text();
        if (text == null || text.isEmpty()) {
            return;
        }
        if (streamingBubble == null) {
            loadingIndicator.setVisible(false);
            streamingContent = createContent("");
            streamingBubble = createBubble(ChatRole.ASSISTANT, streamingContent);
            messageList.add(streamingBubble);
        }
        streamedText.append(text);
        streamingContent.setText(streamedText.toString());
        scrollToBottom();
    }

    private void finishResponse(Throwable error) {
        if (streamingBubble != null) {
            ChatMessage assistantMessage = ChatMessage.assistant(streamedText.toString());
            shoppingSession.addChatMessage(assistantMessage);
            streamingBubble.add(createTimestamp(assistantMessage));
        }
        SupportEvent last = lastChunk;
        lastChunk = null;
        streamingBubble = null;
        streamingContent = null;
        streamedText.setLength(0);
        activeResponse = null;

        if (error != null) {
            showError("Sorry, the Quartermaster could not answer that: " + error.getMessage());
        } else if (last != null && last.isPaused()) {
            askForConfirmation(last);
        }
        if (activeResponse == null) {
            setBusy(false);
        }
        scrollToBottom();
    }

    /**
     * Shows the paused cart changes for confirmation. If one of them cannot
     * succeed, it declines them right away, so the assistant explains why.
     */
    private void askForConfirmation(SupportEvent pause) {
        List<CartChangePreview> previews;
        try {
            previews = supportAssistantService.previewChanges(pause.awaitingConfirmation());
        } catch (RuntimeException e) {
            streamAnswer(() -> supportAssistantService.reject(e.getMessage()));
            return;
        }
        shoppingSession.setPendingCartChanges(previews);
        renderConfirmationCard(previews);
    }

    private void renderMessage(ChatMessage message) {
        Div bubble = createBubble(message.role(), createContent(message.content()));
        bubble.add(createTimestamp(message));
        messageList.add(bubble);
        scrollToBottom();
    }

    private Div createBubble(ChatRole role, Span content) {
        Div bubble = new Div(content);
        bubble.addClassName("chat-message");
        bubble.addClassName(role == ChatRole.USER ? "chat-message-user" : "chat-message-assistant");
        return bubble;
    }

    private Span createContent(String text) {
        Span content = new Span(text);
        content.addClassName("chat-message-text");
        return content;
    }

    private Span createTimestamp(ChatMessage message) {
        Span timestamp = new Span(TIME_FORMAT.format(message.timestamp()));
        timestamp.addClassName("chat-message-time");
        return timestamp;
    }

    private void renderConfirmationCard(List<CartChangePreview> previews) {
        Div card = new Div();
        card.addClassName("chat-action-card");

        for (CartChangePreview preview : previews) {
            Paragraph description = new Paragraph(preview.description() + " (" + formatSignedPrice(preview.priceImpact()) + ")");
            description.addClassName("chat-action-description");
            card.add(description);
        }

        Button confirmButton = new Button("Confirm");
        confirmButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
        Button cancelButton = new Button("Cancel");
        cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        closeConfirmationCard = () -> {
            confirmButton.setEnabled(false);
            cancelButton.setEnabled(false);
        };
        confirmButton.addClickListener(event -> decide(supportAssistantService::approve));
        cancelButton.addClickListener(event -> decide(supportAssistantService::reject));

        HorizontalLayout actions = new HorizontalLayout(confirmButton, cancelButton);
        actions.addClassName("chat-action-buttons");

        card.add(actions);
        messageList.add(card);
        scrollToBottom();
    }

    /** Clears the pending changes first, so a second click cannot send a second decision. */
    private void decide(Supplier<Flux<SupportEvent>> decision) {
        hideError();
        closeConfirmationCard.run();
        shoppingSession.takePendingCartChanges().ifPresent(changes -> streamAnswer(decision));
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
        if (activeResponse != null) {
            activeResponse.dispose();
            lastChunk = null;
            finishResponse(null);
        }
    }

    private void onCartChanged(CartChangedEvent event) {
        // Kept as context for a future AI implementation (e.g. to answer "what's in
        // my cart" without an extra round trip); the stub does not use it yet.
        this.latestCartSummary = event.summary();
    }
}
