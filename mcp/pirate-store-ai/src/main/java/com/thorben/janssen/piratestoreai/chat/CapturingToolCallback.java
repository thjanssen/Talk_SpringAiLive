package com.thorben.janssen.piratestoreai.chat;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.function.Consumer;

/**
 * Wraps a cart-mutating MCP tool callback to capture its raw JSON result (a
 * pirate-store {@code PendingCartActionView}) so {@link SupportChatService}
 * can attach it to the {@link ChatResponse} once the {@code ChatClient} call
 * returns, instead of relying on the model to repeat or reformat it.
 */
class CapturingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final Consumer<String> onResult;

    CapturingToolCallback(ToolCallback delegate, Consumer<String> onResult) {
        this.delegate = delegate;
        this.onResult = onResult;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        String result = delegate.call(toolInput);
        onResult.accept(result);
        return result;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String result = delegate.call(toolInput, toolContext);
        onResult.accept(result);
        return result;
    }
}
