package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.thorben.janssen.piratestore.support.cartaction.CartAssistantActionService;
import com.thorben.janssen.piratestore.support.tools.CartAssistantTools;
import com.thorben.janssen.piratestore.support.tools.OwnerKeyContext;
import com.thorben.janssen.piratestore.support.tools.ProductTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * The support assistant used by {@link SupportChatPanel}.
 *
 * <p>Currently a stub. Once a {@code ChatClient} is wired in here, attach
 * {@code shoppingSession.getOwnerKey()} to every call via
 * {@code .toolContext(Map.of(com.thorben.janssen.piratestore.support.tools.OwnerKeyContext.CONTEXT_KEY,
 * shoppingSession.getOwnerKey().toString()))} -- see that class's Javadoc for
 * how an {@code @Tool} method then reads it back out, never as a declared
 * tool parameter.
 */
@Service
public class SupportAssisstantService {

    private final ShoppingSession shoppingSession;
    private final CartAssistantActionService cartAssistantActionService;
    private final ChatClient chatClient;

    public SupportAssisstantService(
            @Value("classpath:/prompts/system-prompt.st")
            Resource systemPrompt,
            ChatClient.Builder chatClientBuilder,
            ProductTools productTools,
            CartAssistantTools cartAssistantTools,
            ChatMemory chatMemory,
            VectorStore vectorStore,
            CartAssistantActionService cartAssistantActionService,
            ShoppingSession shoppingSession) {
        this.cartAssistantActionService = cartAssistantActionService;
        this.shoppingSession = shoppingSession;

        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        SimpleLoggerAdvisor.builder().order(100).build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore).build()
                )
                .defaultTools(productTools, cartAssistantTools)
                .build();
    }

    public SupportResponse respond(SupportConversationContext context, String userMessage) {
        var response = this.chatClient.prompt(userMessage)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, shoppingSession.getConversationId()))
                .toolContext(Map.of(OwnerKeyContext.CONTEXT_KEY, shoppingSession.getOwnerKey().toString()))
                .call()
                .content();

        // If a tool proposed a cart change during this call, attach its view so
        // the chat panel renders the Confirm/Cancel controls (FR-12/FR-13)
        // instead of silently leaving the proposal unconfirmed.
        return shoppingSession.consumeProposedCartAction()
                .flatMap(cartAssistantActionService::findView)
                .map(view -> new SupportResponse(response, view))
                .orElseGet(() -> SupportResponse.text(response));
    }
}
