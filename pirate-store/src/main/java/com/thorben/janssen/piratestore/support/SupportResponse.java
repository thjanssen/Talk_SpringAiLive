package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.support.cartaction.PendingCartActionView;

/**
 * Response returned by {@link SupportAssisstantService}. {@code pendingAction}
 * is {@code null} unless the assistant is proposing a cart change that still
 * requires the user to confirm it in the chat window.
 */
public record SupportResponse(String message, PendingCartActionView pendingAction) {

    public static SupportResponse text(String message) {
        return new SupportResponse(message, null);
    }

    public boolean hasPendingAction() {
        return pendingAction != null;
    }
}
