package com.thorben.janssen.piratestore.config;

import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductCategory;
import com.thorben.janssen.piratestore.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Creates the demo catalog on first start. Idempotent by {@code sku}, so it
 * can safely run again against a database that already has data.
 */
@Component
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final ProductRepository productRepository;

    public DemoDataInitializer(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        List<Product> demoProducts = List.of(
                new Product("EYE-PATCH-01", "Classic Black Eye Patch", "One size fits all -- unless you already lost the other eye too.", ProductCategory.CLOTHING, new BigDecimal("9.99"), 25, "🏴‍☠️"),
                new Product("HAT-TRICORN-01", "Captain's Tricorn Hat", "Commands respect. Does not command the wind.", ProductCategory.CLOTHING, new BigDecimal("39.90"), 12, "🎩"),
                new Product("LEG-PEG-OAK-01", "Oakwood Peg Leg", "Handcrafted, splinter-resistant, dance-floor approved.", ProductCategory.EQUIPMENT, new BigDecimal("129.00"), 4, "🦿"),
                new Product("PARROT-POLLY-01", "Polly the Parrot", "Comes pre-trained to say \"Pieces of eight!\" Opinions are her own.", ProductCategory.COMPANIONS, new BigDecimal("499.00"), 2, "🦜"),
                new Product("MAP-ISLA-FORTUNA-01", "Treasure Map of Isla Fortuna", "X marks the spot. Directions beyond that are your problem.", ProductCategory.NAVIGATION, new BigDecimal("24.50"), 8, "🗺️"),
                new Product("SPYGLASS-BRASS-01", "Brass Spyglass", "See trouble coming from three leagues away.", ProductCategory.NAVIGATION, new BigDecimal("74.90"), 6, "🔭"),
                new Product("CUTLASS-BOARDING-01", "Rustproof Boarding Cutlass", "Sharp enough for boarding, dull enough for polite company.", ProductCategory.WEAPONS, new BigDecimal("89.00"), 10, "⚔️"),
                new Product("BISCUITS-BARREL-01", "Barrel of Ship's Biscuits", "Survives storms, scurvy and several centuries.", ProductCategory.PROVISIONS, new BigDecimal("18.00"), 20, "🛢️")
        );

        int created = 0;
        for (Product product : demoProducts) {
            if (productRepository.findBySku(product.getSku()).isEmpty()) {
                productRepository.save(product);
                created++;
            }
        }
        log.info("Demo data check complete: {} of {} demo products were newly created", created, demoProducts.size());
    }
}
