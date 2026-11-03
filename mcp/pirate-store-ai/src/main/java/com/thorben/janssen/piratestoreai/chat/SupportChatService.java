package com.thorben.janssen.piratestoreai.chat;

import com.thorben.janssen.piratestoreai.mcp.OwnerKeyContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds and runs the support {@code ChatClient}: RAG over {@code docs/*.md}
 * plus the product/cart tools fetched from pirate-store's MCP server (see
 * {@code ToolCallbackProvider}, auto-configured by {@code spring-ai-starter-mcp-client}).
 *
 * <p>{@code ownerKey} is attached to every outgoing MCP tool call via
 * {@code ChatClient.toolContext(...)} (see {@link OwnerKeyContext} for the
 * context key) -- never as a tool parameter, so the model never sees or
 * supplies it. Spring AI's MCP client copies the {@code ToolContext} into
 * the {@code _meta} of each outgoing tool call, where pirate-store's
 * {@code CartAssisstantTools} read it back out. Cart-mutating tool calls
 * only ever create a {@code PendingCartAction} on the pirate-store side;
 * this class merely captures the resulting view to attach to the response,
 * it never applies anything itself (FR-13).
 */
@Service
public class SupportChatService {

    private static final Set<String> CART_MUTATION_TOOLS = Set.of("addProduct", "changeQuantity", "removeProduct");

    private final ChatClient chatClient;
    private final ToolCallbackProvider mcpToolCallbackProvider;
    private final JsonMapper jsonMapper;

    public SupportChatService(ChatClient.Builder chatClientBuilder,
                               @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt,
                               ChatMemory chatMemory,
                               VectorStore vectorStore,
                               ToolCallbackProvider mcpToolCallbackProvider,
                               JsonMapper jsonMapper) {
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
        this.jsonMapper = jsonMapper;
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        SimpleLoggerAdvisor.builder().order(100).build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore).build())
                .build();
    }

    public ChatResponse respond(ChatRequest request) {
        AtomicReference<String> lastPendingActionJson = new AtomicReference<>();
        List<ToolCallback> tools = Arrays.stream(mcpToolCallbackProvider.getToolCallbacks())
                .<ToolCallback>map(tool -> CART_MUTATION_TOOLS.contains(tool.getToolDefinition().name())
                        ? new CapturingToolCallback(tool, lastPendingActionJson::set)
                        : tool)
                .toList();

        String text = chatClient.prompt(request.message())
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, request.conversationId()))
                .toolCallbacks(tools)
                .toolContext(Map.of(OwnerKeyContext.CONTEXT_KEY, request.ownerKey().toString()))
                .call()
                .content();

        String rawResult = lastPendingActionJson.get();
        if (rawResult == null) {
            return ChatResponse.text(text);
        }
        return new ChatResponse(text, extractPendingAction(rawResult));
    }

    /**
     * {@link org.springframework.ai.tool.ToolCallback#call} returns the MCP
     * result's {@code content} list, JSON-serialized as-is -- e.g.
     * {@code [{"type":"text","text":"{\"actionId\":...}"}]} -- not the tool's
     * return value directly. This unwraps that one level to recover the
     * {@code PendingCartActionView} JSON pirate-store's {@code CartAssisstantTools}
     * actually returned, then parses it into a plain map for {@link ChatResponse}.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPendingAction(String rawToolResult) {
        JsonNode contentBlocks = jsonMapper.readTree(rawToolResult);
        String pendingActionJson = contentBlocks.get(0).get("text").asText();
        return jsonMapper.readValue(pendingActionJson, Map.class);
    }
}
