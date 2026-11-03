package com.thorben.janssen.piratestore.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A placed order. Once created, an order and its {@link OrderItem} snapshots
 * are never modified by the regular UI or the support assistant (FR-15).
 */
@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Column(nullable = false)
    private UUID ownerKey;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false)
    private String postalCode;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    protected CustomerOrder() {
        // required by JPA
    }

    public CustomerOrder(String orderNumber, UUID ownerKey, CheckoutCommand command) {
        this.orderNumber = orderNumber;
        this.ownerKey = ownerKey;
        this.createdAt = Instant.now();
        this.status = OrderStatus.PLACED;
        this.recipientName = command.recipientName();
        this.email = command.email();
        this.street = command.street();
        this.postalCode = command.postalCode();
        this.city = command.city();
        this.country = command.country();
        this.total = BigDecimal.ZERO.setScale(2);
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
        this.total = this.total.add(item.getLineTotal());
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public UUID getOwnerKey() {
        return ownerKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getEmail() {
        return email;
    }

    public String getStreet() {
        return street;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
