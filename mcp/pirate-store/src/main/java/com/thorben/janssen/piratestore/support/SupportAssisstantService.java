package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.thorben.janssen.piratestore.support.SupportEvent.CartToolCall;
import com.thorben.janssen.piratestore.support.tools.CartChangePreview;
import com.thorben.janssen.piratestore.support.tools.CartChangePreviewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * The support assistant used by {@link SupportChatPanel}. This class is the
 * only bridge to the separate {@code pirate-store-ai} application, which runs
 * the {@code ChatClient} and keeps a reply that waits for confirmation.
 *
 * <p>The {@code ownerKey} comes from the server-side {@link ShoppingSession}
 * and only travels over this server-to-server call. pirate-store-ai attaches it
 * to every MCP tool call it makes back into this application.
 *
 * <p>All methods must be called while the Vaadin session is available, because
 * they read the session's ids here, not in the stream.
 */
@Service
public class SupportAssisstantService {

    private final WebClient webClient;
    private final ShoppingSession shoppingSession;
    private final CartChangePreviewService cartChangePreviewService;

    public SupportAssisstantService(@Value("${pirate-store.ai-service.base-url}") String aiServiceBaseUrl,
                                    ShoppingSession shoppingSession,
                                    CartChangePreviewService cartChangePreviewService) {
        this.webClient = WebClient.builder().baseUrl(aiServiceBaseUrl).build();
        this.shoppingSession = shoppingSession;
        this.cartChangePreviewService = cartChangePreviewService;
    }

    /** Streams the assistant's answer. A pause the user ignored is resolved by pirate-store-ai. */
    public Flux<SupportEvent> streamResponse(String userMessage) {
        return post("/chat", new ChatRequest(shoppingSession.getOwnerKey(), shoppingSession.getConversationId(), userMessage));
    }

    /**
     * Describes the cart changes that wait for confirmation.
     *
     * @throws RuntimeException if one of them cannot succeed, with a message explaining why
     */
    public List<CartChangePreview> previewChanges(List<CartToolCall> toolCalls) {
        return toolCalls.stream()
                .map(toolCall -> cartChangePreviewService.preview(
                        shoppingSession.getOwnerKey(), toolCall.name(), toolCall.arguments()))
                .toList();
    }

    /** Runs the paused tool calls and streams the assistant's answer to their results. */
    public Flux<SupportEvent> approve() {
        return decide(true, null);
    }

    /** Declines the paused tool calls and streams the assistant's reaction. */
    public Flux<SupportEvent> reject() {
        return decide(false, null);
    }

    /** Declines the paused tool calls for the given reason and streams the assistant's reaction. */
    public Flux<SupportEvent> reject(String reason) {
        return decide(false, reason);
    }

    private Flux<SupportEvent> decide(boolean approved, String reason) {
        return post("/chat/decision", new DecisionRequest(
                shoppingSession.getOwnerKey(), shoppingSession.getConversationId(), approved, reason));
    }

    private Flux<SupportEvent> post(String uri, Object request) {
        return webClient.post()
                .uri(uri)
                .accept(MediaType.APPLICATION_NDJSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(SupportEvent.class);
    }

    private record ChatRequest(UUID ownerKey, UUID conversationId, String message) {
    }

    private record DecisionRequest(UUID ownerKey, UUID conversationId, boolean approved, String reason) {
    }
}
