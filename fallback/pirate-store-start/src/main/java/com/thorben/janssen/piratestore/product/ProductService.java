package com.thorben.janssen.piratestore.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-oriented business service for the product catalog.
 *
 * <p>This service has no dependency on Vaadin or on any AI classes, so it can
 * be reused unchanged by a future search-products tool for the support
 * assistant.
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** Returns all active products, regardless of stock, ordered by name. */
    public List<Product> findAvailableProducts() {
        return productRepository.findByActiveTrueOrderByNameAsc();
    }

    /** Returns all active products of the given category, ordered by name. */
    public List<Product> findByCategory(ProductCategory category) {
        return productRepository.findByActiveTrueAndCategoryOrderByNameAsc(category);
    }

    /** Case-insensitive search across name, description and category of active products. */
    public List<Product> search(String searchTerm) {
        return productRepository.search(searchTerm);
    }

    /**
     * Returns the product with the given id.
     *
     * @throws ProductNotFoundException if no such product exists
     */
    public Product getRequiredProduct(long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
