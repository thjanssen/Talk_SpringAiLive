package com.thorben.janssen.piratestore.support.tools;

import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductCategory;
import com.thorben.janssen.piratestore.product.ProductService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductTools {

    private ProductService productService;

    public ProductTools(ProductService productService) {
        this.productService = productService;
    }

    @Tool(name = "findAvailableProducts", description = "Returns all active products, regardless of stock, ordered by name.")
    public List<Product> findAvailableProducts() {
        return productService.findAvailableProducts();
    }

    @Tool(name = "findByCategory", description = "Returns all active products of the given category, ordered by name.")
    public List<Product> findByCategory(ProductCategory category) {
        return productService.findByCategory(category);
    }

    @Tool(name = "search", description = "Case-insensitive search across name, description and category of active products.")
    public List<Product> search(String searchTerm) {
        return productService.search(searchTerm);
    }

    @Tool(name = "getRequiredProduct", description = "Returns the product with the given id.")
    public Product getRequiredProduct(long productId) {
        return productService.getRequiredProduct(productId);
    }
}
