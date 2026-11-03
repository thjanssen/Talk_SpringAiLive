package com.thorben.janssen.piratestore.support.tools;

import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductCategory;
import com.thorben.janssen.piratestore.product.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductTools {

    private static final Logger logger = LoggerFactory.getLogger(ProductTools.class);

    private ProductService productService;

    public ProductTools(ProductService productService) {
        this.productService = productService;
    }

    @Tool(name = "findAvailableProducts", description = "Returns all active products, regardless of stock, ordered by name.")
    public List<Product> findAvailableProducts() {
        logger.info("Tool findAvailableProducts called without parameters");
        return productService.findAvailableProducts();
    }

    @Tool(name = "findByCategory", description = "Returns all active products of the given category, ordered by name.")
    public List<Product> findByCategory(ProductCategory category) {
        logger.info("Tool findByCategory called with category={}", category);
        return productService.findByCategory(category);
    }

    @Tool(name = "search", description = "Case-insensitive search across name, description and category of active products.")
    public List<Product> search(String searchTerm) {
        logger.info("Tool search called with searchTerm={}", searchTerm);
        return productService.search(searchTerm);
    }

    @Tool(name = "getRequiredProduct", description = "Returns the product with the given id.")
    public Product getRequiredProduct(long productId) {
        logger.info("Tool getRequiredProduct called with productId={}", productId);
        return productService.getRequiredProduct(productId);
    }
}
