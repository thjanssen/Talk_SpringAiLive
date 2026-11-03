package com.thorben.janssen.piratestore.cart;

import com.thorben.janssen.piratestore.product.Product;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The shopping cart belonging to exactly one Vaadin session, identified by
 * {@link #ownerKey}. Every session has at most one {@link CartStatus#ACTIVE}
 * cart at a time; carts of different sessions are never linked.
 */
@Entity
@Table(name = "carts", indexes = @Index(name = "idx_cart_owner_key", columnList = "ownerKey"))
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID ownerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartStatus status;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<CartItem> items = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Cart() {
        // required by JPA
    }

    public Cart(UUID ownerKey) {
        this.ownerKey = ownerKey;
        this.status = CartStatus.ACTIVE;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public UUID getOwnerKey() {
        return ownerKey;
    }

    public CartStatus getStatus() {
        return status;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Optional<CartItem> findItem(long productId) {
        return items.stream()
                .filter(item -> item.getProduct().getId() == productId)
                .findFirst();
    }

    public void addItem(CartItem item) {
        item.setCart(this);
        items.add(item);
        touch();
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        touch();
    }

    public void markOrdered() {
        this.status = CartStatus.ORDERED;
        touch();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void addProduct(Product product, int quantity) {
        Optional<CartItem> existing = findItem(product.getId());
        if (existing.isPresent()) {
            existing.get().increaseQuantity(quantity);
        } else {
            addItem(new CartItem(product, quantity));
        }
        touch();
    }
}
