package com.thorben.janssen.piratestore.support.cartaction;

/** The kind of cart mutation a {@link PendingCartAction} proposes. */
public enum PendingActionType {
    ADD_PRODUCT,
    CHANGE_QUANTITY,
    REMOVE_PRODUCT
}
