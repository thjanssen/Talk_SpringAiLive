package com.thorben.janssen.piratestore.order;

import com.thorben.janssen.piratestore.cart.Cart;
import com.thorben.janssen.piratestore.cart.CartItem;
import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.EmptyCartException;
import com.thorben.janssen.piratestore.cart.InsufficientStockException;
import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductService;
import com.thorben.janssen.piratestore.product.ProductUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Business service that turns a cart into a placed order.
 *
 * <p>{@link #placeOrder} is the single transactional boundary for checkout:
 * it re-checks stock for every line against freshly loaded products, builds
 * the immutable {@link OrderItem} snapshots, reduces stock, closes the cart
 * and saves the order -- all or nothing (FR-06, AC-09, AC-10).
 */
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final OrderNumberGenerator orderNumberGenerator;

    public OrderService(OrderRepository orderRepository, CartService cartService, ProductService productService,
                         OrderNumberGenerator orderNumberGenerator) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.productService = productService;
        this.orderNumberGenerator = orderNumberGenerator;
    }

    public CustomerOrder placeOrder(UUID ownerKey, CheckoutCommand command) {
        Cart cart = cartService.getActiveCart(ownerKey);
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException();
        }

        CustomerOrder order = new CustomerOrder(orderNumberGenerator.nextOrderNumber(), ownerKey, command);

        // Re-check every line against a freshly loaded product before changing anything.
        for (CartItem item : cart.getItems()) {
            Product product = productService.getRequiredProduct(item.getProduct().getId());
            if (!product.isAvailable()) {
                throw new ProductUnavailableException(product.getName());
            }
            if (item.getQuantity() > product.getStockQuantity()) {
                throw new InsufficientStockException(product.getName(), product.getStockQuantity());
            }
        }

        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            order.addItem(new OrderItem(product.getSku(), product.getName(), product.getPrice(), item.getQuantity()));
            product.reduceStock(item.getQuantity());
        }

        CustomerOrder savedOrder = orderRepository.save(order);
        cartService.closeActiveCart(ownerKey);
        return savedOrder;
    }

    @Transactional(readOnly = true)
    public CustomerOrder getRequiredOrder(UUID ownerKey, String orderNumber) {
        return orderRepository.findByOwnerKeyAndOrderNumber(ownerKey, orderNumber)
                .orElseThrow(() -> new OrderNotFoundException(orderNumber));
    }
}
