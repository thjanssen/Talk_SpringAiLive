package com.thorben.janssen.piratestore.support.tools;

import java.util.Map;
import java.util.UUID;

/**
 * Key under which the current shopping session's ownerKey is expected to
 * travel through a Spring AI {@code ToolContext}, once the AI tool-calling
 * part of this application is built.
 *
 * <p>Intended usage, mirroring the separate MCP-based pirate-store demo
 * (see its {@code support.tools.McpOwnerKeyContext}):
 * <ul>
 *   <li>When building the {@code ChatClient} call, attach the ownerKey via
 *       {@code chatClient.prompt(...).toolContext(Map.of(OwnerKeyContext.CONTEXT_KEY,
 *       shoppingSession.getOwnerKey().toString()))} -- never as a declared
 *       tool parameter, so the model never sees or supplies it.</li>
 *   <li>In an {@code @Tool}-annotated method, declare a
 *       {@code org.springframework.ai.chat.model.ToolContext} parameter --
 *       Spring AI injects it automatically and excludes it from the tool's
 *       JSON schema -- and resolve the ownerKey with
 *       {@code OwnerKeyContext.require(toolContext.getContext())}.</li>
 * </ul>
 *
 * <p>Deliberately works against the plain {@code Map<String, Object>} behind
 * {@code ToolContext.getContext()} rather than the {@code ToolContext} type
 * itself, so this class needs no Spring AI dependency yet -- pirate-store-start
 * has none until the AI part of this demo is built (see {@code pom.xml}).
 */
public final class OwnerKeyContext {

    public static final String CONTEXT_KEY = "ownerKey";

    private OwnerKeyContext() {
    }

    public static UUID require(Map<String, Object> toolContext) {
        if (toolContext == null) {
            throw new IllegalStateException(
                    "No tool context available -- this tool must be called through a ChatClient call "
                            + "that attached '" + CONTEXT_KEY + "' via toolContext(...)");
        }
        Object ownerKey = toolContext.get(CONTEXT_KEY);
        if (!(ownerKey instanceof String raw) || raw.isBlank()) {
            throw new IllegalStateException("Missing '" + CONTEXT_KEY + "' entry in the tool context");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("'" + CONTEXT_KEY + "' tool context entry is not a valid UUID", e);
        }
    }
}
