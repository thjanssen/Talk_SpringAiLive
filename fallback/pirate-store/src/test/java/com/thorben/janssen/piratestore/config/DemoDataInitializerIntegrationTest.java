package com.thorben.janssen.piratestore.config;

import com.thorben.janssen.piratestore.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that the demo catalog is created idempotently by SKU (technical spec, section 7). */
@DataJpaTest
class DemoDataInitializerIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void runningTheInitializerTwiceDoesNotDuplicateProducts() throws Exception {
        DemoDataInitializer initializer = new DemoDataInitializer(productRepository);

        initializer.run();
        long countAfterFirstRun = productRepository.count();
        initializer.run();
        long countAfterSecondRun = productRepository.count();

        assertThat(countAfterFirstRun).isEqualTo(8);
        assertThat(countAfterSecondRun).isEqualTo(countAfterFirstRun);
    }
}
