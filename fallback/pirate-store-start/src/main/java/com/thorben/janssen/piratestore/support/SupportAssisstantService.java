package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.thorben.janssen.piratestore.support.tools.CartAssistantTools;
import com.thorben.janssen.piratestore.support.tools.CartChangePreview;
import com.thorben.janssen.piratestore.support.tools.CartChangePreviewService;
import com.thorben.janssen.piratestore.support.tools.OwnerKeyContext;
import com.thorben.janssen.piratestore.support.tools.ProductTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The support assistant used by {@link SupportChatPanel}.
 *
 * <p>A cart tool only runs after the user confirmed it: the stream of a response
 * that wants to call one ends with a paused reply (see {@link #isPaused}). The
 * caller keeps that reply on the server and continues with {@link #approve},
 * {@link #reject} or {@link #abandon}. The {@code ownerKey} travels in the tool
 * context, so the model never sees or supplies it.
 */
@Service
public class SupportAssisstantService {

    // private static final String DECLINED_MESSAGE = "The user clicked Cancel and does not want this change. Nothing went wrong. Do not retry it unless they ask again.";
    // private static final String MOVED_ON_MESSAGE = "The user moved on without deciding.";

    // private final ShoppingSession shoppingSession;
    // private final SessionService sessionService;
    // private final CartChangePreviewService cartChangePreviewService;
    // private final ToolCallingManager toolCallingManager;
    // private final List<ToolCallback> toolCallbacks;
    
    public SupportAssisstantService(
            CartChangePreviewService cartChangePreviewService,
            ShoppingSession shoppingSession,
            SessionService sessionService,
            ToolCallingManager toolCallingManager) {
        // this.toolCallingManager = toolCallingManager;
        // this.toolCallbacks = List.of(ToolCallbacks.from(productTools, cartAssistantTools));
        // this.shoppingSession = shoppingSession;
        // this.sessionService = sessionService;
        // this.cartChangePreviewService = cartChangePreviewService;
    }

    /**
     * Streams the assistant's answer. Must be called while the Vaadin session
     * is available, because it reads the session's ids here, not in the stream.
     */
    public Flux<ChatResponse> streamResponse(String userMessage) {
        return Flux.just(new ChatResponse(List.of(new Generation(
            new AssistantMessage("The crew is busy, but they will answer soon. Please wait...")))));
    }



    /** Runs the paused tool calls and streams the assistant's answer to their results. */
    public Flux<ChatResponse> approve(ChatResponse pausedReply) {
        return Flux.just();
    }

    /** Declines the paused tool calls and streams the assistant's reaction. */
    public Flux<ChatResponse> reject(ChatResponse pausedReply) {
        return Flux.just();
    }

    /** Declines the paused tool calls for the given reason and streams the assistant's reaction. */
    public Flux<ChatResponse> reject(ChatResponse pausedReply, String reason) {
        return Flux.just();
    }

    /**
     * Resolves a pause the user ignored, because the model providers expect a
     * result for every tool call in the conversation.
     */
    public void abandon(ChatResponse pausedReply) {
    }

    /** Whether this last chunk of a stream waits for the user to confirm a cart change. */
    public boolean isPaused(ChatResponse lastChunk) {
        return false;
    }

    /**
     * Describes the cart changes a paused reply wants to make.
     *
     * @throws RuntimeException if one of them cannot succeed, with a message explaining why
     */
    public List<CartChangePreview> previewPausedCalls(ChatResponse pausedReply) {
        return new ArrayList<>();
    }

    /** Returns the text of a streamed chunk, which is empty for some chunks. */
    public static String textOf(ChatResponse chunk) {
        if (chunk.getResult() == null || chunk.getResult().getOutput().getText() == null) {
            return "";
        }
        return chunk.getResult().getOutput().getText();
    }


































    // private String sessionId() {
    //     return shoppingSession.getConversationId().toString();
    // }

    // /** Whether this last chunk of a stream waits for the user to confirm a cart change. */
    // public boolean isPaused(ChatResponse lastChunk) {
    //     return ToolCallDecisions.isAwaitingDecision(lastChunk);
    // }

    // /**
    //  * Describes the cart changes a paused reply wants to make.
    //  *
    //  * @throws RuntimeException if one of them cannot succeed, with a message explaining why
    //  */
    // public List<CartChangePreview> previewPausedCalls(ChatResponse pausedReply) {
    //     AssistantMessage reply = pausedReply.getResult().getOutput();
    //     List<String> keys = ToolCallDecisions.toolCallKeys(reply);
    //     List<String> awaiting = ToolCallDecisions.awaitingDecision(pausedReply);

    //     List<CartChangePreview> previews = new ArrayList<>();
    //     for (int i = 0; i < keys.size(); i++) {
    //         if (awaiting.contains(keys.get(i))) {
    //             previews.add(cartChangePreviewService.preview(shoppingSession.getOwnerKey(), reply.getToolCalls().get(i)));
    //         }
    //     }
    //     return previews;
    // }

    // /** Runs the paused tool calls and streams the assistant's answer to their results. */
    // public Flux<ChatResponse> approve(ChatResponse pausedReply) {
    //     return stream(this.chatClient.prompt().messages(executeDecisions(pausedReply, null)));
    // }

    // /** Declines the paused tool calls and streams the assistant's reaction. */
    // public Flux<ChatResponse> reject(ChatResponse pausedReply) {
    //     return reject(pausedReply, DECLINED_MESSAGE);
    // }

    // /** Declines the paused tool calls for the given reason and streams the assistant's reaction. */
    // public Flux<ChatResponse> reject(ChatResponse pausedReply, String reason) {
    //     return stream(this.chatClient.prompt().messages(executeDecisions(pausedReply, reason)));
    // }

    // /**
    //  * Resolves a pause the user ignored, because the model providers expect a
    //  * result for every tool call in the conversation.
    //  */
    // public void abandon(ChatResponse pausedReply) {
    //     sessionService.appendMessage(sessionId(), executeDecisions(pausedReply, MOVED_ON_MESSAGE));
    // }

    // /** Blocking variant of {@link #streamResponse} that waits for the complete answer. */
    // public SupportResponse respond(SupportConversationContext context, String userMessage) {
    //     return new SupportResponse(streamResponse(userMessage)
    //             .map(SupportAssisstantService::textOf)
    //             .collect(Collectors.joining())
    //             .block());
    // }

    // private Flux<ChatResponse> stream(ChatClient.ChatClientRequestSpec request) {
    //     return request
    //             .advisors(SessionAdvisors.session(sessionId()))
    //             .toolContext(toolContext())
    //             .stream()
    //             .chatResponse();
    // }

    // /**
    //  * Allows or denies the calls waiting for a decision and returns the tool
    //  * results. Calls that need no confirmation always run, because Spring AI
    //  * expects a decision for every call of the reply.
    //  */
    // private Message executeDecisions(ChatResponse pausedReply, String denyMessage) {
    //     List<String> awaiting = ToolCallDecisions.awaitingDecision(pausedReply);
    //     ToolCallDecision[] decisions = ToolCallDecisions.toolCallKeys(pausedReply.getResult().getOutput()).stream()
    //             .map(key -> denyMessage != null && awaiting.contains(key)
    //                     ? ToolCallDecision.deny(key, denyMessage)
    //                     : ToolCallDecision.allow(key))
    //             .toArray(ToolCallDecision[]::new);

    //     ToolCallingChatOptions options = ToolCallingChatOptions.builder()
    //             .toolCallbacks(toolCallbacks)
    //             .toolContext(toolContext())
    //             .build();
    //     List<Message> history = toolCallingManager
    //             .executeToolCalls(new Prompt(List.of(), options), pausedReply, ToolCallDecisions.of(decisions))
    //             .conversationHistory();
    //     return history.get(history.size() - 1);
    // }

    // private Map<String, Object> toolContext() {
    //     return Map.of(OwnerKeyContext.CONTEXT_KEY, shoppingSession.getOwnerKey().toString());
    // }
}
