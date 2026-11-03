package com.thorben.janssen.piratestore.ui;

import com.thorben.janssen.piratestore.order.CustomerOrder;
import com.thorben.janssen.piratestore.order.OrderItem;
import com.thorben.janssen.piratestore.order.OrderNotFoundException;
import com.thorben.janssen.piratestore.order.OrderService;
import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Order confirmation page, addressed as {@code /orders/<orderNumber>}. Shows
 * a friendly error page instead of a stack trace when the order number is
 * unknown to this session.
 */
@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Order Confirmation | The Pirate's Chest")
public class OrderConfirmationView extends Div implements HasUrlParameter<String> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final OrderService orderService;
    private final ShoppingSession shoppingSession;

    public OrderConfirmationView(OrderService orderService, ShoppingSession shoppingSession) {
        this.orderService = orderService;
        this.shoppingSession = shoppingSession;
        addClassName("order-confirmation-view");
    }

    @Override
    public void setParameter(BeforeEvent event, String orderNumber) {
        removeAll();
        try {
            CustomerOrder order = orderService.getRequiredOrder(shoppingSession.getOwnerKey(), orderNumber);
            renderOrder(order);
        } catch (OrderNotFoundException e) {
            renderNotFound(orderNumber);
        }
    }

    private void renderOrder(CustomerOrder order) {
        Span heading = new Span("Order confirmed!");
        heading.addClassName("page-heading");

        Div details = new Div();
        details.addClassName("order-details");
        details.add(
                new Paragraph("Order number: " + order.getOrderNumber()),
                new Paragraph("Placed on: " + DATE_TIME_FORMAT.format(order.getCreatedAt())),
                new Paragraph("Recipient: " + order.getRecipientName()),
                new Paragraph("Shipping to: " + order.getStreet() + ", " + order.getPostalCode() + " "
                        + order.getCity() + ", " + order.getCountry()),
                new Paragraph("Status: " + order.getStatus())
        );

        Div items = new Div();
        items.addClassName("order-items");
        for (OrderItem item : order.getItems()) {
            Span line = new Span(new Text(item.getQuantity() + "x " + item.getProductName() + " -- "), new PriceLabel(item.getLineTotal()));
            line.addClassName("order-item-line");
            items.add(line);
        }

        Span total = new Span(new Text("Total: "), new PriceLabel(order.getTotal()));
        total.addClassName("order-total");

        Anchor backLink = new Anchor("products", "Back to Products");

        add(heading, details, items, total, backLink);
    }

    private void renderNotFound(String orderNumber) {
        Span heading = new Span("Order not found");
        heading.addClassName("page-heading");
        Paragraph text = new Paragraph("We could not find an order with number \"" + orderNumber
                + "\" for your session.");
        Anchor backLink = new Anchor("products", "Back to Products");
        add(heading, text, backLink);
    }
}
