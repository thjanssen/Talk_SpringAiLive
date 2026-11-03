package com.thorben.janssen.piratestore.ui;

import com.thorben.janssen.piratestore.cart.CartChangedEvent;
import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.CartSummary;
import com.thorben.janssen.piratestore.cart.CartUpdateBroadcaster;
import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.thorben.janssen.piratestore.support.SupportAssisstantService;
import com.thorben.janssen.piratestore.support.SupportChatPanel;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;

import java.util.function.Consumer;

/**
 * Shared page shell: header with navigation, cart badge and the global
 * "Ask the Quartermaster" button, plus the {@link SupportChatPanel} overlay.
 * Contains no business logic; it only reads {@link CartSummary} totals that
 * {@link CartService} already computed.
 */
public class MainLayout extends Div implements RouterLayout {

    private final ShoppingSession shoppingSession;
    private final CartService cartService;
    private final CartUpdateBroadcaster broadcaster;
    private final Consumer<CartChangedEvent> cartListener = this::onCartChanged;

    private final Span cartBadge = new Span();
    private final Div contentSlot = new Div();
    private final SupportChatPanel chatPanel;

    public MainLayout(ShoppingSession shoppingSession, CartService cartService, CartUpdateBroadcaster broadcaster,
                       SupportAssisstantService supportAssistantService) {
        this.shoppingSession = shoppingSession;
        this.cartService = cartService;
        this.broadcaster = broadcaster;
        this.chatPanel = new SupportChatPanel(shoppingSession, supportAssistantService, broadcaster);

        addClassName("app-shell");
        add(buildHeader());
        contentSlot.addClassName("app-content");
        add(contentSlot);
        add(chatPanel);

        refreshBadge(cartService.getCartSummary(shoppingSession.getOwnerKey()));
    }

    private Div buildHeader() {
        Div header = new Div();
        header.addClassName("app-header");

        Span logo = new Span("🏴‍☠️ The Pirate's Chest");
        logo.addClassName("app-logo");

        RouterLink productsLink = new RouterLink("Products", ProductView.class);
        productsLink.addClassName("nav-link");
        RouterLink cartLink = new RouterLink("Cart", CartView.class);
        cartLink.addClassName("nav-link");

        cartBadge.addClassName("cart-badge");
        HorizontalLayout cartLinkWithBadge = new HorizontalLayout(cartLink, cartBadge);
        cartLinkWithBadge.setSpacing(false);
        cartLinkWithBadge.addClassName("cart-nav-item");

        HorizontalLayout nav = new HorizontalLayout(productsLink, cartLinkWithBadge);
        nav.addClassName("app-nav");

        Button askButton = new Button("Ask the Quartermaster", new Icon(VaadinIcon.COMMENTS));
        askButton.addClassName("ask-button");
        askButton.addClickListener(event -> chatPanel.toggleOpen());

        FlexLayout headerRow = new FlexLayout(logo, nav, askButton);
        headerRow.addClassName("app-header-row");
        header.add(headerRow);
        return header;
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        contentSlot.removeAll();
        if (content != null) {
            contentSlot.getElement().appendChild(content.getElement());
        }
    }

    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent attachEvent) {
        broadcaster.register(shoppingSession.getOwnerKey(), cartListener);
    }

    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent detachEvent) {
        broadcaster.remove(shoppingSession.getOwnerKey(), cartListener);
    }

    private void onCartChanged(CartChangedEvent event) {
        UI ui = getUI().orElse(null);
        if (ui == null) {
            return;
        }
        ui.access(() -> refreshBadge(event.summary()));
    }

    private void refreshBadge(CartSummary summary) {
        cartBadge.setText(String.valueOf(summary.totalQuantity()));
    }
}
