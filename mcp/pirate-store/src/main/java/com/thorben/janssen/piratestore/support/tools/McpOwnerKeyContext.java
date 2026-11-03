package com.thorben.janssen.piratestore.support.tools;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.UUID;

/**
 * Reads the ownerKey that pirate-store-ai attaches to every outgoing MCP
 * tool call via {@code ChatClient.toolContext(...)} back out of the
 * {@link McpSchema.CallToolRequest} that {@code @McpTool} methods receive
 * directly as a parameter. Spring AI's MCP client copies the
 * {@code ToolContext} map into the request's {@code _meta} for every call
 * (see {@code org.springframework.ai.mcp.ToolContextToMcpMetaConverter}),
 * which is where {@value #CONTEXT_KEY} is read back out here.
 *
 * <p>This is the only place ownerKey ever enters an MCP tool call. It is
 * never a declared method parameter, so it never appears in the tool's JSON
 * schema and the model can neither see nor supply it (same guarantee the
 * in-process design had via {@code ShoppingSession}, now re-established
 * across the process boundary to the pirate-store-ai application).
 */
final class McpOwnerKeyContext {

    static final String CONTEXT_KEY = "ownerKey";

    private McpOwnerKeyContext() {
    }

    static UUID require(McpSchema.CallToolRequest request) {
        if (request == null || request.meta() == null) {
            throw new IllegalStateException(
                    "No MCP call-tool request available -- this tool must be called through the MCP server");
        }
        Object ownerKey = request.meta().get(CONTEXT_KEY);
        if (!(ownerKey instanceof String raw) || raw.isBlank()) {
            throw new IllegalStateException("Missing '" + CONTEXT_KEY + "' entry in the MCP call-tool request meta");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("'" + CONTEXT_KEY + "' meta entry is not a valid UUID", e);
        }
    }
}
