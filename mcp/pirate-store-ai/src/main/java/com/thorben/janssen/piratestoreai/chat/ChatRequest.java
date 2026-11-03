package com.thorben.janssen.piratestoreai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body sent by pirate-store's {@code SupportAssisstantService}.
 * {@code ownerKey} is generated server-side there (from {@code ShoppingSession})
 * and only ever travels over this trusted server-to-server call -- never
 * supplied by a browser or the model.
 */
public record ChatRequest(
        @NotNull UUID ownerKey,
        @NotNull UUID conversationId,
        @NotBlank String message
) {
}
