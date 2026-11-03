package com.thorben.janssen.piratestore.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * The support assistant used by {@link SupportChatPanel}. This class is the
 * only bridge to the separate {@code pirate-store-ai} application: it has no
 * dependency on Spring AI, {@code ChatClient} or any model provider, so that
 * application can be swapped or reconfigured without touching this one
 * (see README, "Architecture").
 *
 * <p>{@code ownerKey} is taken from the caller-supplied, server-generated
 * {@link SupportConversationContext} -- never from the browser or the model
 * -- and sent to {@code pirate-store-ai} as part of the request body.
 * {@code pirate-store-ai} in turn attaches it to every MCP tool call it
 * makes back into this app (see {@code support.tools.McpToolsConfig}), so it
 * never becomes a parameter the model itself supplies or even sees.
 */
@Service
public class SupportAssisstantService {

    private final RestClient restClient;

    public SupportAssisstantService(@Value("${pirate-store.ai-service.base-url}") String aiServiceBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(aiServiceBaseUrl).build();
    }

    public SupportResponse respond(SupportConversationContext context, String userMessage) {
        ChatRequest request = new ChatRequest(context.ownerKey(), context.conversationId(), userMessage);
        return restClient.post()
                .uri("/chat")
                .body(request)
                .retrieve()
                .body(SupportResponse.class);
    }

    private record ChatRequest(UUID ownerKey, UUID conversationId, String message) {
    }
}
