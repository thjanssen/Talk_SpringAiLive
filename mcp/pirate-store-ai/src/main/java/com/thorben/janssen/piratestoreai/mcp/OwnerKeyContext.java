package com.thorben.janssen.piratestoreai.mcp;

/**
 * Key under which {@code SupportChatService} places the current chat
 * request's ownerKey in the {@code ChatClient}'s {@code ToolContext} (via
 * {@code ChatClientRequestSpec.toolContext(...)}). Spring AI's MCP client
 * copies every {@code ToolContext} entry into the {@code _meta} of each
 * outgoing MCP tool call (see
 * {@code org.springframework.ai.mcp.ToolContextToMcpMetaConverter}), which is
 * how pirate-store's {@code CartAssistantTools} resolve which shopping
 * cart a call belongs to -- ownerKey is never a declared
 * tool parameter, so the model never supplies or even sees it (see
 * pirate-store's {@code support.tools.McpOwnerKeyContext}).
 */
public final class OwnerKeyContext {

    public static final String CONTEXT_KEY = "ownerKey";

    private OwnerKeyContext() {
    }
}
