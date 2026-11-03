package com.thorben.janssen.piratestore.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** JPA mapping tests for {@link Product}, run against an embedded H2 database. */
@DataJpaTest
class ProductRepositoryIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void savedProductCanBeReloadedBySku() {
        productRepository.save(new Product("TEST-SKU-01", "Test Cutlass", "A blade for testing",
                ProductCategory.WEAPONS, new BigDecimal("10.00"), 5, "⚔️"));

        var reloaded = productRepository.findBySku("TEST-SKU-01");

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getName()).isEqualTo("Test Cutlass");
        assertThat(reloaded.get().getPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void inactiveProductsAreExcludedFromActiveQueries() {
        Product active = new Product("TEST-ACTIVE-01", "Active Product", "desc", ProductCategory.PROVISIONS,
                new BigDecimal("5.00"), 1, "🛢️");
        Product inactive = new Product("TEST-INACTIVE-01", "Inactive Product", "desc", ProductCategory.PROVISIONS,
                new BigDecimal("5.00"), 1, "🛢️");
        inactive.setActive(false);
        productRepository.save(active);
        productRepository.save(inactive);

        var activeProducts = productRepository.findByActiveTrueOrderByNameAsc();

        assertThat(activeProducts).extracting(Product::getSku).contains("TEST-ACTIVE-01");
        assertThat(activeProducts).extracting(Product::getSku).doesNotContain("TEST-INACTIVE-01");
    }

    @Test
    void searchMatchesNameDescriptionAndCategory() {
        productRepository.save(new Product("TEST-SPYGLASS-01", "Brass Spyglass", "See far",
                ProductCategory.NAVIGATION, new BigDecimal("74.90"), 6, "🔭"));

        assertThat(productRepository.search("spyglass")).isNotEmpty();
        assertThat(productRepository.search("navigation")).isNotEmpty();
        assertThat(productRepository.search("no-such-term-xyz")).isEmpty();
    }
}
