package com.thorben.janssen.piratestore.support.cartaction;

import com.thorben.janssen.piratestore.support.SupportChatPanel;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Immutable, UI-facing view of a {@link PendingCartAction}, used by
 * {@link SupportChatPanel} to render the "Confirm" / "Cancel" controls
 * without depending on the internal action model.
 */
public record PendingCartActionView(
        UUID actionId,
        PendingActionType type,
        String productName,
        int previousQuantity,
        int newQuantity,
        BigDecimal priceImpact,
        String description
) {
}
