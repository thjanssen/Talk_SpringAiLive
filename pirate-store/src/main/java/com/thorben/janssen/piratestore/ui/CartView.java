package com.thorben.janssen.piratestore.ui;

import com.thorben.janssen.piratestore.cart.CartChangedEvent;
import com.thorben.janssen.piratestore.cart.CartItemView;
import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.CartSummary;
import com.thorben.janssen.piratestore.cart.CartUpdateBroadcaster;
import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.function.Consumer;

/**
 * Cart page. Reads and writes exclusively through {@link CartService}; all
 * totals shown come from {@link CartSummary}, never computed here.
 */
@Route(value = "cart", layout = MainLayout.class)
@PageTitle("Cart | The Pirate's Chest")
public class CartView extends Div {

    private final CartService cartService;
    private final ShoppingSession shoppingSession;
    private final CartUpdateBroadcaster broadcaster;
    private final Consumer<CartChangedEvent> cartListener = event -> refresh(event.summary());

    private final Div content = new Div();

    public CartView(CartService cartService, ShoppingSession shoppingSession, CartUpdateBroadcaster broadcaster) {
        this.cartService = cartService;
        this.shoppingSession = shoppingSession;
        this.broadcaster = broadcaster;

        addClassName("cart-view");
        Span heading = new Span("Your Cart");
        heading.addClassName("page-heading");
        add(heading, content);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        broadcaster.register(shoppingSession.getOwnerKey(), cartListener);
        refresh(cartService.getCartSummary(shoppingSession.getOwnerKey()));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        broadcaster.remove(shoppingSession.getOwnerKey(), cartListener);
    }

    private void refresh(CartSummary summary) {
        UI ui = getUI().orElse(null);
        Runnable render = () -> renderContent(summary);
        if (ui != null) {
            ui.access(render::run);
        } else {
            render.run();
        }
    }

    private void renderContent(CartSummary summary) {
        content.removeAll();
        if (summary.isEmpty()) {
            content.add(buildEmptyState());
            return;
        }

        Grid<CartItemView> grid = new Grid<>();
        grid.setItems(summary.items());
        grid.setAllRowsVisible(true);
        grid.addColumn(CartItemView::productName).setHeader("Product").setAutoWidth(true);
        grid.addComponentColumn(item -> new PriceLabel(item.unitPrice())).setHeader("Unit price").setAutoWidth(true);
        grid.addComponentColumn(this::buildQuantityField).setHeader("Quantity").setAutoWidth(true);
        grid.addComponentColumn(item -> new PriceLabel(item.lineTotal())).setHeader("Subtotal").setAutoWidth(true);
        grid.addComponentColumn(this::buildRemoveButton).setHeader("");

        Span totals = new Span(new Text(summary.totalQuantity() + " item(s) -- Total: "), new PriceLabel(summary.totalPrice()));
        totals.addClassName("cart-totals");

        Button checkoutButton = new Button("Proceed to checkout");
        checkoutButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        checkoutButton.addClickListener(event -> checkoutButton.getUI()
                .ifPresent(currentUi -> currentUi.navigate(CheckoutView.class)));

        content.add(grid, totals, checkoutButton);
    }

    private IntegerField buildQuantityField(CartItemView item) {
        IntegerField field = new IntegerField();
        field.setValue(item.quantity());
        field.setMin(1);
        field.setMax(item.availableStock());
        field.setStepButtonsVisible(true);
        field.addClassName("cart-quantity-field");
        field.addValueChangeListener(event -> {
            if (!event.isFromClient() || event.getValue() == null) {
                return;
            }
            try {
                cartService.updateQuantity(shoppingSession.getOwnerKey(), item.productId(), event.getValue());
            } catch (RuntimeException e) {
                showError(e.getMessage());
                refresh(cartService.getCartSummary(shoppingSession.getOwnerKey()));
            }
        });
        return field;
    }

    private Button buildRemoveButton(CartItemView item) {
        Button remove = new Button("Remove");
        remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        remove.addClickListener(event -> cartService.removeProduct(shoppingSession.getOwnerKey(), item.productId()));
        return remove;
    }

    private Div buildEmptyState() {
        Div emptyState = new Div();
        emptyState.addClassName("cart-empty-state");
        Paragraph text = new Paragraph("Your cart is empty. Time to fill it with plunder!");
        Anchor backLink = new Anchor("products", "Back to Products");
        emptyState.add(text, backLink);
        return emptyState;
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 3500, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
