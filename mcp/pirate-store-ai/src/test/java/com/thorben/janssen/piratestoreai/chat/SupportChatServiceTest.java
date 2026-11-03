package com.thorben.janssen.piratestoreai.chat;

import com.thorben.janssen.piratestoreai.mcp.OwnerKeyContext;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallConfirmationPolicy;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Runs the confirmation flow with a scripted model and a recording tool, so the
 * rules that protect the cart are checked without an LLM or a running MCP server.
 */
class SupportChatServiceTest {

    private static final String CART_TOOL = "addToCart";

    private final ScriptedChatModel model = new ScriptedChatModel();
    private final RecordingToolCallback cartTool = new RecordingToolCallback();
    private final UUID ownerKey = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private SessionService sessionService;
    private SupportChatService service;

    @BeforeEach
    void setUp() {
        sessionService = DefaultSessionService.builder()
                .sessionRepository(InMemorySessionRepository.builder().build())
                .build();
        ToolCallingManager manager = ToolCallingManager.builder()
                .toolCallConfirmationPolicy(ToolCallConfirmationPolicy.forTools(CART_TOOL))
                .build();
        ChatClient.Builder builder = ChatClient.builder(model, ObservationRegistry.NOOP, null, null,
                ToolCallingAdvisor.builder().toolCallingManager(manager));
        ToolCallbackProvider provider = () -> new ToolCallback[]{cartTool};
        service = new SupportChatService(builder, new ByteArrayResource("You are a pirate.".getBytes()),
                sessionService, mock(VectorStore.class), provider, manager);
    }

    @Test
    void aCartToolCallPausesWithoutRunningAndSendsTheCallToTheStore() {
        List<ChatEvent> events = askToAdd();

        ChatEvent last = events.get(events.size() - 1);
        assertThat(last.awaitingConfirmation()).containsExactly(
                new ChatEvent.ToolCallRequest(CART_TOOL, "{\"productId\":1,\"quantity\":2}"));
        assertThat(cartTool.calls).isEmpty();
    }

    @Test
    void approvingRunsTheToolOnceWithTheOwnerKeyInTheToolContext() {
        askToAdd();

        model.answers("Aye, added!");
        String answer = textOf(service.decide(decision(true, null)));

        assertThat(answer).isEqualTo("Aye, added!");
        assertThat(cartTool.calls).containsExactly("{\"productId\":1,\"quantity\":2}");
        assertThat(cartTool.ownerKeys).containsExactly(ownerKey.toString());
    }

    @Test
    void aSecondDecisionCannotRunTheToolAgain() {
        askToAdd();
        model.answers("Aye, added!");
        textOf(service.decide(decision(true, null)));

        List<ChatEvent> second = service.decide(decision(true, null)).collectList().block();

        assertThat(second).isEmpty();
        assertThat(cartTool.calls).hasSize(1);
    }

    @Test
    void decliningDoesNotRunTheTool() {
        askToAdd();

        model.answers("As ye wish.");
        String answer = textOf(service.decide(decision(false, null)));

        assertThat(answer).isEqualTo("As ye wish.");
        assertThat(cartTool.calls).isEmpty();
    }

    @Test
    void aNewMessageDeclinesAnIgnoredPause() {
        askToAdd();

        model.answers("Back to business.");
        String answer = textOf(service.respond(new ChatRequest(ownerKey, conversationId, "Forget it")));

        assertThat(answer).isEqualTo("Back to business.");
        assertThat(cartTool.calls).isEmpty();
        List<Message> history = sessionService.getActiveMessages(conversationId.toString());
        assertThat(history).extracting(Message::getMessageType).contains(MessageType.TOOL);
    }

    private List<ChatEvent> askToAdd() {
        model.callsTool(CART_TOOL, "{\"productId\":1,\"quantity\":2}");
        return service.respond(new ChatRequest(ownerKey, conversationId, "Add it")).collectList().block();
    }

    private DecisionRequest decision(boolean approved, String reason) {
        return new DecisionRequest(ownerKey, conversationId, approved, reason);
    }

    private String textOf(Flux<ChatEvent> events) {
        return events.map(ChatEvent::text).collect(Collectors.joining()).block();
    }

    private static final class RecordingToolCallback implements ToolCallback {

        final List<String> calls = new ArrayList<>();
        final List<Object> ownerKeys = new ArrayList<>();

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name(CART_TOOL).description("Adds to the cart").inputSchema("{}").build();
        }

        @Override
        public String call(String toolInput) {
            throw new AssertionError("The tool must receive the tool context");
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            calls.add(toolInput);
            ownerKeys.add(toolContext.getContext().get(OwnerKeyContext.CONTEXT_KEY));
            return "{\"totalQuantity\":2}";
        }
    }

    private static final class ScriptedChatModel implements ChatModel {

        private final Deque<ChatResponse> responses = new ArrayDeque<>();

        void callsTool(String name, String arguments) {
            var toolCall = new AssistantMessage.ToolCall("call_1", "function", name, arguments);
            responses.add(new ChatResponse(List.of(new Generation(
                    AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build(),
                    ChatGenerationMetadata.builder().finishReason("TOOL_CALLS").build()))));
        }

        void answers(String text) {
            responses.add(new ChatResponse(List.of(new Generation(new AssistantMessage(text),
                    ChatGenerationMetadata.builder().finishReason("STOP").build()))));
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return responses.remove();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(responses.remove());
        }

        @Override
        public ChatOptions getOptions() {
            return OpenAiChatOptions.builder().build();
        }
    }
}
