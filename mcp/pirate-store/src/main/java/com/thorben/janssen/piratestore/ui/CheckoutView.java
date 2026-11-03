package com.thorben.janssen.piratestore.ui;

import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.CartSummary;
import com.thorben.janssen.piratestore.order.CheckoutCommand;
import com.thorben.janssen.piratestore.order.CustomerOrder;
import com.thorben.janssen.piratestore.order.OrderService;
import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Checkout form. Deliberately not bound to a JPA entity: values are collected
 * into the {@link CheckoutCommand} DTO, validated, and handed to
 * {@link OrderService#placeOrder} which owns the actual business logic.
 */
@Route(value = "checkout", layout = MainLayout.class)
@PageTitle("Checkout | The Pirate's Chest")
public class CheckoutView extends Div implements BeforeEnterObserver {

    private final CartService cartService;
    private final OrderService orderService;
    private final ShoppingSession shoppingSession;
    private final Validator validator;

    private final TextField recipientName = new TextField("Recipient name");
    private final EmailField email = new EmailField("Email");
    private final TextField street = new TextField("Street and house number");
    private final TextField postalCode = new TextField("Postal code");
    private final TextField city = new TextField("City");
    private final TextField country = new TextField("Country");
    private final Button submitButton = new Button("Place order");
    private final Map<String, HasValidation> fieldsByProperty = new LinkedHashMap<>();

    public CheckoutView(CartService cartService, OrderService orderService, ShoppingSession shoppingSession,
                         Validator validator) {
        this.cartService = cartService;
        this.orderService = orderService;
        this.shoppingSession = shoppingSession;
        this.validator = validator;

        addClassName("checkout-view");
        Span heading = new Span("Checkout");
        heading.addClassName("page-heading");

        country.setValue("Germany");
        fieldsByProperty.put("recipientName", recipientName);
        fieldsByProperty.put("email", email);
        fieldsByProperty.put("street", street);
        fieldsByProperty.put("postalCode", postalCode);
        fieldsByProperty.put("city", city);
        fieldsByProperty.put("country", country);

        FormLayout form = new FormLayout(recipientName, email, street, postalCode, city, country);

        submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitButton.addClickListener(event -> submit());

        add(heading, buildSummary(), form, submitButton);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (cartService.getCartSummary(shoppingSession.getOwnerKey()).isEmpty()) {
            event.forwardTo(CartView.class);
        }
    }

    private Div buildSummary() {
        CartSummary summary = cartService.getCartSummary(shoppingSession.getOwnerKey());
        Div summaryBox = new Div();
        summaryBox.addClassName("checkout-summary");
        summary.items().forEach(item -> {
            Span line = new Span(new Text(item.quantity() + "x " + item.productName() + " -- "), new PriceLabel(item.lineTotal()));
            line.addClassName("checkout-summary-line");
            summaryBox.add(line);
        });
        Span total = new Span(new Text("Total: "), new PriceLabel(summary.totalPrice()));
        total.addClassName("checkout-summary-total");
        summaryBox.add(total);
        return summaryBox;
    }

    private void submit() {
        clearFieldErrors();
        CheckoutCommand command = new CheckoutCommand(
                recipientName.getValue().trim(),
                email.getValue().trim(),
                street.getValue().trim(),
                postalCode.getValue().trim(),
                city.getValue().trim(),
                country.getValue().trim());

        Set<ConstraintViolation<CheckoutCommand>> violations = validator.validate(command);
        if (!violations.isEmpty()) {
            applyFieldErrors(violations);
            return;
        }

        submitButton.setEnabled(false);
        try {
            CustomerOrder order = orderService.placeOrder(shoppingSession.getOwnerKey(), command);
            shoppingSession.addOrderNumber(order.getOrderNumber());
            getUI().ifPresent(ui -> ui.navigate(OrderConfirmationView.class, order.getOrderNumber()));
        } catch (RuntimeException e) {
            Notification notification = Notification.show(e.getMessage(), 4000, Notification.Position.BOTTOM_END);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        } finally {
            submitButton.setEnabled(true);
        }
    }

    private void clearFieldErrors() {
        fieldsByProperty.values().forEach(field -> field.setInvalid(false));
    }

    private void applyFieldErrors(Set<ConstraintViolation<CheckoutCommand>> violations) {
        for (ConstraintViolation<CheckoutCommand> violation : violations) {
            HasValidation field = fieldsByProperty.get(violation.getPropertyPath().toString());
            if (field != null) {
                field.setInvalid(true);
                field.setErrorMessage(violation.getMessage());
            }
        }
    }
}
