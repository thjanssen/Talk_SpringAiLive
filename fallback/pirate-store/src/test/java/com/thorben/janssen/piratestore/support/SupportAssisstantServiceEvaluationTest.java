package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductRepository;
import com.thorben.janssen.piratestore.product.ProductService;
import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.thorben.janssen.piratestore.support.cartaction.CartAssistantActionService;
import com.thorben.janssen.piratestore.support.cartaction.PendingCartActionStore;
import com.thorben.janssen.piratestore.support.tools.CartAssistantTools;
import com.thorben.janssen.piratestore.support.tools.ProductTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live LLM-as-judge tests for {@link SupportAssisstantService#respond}. Like
 * the rest of this application's context (see {@code IngestionPipeline},
 * which embeds the RAG docs on startup), these tests make real calls to the
 * configured OpenAI model -- both for the assistant's answer and for the
 * evaluator judging it -- so a valid {@code spring.ai.openai.api-key} must be
 * available to the test JVM.
 *
 * <p>{@link SupportAssisstantService} is not autowired directly: it
 * constructor-injects the {@code vaadin-session}-scoped {@link ShoppingSession}
 * (and, transitively via {@link CartAssistantTools}, {@link PendingCartActionStore}),
 * and no Vaadin session is active in a plain {@code @SpringBootTest} -- calling
 * a method on either scoped proxy outside of one would fail. Each test builds
 * its own instance from plain, unscoped objects instead, the same workaround
 * {@code CartAssistantActionIntegrationTest} uses for {@code PendingCartActionStore}.
 */
@SpringBootTest
@Transactional
class SupportAssisstantServiceEvaluationTest {

    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemPrompt;
    @Autowired
    private ChatClient.Builder chatClientBuilder;
    @Autowired
    private ChatClient.Builder evaluatorChatClientBuilder;
    @Autowired
    private ProductTools productTools;
    @Autowired
    private ChatMemory chatMemory;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private CartService cartService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void answerAboutEyePatchesIsRelevantToTheQuestion() {
        SupportConversationContext context = new SupportConversationContext(UUID.randomUUID(), UUID.randomUUID());
        String question = "What clothing do you have for someone who just lost an eye in a swordfight?";

        SupportResponse response = newSupportAssisstantService().respond(context, question);

        RelevancyEvaluator relevancyEvaluator = RelevancyEvaluator.builder()
                .chatClientBuilder(evaluatorChatClientBuilder)
                .build();
        EvaluationResponse evaluation = relevancyEvaluator.evaluate(new EvaluationRequest(question, response.message()));

        assertThat(evaluation.isPass())
                .as("Relevancy check failed for question '%s', answer: %s", question, response.message())
                .isTrue();
    }

    @Test
    void answerAboutTheEyePatchPriceIsFactuallySupportedByTheCatalog() {
        Product eyePatch = productRepository.findBySku("EYE-PATCH-01").orElseThrow();
        SupportConversationContext context = new SupportConversationContext(UUID.randomUUID(), UUID.randomUUID());
        String question = "How much does the " + eyePatch.getName() + " cost?";

        SupportResponse response = newSupportAssisstantService().respond(context, question);

        // Covers both facts the assistant tends to mention in its answer (see FactCheckingEvaluator's
        // Javadoc: it checks the *entire* response against the document, so any claim -- e.g. stock --
        // left out here would make an otherwise correct answer fail as "unsupported").
        Document catalogFact = new Document(eyePatch.getName() + " costs " + eyePatch.getPrice()
                + " gold coins. " + eyePatch.getStockQuantity() + " units are currently in stock.");
        FactCheckingEvaluator factCheckingEvaluator = FactCheckingEvaluator.builder(evaluatorChatClientBuilder).build();
        EvaluationResponse evaluation = factCheckingEvaluator.evaluate(
                new EvaluationRequest(question, List.of(catalogFact), response.message()));

        assertThat(evaluation.isPass())
                .as("Fact-check failed for question '%s', answer: %s", question, response.message())
                .isTrue();
    }

    /**
     * Builds a fresh {@code SupportAssisstantService} from plain, unscoped
     * collaborators -- see the class Javadoc for why the real, scoped beans
     * cannot be used here.
     */
    private SupportAssisstantService newSupportAssisstantService() {
        ShoppingSession shoppingSession = new ShoppingSession();
        CartAssistantActionService cartAssistantActionService =
                new CartAssistantActionService(cartService, productService, new PendingCartActionStore());
        CartAssistantTools cartAssistantTools = new CartAssistantTools(cartAssistantActionService, shoppingSession);

        return new SupportAssisstantService(systemPrompt, chatClientBuilder, productTools, cartAssistantTools,
                chatMemory, vectorStore, cartAssistantActionService, shoppingSession);
    }
}
