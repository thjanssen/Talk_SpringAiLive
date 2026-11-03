package com.thorben.janssen.piratestore.ui;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

import java.math.BigDecimal;

/**
 * A coin-pile icon plus an amount, used everywhere a price is displayed so
 * the icon stays consistent instead of being duplicated as string
 * concatenation in every view. Purely presentational -- callers still get
 * the amount from the regular business services (e.g. {@code CartSummary},
 * {@code Product}), never compute it here.
 *
 * <p>Two layouts are supported: the default "icon, amount, gold coins" used
 * where the price needs to be unambiguous on its own (cart, checkout, order
 * confirmation), and a compact "amount, icon" layout (no unit text) for
 * places like the product overview where the icon alone already reads as
 * currency.
 */
public class PriceLabel extends Span {

    public PriceLabel(BigDecimal amount) {
        this(amount, true);
    }

    public PriceLabel(BigDecimal amount, boolean showUnitText) {
        addClassName("price-label");
        Icon icon = new Icon(VaadinIcon.COIN_PILES);
        icon.addClassName("price-icon");
        if (showUnitText) {
            add(icon, new Span(amount + " gold coins"));
        } else {
            add(new Span(amount.toString()), icon);
        }
    }
}
