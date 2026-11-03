package com.thorben.janssen.piratestore.ui;

import com.thorben.janssen.piratestore.cart.InsufficientStockException;
import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductCategory;
import com.thorben.janssen.piratestore.product.ProductService;
import com.thorben.janssen.piratestore.product.ProductUnavailableException;
import com.thorben.janssen.piratestore.session.ShoppingSession;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

import java.util.List;

/**
 * Product catalog. Contains no business logic: it only calls
 * {@link ProductService} and {@link CartService} and renders their results.
 */
@Route(value = "", layout = MainLayout.class)
@RouteAlias(value = "products", layout = MainLayout.class)
@PageTitle("Products | The Pirate's Chest")
public class ProductView extends Div {

    private final ProductService productService;
    private final CartService cartService;
    private final ShoppingSession shoppingSession;

    private final Div grid = new Div();

    public ProductView(ProductService productService, CartService cartService, ShoppingSession shoppingSession) {
        this.productService = productService;
        this.cartService = cartService;
        this.shoppingSession = shoppingSession;

        addClassName("product-view");

        Span heading = new Span("Gear up, matey!");
        heading.addClassName("page-heading");

        ComboBox<ProductCategory> categoryFilter = new ComboBox<>("Filter by category");
        categoryFilter.setItems(ProductCategory.values());
        categoryFilter.setClearButtonVisible(true);
        categoryFilter.setPlaceholder("All categories");
        categoryFilter.addValueChangeListener(event -> renderProducts(event.getValue()));

        grid.addClassName("product-grid");

        add(heading, categoryFilter, grid);
        renderProducts(null);
    }

    private void renderProducts(ProductCategory category) {
        grid.removeAll();
        List<Product> products = category == null
                ? productService.findAvailableProducts()
                : productService.findByCategory(category);
        products.forEach(product -> grid.add(buildCard(product)));
    }

    private Div buildCard(Product product) {
        Div card = new Div();
        card.addClassName("product-card");

        Span icon = new Span(product.getImageReference());
        icon.addClassName("product-icon");

        Span name = new Span(product.getName());
        name.addClassName("product-name");

        Paragraph description = new Paragraph(product.getDescription());
        description.addClassName("product-description");

        Span category = new Span(product.getCategory().toString());
        category.addClassName("product-category");

        PriceLabel price = new PriceLabel(product.getPrice(), false);
        price.addClassName("product-price");

        Span availability = new Span(product.isInStock() ? "In stock: " + product.getStockQuantity() : "Sold out");
        availability.addClassName(product.isInStock() ? "product-in-stock" : "product-out-of-stock");

        Button addButton = new Button("Add to cart");
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.setEnabled(product.isAvailable());
        addButton.addClickListener(event -> addToCart(product));

        card.add(icon, name, category, description, price, availability, addButton);
        return card;
    }

    private void addToCart(Product product) {
        try {
            cartService.addProduct(shoppingSession.getOwnerKey(), product.getId(), 1);
            Notification success = Notification.show(product.getName() + " added to cart", 2500,
                    Notification.Position.BOTTOM_END);
            success.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (ProductUnavailableException | InsufficientStockException e) {
            Notification error = Notification.show(e.getMessage(), 3500, Notification.Position.BOTTOM_END);
            error.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
