package com.thorben.janssen.piratestore.support.cartaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A proposed but not-yet-applied cart change created by a support assistant
 * tool. The action only takes effect once {@code CartAssistantActionService}
 * confirms it against the regular {@code CartService}; a system prompt or the
 * model itself is never sufficient authorization (FR-13).
 *
 * <p>Instances are held only in memory for the lifetime of the owning Vaadin
 * session (see {@link PendingCartActionStore}); they do not need to survive a
 * restart.
 */
public class PendingCartAction {

    private final UUID actionId = UUID.randomUUID();
    private final UUID ownerKey;
    private final PendingActionType type;
    private final long productId;
    private final String productName;
    private final int previousQuantity;
    private final int newQuantity;
    private final BigDecimal priceImpact;
    private final Instant createdAt = Instant.now();
    private PendingActionStatus status = PendingActionStatus.PENDING;

    public PendingCartAction(UUID ownerKey, PendingActionType type, long productId, String productName,
                              int previousQuantity, int newQuantity, BigDecimal priceImpact) {
        this.ownerKey = ownerKey;
        this.type = type;
        this.productId = productId;
        this.productName = productName;
        this.previousQuantity = previousQuantity;
        this.newQuantity = newQuantity;
        this.priceImpact = priceImpact;
    }

    public UUID getActionId() {
        return actionId;
    }

    public UUID getOwnerKey() {
        return ownerKey;
    }

    public PendingActionType getType() {
        return type;
    }

    public long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getPreviousQuantity() {
        return previousQuantity;
    }

    public int getNewQuantity() {
        return newQuantity;
    }

    public BigDecimal getPriceImpact() {
        return priceImpact;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PendingActionStatus getStatus() {
        return status;
    }

    public boolean isPending() {
        return status == PendingActionStatus.PENDING;
    }

    public void markConfirmed() {
        this.status = PendingActionStatus.CONFIRMED;
    }

    public void markCancelled() {
        this.status = PendingActionStatus.CANCELLED;
    }

    public PendingCartActionView toView() {
        return new PendingCartActionView(actionId, type, productName, previousQuantity, newQuantity,
                priceImpact, describe());
    }

    private String describe() {
        return switch (type) {
            case ADD_PRODUCT -> "Add " + (newQuantity - previousQuantity) + "x \"" + productName + "\"";
            case CHANGE_QUANTITY -> "Change quantity of \"" + productName + "\" from " + previousQuantity
                    + " to " + newQuantity;
            case REMOVE_PRODUCT -> "Remove \"" + productName + "\" from the cart";
        };
    }
}
