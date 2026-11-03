package com.thorben.janssen.piratestore.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * A product offered in the pirate store.
 *
 * <p>Instances are managed exclusively through {@link ProductService}; the UI
 * never binds directly to this entity (see {@code CLAUDE.md} guardrails).
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    @NotBlank
    private String sku;

    @Column(nullable = false)
    @NotBlank
    private String name;

    @Column(nullable = false, length = 500)
    @NotBlank
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private ProductCategory category;

    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal price;

    @Column(nullable = false)
    @Min(0)
    private int stockQuantity;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    @NotBlank
    private String imageReference;

    @Version
    private long version;

    protected Product() {
        // required by JPA
    }

    public Product(String sku, String name, String description, ProductCategory category,
                   BigDecimal price, int stockQuantity, String imageReference) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.imageReference = imageReference;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getImageReference() {
        return imageReference;
    }

    public long getVersion() {
        return version;
    }

    public boolean isInStock() {
        return stockQuantity > 0;
    }

    public boolean isAvailable() {
        return active && isInStock();
    }

    /** Reduces the stock by the given amount. Callers must ensure sufficient stock beforehand. */
    public void reduceStock(int amount) {
        if (amount < 0 || amount > stockQuantity) {
            throw new IllegalArgumentException("Cannot reduce stock of " + sku + " by " + amount);
        }
        this.stockQuantity -= amount;
    }
}
