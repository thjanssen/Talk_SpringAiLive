package com.thorben.janssen.piratestoreai.chat;

import com.thorben.janssen.piratestoreai.mcp.OwnerKeyContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and runs the support {@code ChatClient}: RAG over {@code docs/*.md},
 * session memory and the product/cart tools fetched from pirate-store's MCP
 * server.
 *
 * <p>A cart tool only runs after the user confirmed it: the policy from
 * {@code spring.ai.tools.confirmation.tools} pauses the tool-calling loop. This
 * class keeps the paused reply per conversation, sends the waiting tool calls to
 * pirate-store and continues with its {@link #decide decision}. A new message
 * declines a pause the user ignored.
 *
 * <p>{@code ownerKey} is attached via {@code toolContext(...)}, never as a tool
 * parameter, so the model never sees or supplies it. Spring AI's MCP client
 * copies the tool context into the {@code _meta} of each outgoing tool call.
 */
@Service
public class SupportChatService {

    private static final String DECLINED_MESSAGE = "The user clicked Cancel and does not want this change. Nothing went wrong. Do not retry it unless they ask again.";
    private static final String MOVED_ON_MESSAGE = "The user moved on without deciding.";

    private final ChatClient chatClient;
    private final SessionService sessionService;
    private final ToolCallbackProvider mcpToolCallbackProvider;
    private final ToolCallingManager toolCallingManager;
    private final Map<UUID, ChatResponse> pausedReplies = new ConcurrentHashMap<>();

    public SupportChatService(ChatClient.Builder chatClientBuilder,
                               @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                               SessionService sessionService,
                               VectorStore vectorStore,
                               ToolCallbackProvider mcpToolCallbackProvider,
                               ToolCallingManager toolCallingManager) {
        this.sessionService = sessionService;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
        this.toolCallingManager = toolCallingManager;
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(SessionAdvisors.builder(sessionService).build())
                .defaultAdvisors(
                        SimpleLoggerAdvisor.builder().order(100).build(),
                        QuestionAnswerAdvisor.builder(vectorStore).build())
                .build();
    }

    /** Streams the answer to a message, after declining a pause the user ignored. */
    public Flux<ChatEvent> respond(ChatRequest request) {
        ChatResponse ignoredPause = pausedReplies.remove(request.conversationId());
        if (ignoredPause != null) {
            sessionService.appendMessage(request.conversationId().toString(),
                    executeDecisions(ignoredPause, request.ownerKey(), MOVED_ON_MESSAGE));
        }
        return stream(this.chatClient.prompt(request.message()), request.ownerKey(), request.conversationId());
    }

    /**
     * Runs or declines the paused tool calls and streams the assistant's answer.
     * The paused reply is removed first, so the same calls can only run once. A
     * decision without a paused reply has no effect.
     */
    public Flux<ChatEvent> decide(DecisionRequest request) {
        ChatResponse pausedReply = pausedReplies.remove(request.conversationId());
        if (pausedReply == null) {
            return Flux.empty();
        }
        String denyMessage = request.approved() ? null
                : request.reason() != null ? request.reason() : DECLINED_MESSAGE;
        Message toolResults = executeDecisions(pausedReply, request.ownerKey(), denyMessage);
        return stream(this.chatClient.prompt().messages(toolResults), request.ownerKey(), request.conversationId());
    }

    private Flux<ChatEvent> stream(ChatClient.ChatClientRequestSpec prompt, UUID ownerKey, UUID conversationId) {
        return prompt
                .advisors(SessionAdvisors.session(conversationId.toString()))
                .tools(mcpToolCallbackProvider)
                .toolContext(toolContext(ownerKey))
                .stream()
                .chatResponse()
                .map(chunk -> toEvent(chunk, conversationId))
                .filter(event -> event.awaitingConfirmation() != null || !event.text().isEmpty());
    }

    private ChatEvent toEvent(ChatResponse chunk, UUID conversationId) {
        if (ToolCallDecisions.isAwaitingDecision(chunk)) {
            pausedReplies.put(conversationId, chunk);
            return ChatEvent.paused(awaitingToolCalls(chunk));
        }
        if (chunk.getResult() == null || chunk.getResult().getOutput().getText() == null) {
            return ChatEvent.text("");
        }
        return ChatEvent.text(chunk.getResult().getOutput().getText());
    }

    private List<ChatEvent.ToolCallRequest> awaitingToolCalls(ChatResponse pausedReply) {
        AssistantMessage reply = pausedReply.getResult().getOutput();
        List<String> keys = ToolCallDecisions.toolCallKeys(reply);
        List<String> awaiting = ToolCallDecisions.awaitingDecision(pausedReply);

        List<ChatEvent.ToolCallRequest> toolCalls = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            if (awaiting.contains(keys.get(i))) {
                AssistantMessage.ToolCall toolCall = reply.getToolCalls().get(i);
                toolCalls.add(new ChatEvent.ToolCallRequest(toolCall.name(), toolCall.arguments()));
            }
        }
        return toolCalls;
    }

    /**
     * Allows or denies the calls waiting for a decision and returns the tool
     * results. Calls that need no confirmation always run, because Spring AI
     * expects a decision for every call of the reply.
     */
    private Message executeDecisions(ChatResponse pausedReply, UUID ownerKey, String denyMessage) {
        List<String> awaiting = ToolCallDecisions.awaitingDecision(pausedReply);
        ToolCallDecision[] decisions = ToolCallDecisions.toolCallKeys(pausedReply.getResult().getOutput()).stream()
                .map(key -> denyMessage != null && awaiting.contains(key)
                        ? ToolCallDecision.deny(key, denyMessage)
                        : ToolCallDecision.allow(key))
                .toArray(ToolCallDecision[]::new);

        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(List.of(mcpToolCallbackProvider.getToolCallbacks()))
                .toolContext(toolContext(ownerKey))
                .build();
        List<Message> history = toolCallingManager
                .executeToolCalls(new Prompt(List.of(), options), pausedReply, ToolCallDecisions.of(decisions))
                .conversationHistory();
        return history.get(history.size() - 1);
    }

    private Map<String, Object> toolContext(UUID ownerKey) {
        return Map.of(OwnerKeyContext.CONTEXT_KEY, ownerKey.toString());
    }
}
