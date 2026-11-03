package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.InsufficientStockException;
import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductRepository;
import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.thorben.janssen.piratestore.support.tools.CartAssistantTools;
import com.thorben.janssen.piratestore.support.tools.CartChangePreview;
import com.thorben.janssen.piratestore.support.tools.CartChangePreviewService;
import com.thorben.janssen.piratestore.support.tools.ProductTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Runs the confirmation flow with a scripted model, so the rules that protect
 * the cart are checked without any call to a real LLM.
 */
@SpringBootTest
class CartConfirmationFlowTest {

    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPrompt;
    @Autowired
    private ProductTools productTools;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private CartService cartService;
    @Autowired
    private CartChangePreviewService cartChangePreviewService;
    @Autowired
    private ToolCallingManager toolCallingManager;
    @Autowired
    private ProductRepository productRepository;

    private final ScriptedChatModel model = new ScriptedChatModel();
    private final ShoppingSession session = new ShoppingSession();
    private SupportAssisstantService service;
    private Product eyePatch;
    private UUID ownerKey;

    @BeforeEach
    void setUp() {
        service = new SupportAssisstantService(systemPrompt, ChatClient.builder(model), productTools,
                new CartAssistantTools(cartService), sessionService, mock(VectorStore.class), cartChangePreviewService,
                toolCallingManager, session);
        eyePatch = productRepository.findBySku("EYE-PATCH-01").orElseThrow();
        ownerKey = session.getOwnerKey();
    }

    @Test
    void aCartToolCallPausesWithoutChangingTheCart() {
        ChatResponse reply = askToAdd(1);

        assertThat(service.isPaused(reply)).isTrue();
        assertThat(quantityInCart()).isZero();
    }

    @Test
    void approvingRunsTheToolAndStreamsTheAnswer() {
        ChatResponse reply = askToAdd(2);

        model.answers("Aye, added!");
        String answer = textOf(service.approve(reply));

        assertThat(answer).isEqualTo("Aye, added!");
        assertThat(quantityInCart()).isEqualTo(2);
    }

    @Test
    void rejectingLeavesTheCartUnchanged() {
        ChatResponse reply = askToAdd(1);

        model.answers("As ye wish.");
        String answer = textOf(service.reject(reply));

        assertThat(answer).isEqualTo("As ye wish.");
        assertThat(quantityInCart()).isZero();
    }

    @Test
    void abandoningResolvesThePauseWithoutChangingTheCart() {
        ChatResponse reply = askToAdd(1);

        service.abandon(reply);

        List<Message> history = sessionService.getActiveMessages(session.getConversationId().toString());
        assertThat(history.get(history.size() - 1).getMessageType()).isEqualTo(MessageType.TOOL);
        assertThat(quantityInCart()).isZero();
    }

    @Test
    void stockIsCheckedAgainWhenTheUserConfirms() {
        ChatResponse reply = askToAdd(1);
        cartService.addProduct(ownerKey, eyePatch.getId(), eyePatch.getStockQuantity());

        model.answers("Not enough left, matey.");
        textOf(service.approve(reply));

        assertThat(quantityInCart()).isEqualTo(eyePatch.getStockQuantity());
    }

    @Test
    void previewDescribesTheChange() {
        ChatResponse reply = askToAdd(2);

        List<CartChangePreview> previews = service.previewPausedCalls(reply);

        assertThat(previews).hasSize(1);
        assertThat(previews.get(0).description()).isEqualTo("Add 2x \"" + eyePatch.getName() + "\"");
        assertThat(previews.get(0).priceImpact()).isEqualByComparingTo(eyePatch.getPrice().multiply(BigDecimal.TWO));
    }

    @Test
    void previewRejectsAQuantityAboveTheStock() {
        ChatResponse reply = askToAdd(eyePatch.getStockQuantity() + 1);

        assertThatThrownBy(() -> service.previewPausedCalls(reply)).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void thePausedReplyCanOnlyBeTakenOnce() {
        session.setPausedReply(askToAdd(1));

        assertThat(session.takePausedReply()).isPresent();
        assertThat(session.takePausedReply()).isEmpty();
    }

    private ChatResponse askToAdd(int quantity) {
        model.callsTool(CartAssistantTools.ADD_TO_CART,
                "{\"productId\":" + eyePatch.getId() + ",\"quantity\":" + quantity + "}");
        return service.streamResponse("Add it").blockLast();
    }

    private String textOf(Flux<ChatResponse> stream) {
        return stream.map(SupportAssisstantService::textOf).collect(Collectors.joining()).block();
    }

    private int quantityInCart() {
        return cartService.getCartSummary(ownerKey).totalQuantity();
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
