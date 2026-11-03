package com.thorben.janssen.piratestore.support;

import com.thorben.janssen.piratestore.cart.CartItemNotFoundException;
import com.thorben.janssen.piratestore.cart.CartService;
import com.thorben.janssen.piratestore.cart.InsufficientStockException;
import com.thorben.janssen.piratestore.cart.InvalidQuantityException;
import com.thorben.janssen.piratestore.product.Product;
import com.thorben.janssen.piratestore.product.ProductRepository;
import com.thorben.janssen.piratestore.support.tools.CartAssistantTools;
import com.thorben.janssen.piratestore.support.tools.CartChangePreview;
import com.thorben.janssen.piratestore.support.tools.CartChangePreviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CartChangePreviewServiceTest {

    @Autowired
    private CartChangePreviewService service;
    @Autowired
    private CartService cartService;
    @Autowired
    private ProductRepository productRepository;

    private final UUID ownerKey = UUID.randomUUID();
    private Product eyePatch;

    @BeforeEach
    void setUp() {
        eyePatch = productRepository.findBySku("EYE-PATCH-01").orElseThrow();
    }

    @Test
    void previewOfAnAdditionDescribesItWithoutChangingTheCart() {
        CartChangePreview preview = service.preview(ownerKey, CartAssistantTools.ADD_TO_CART,
                "{\"productId\":" + eyePatch.getId() + ",\"quantity\":2}");

        assertThat(preview.description()).isEqualTo("Add 2x \"" + eyePatch.getName() + "\"");
        assertThat(preview.priceImpact()).isEqualByComparingTo(eyePatch.getPrice().multiply(BigDecimal.TWO));
        assertThat(cartService.getCartSummary(ownerKey).totalQuantity()).isZero();
    }

    @Test
    void previewOfAQuantityChangeShowsTheDifference() {
        cartService.addProduct(ownerKey, eyePatch.getId(), 1);

        CartChangePreview preview = service.preview(ownerKey, CartAssistantTools.CHANGE_QUANTITY,
                "{\"productId\":" + eyePatch.getId() + ",\"newQuantity\":3}");

        assertThat(preview.description()).contains("from 1 to 3");
        assertThat(preview.priceImpact()).isEqualByComparingTo(eyePatch.getPrice().multiply(BigDecimal.TWO));
    }

    @Test
    void previewOfARemovalShowsTheNegativePrice() {
        cartService.addProduct(ownerKey, eyePatch.getId(), 2);

        CartChangePreview preview = service.preview(ownerKey, CartAssistantTools.REMOVE_FROM_CART,
                "{\"productId\":" + eyePatch.getId() + "}");

        assertThat(preview.priceImpact()).isEqualByComparingTo(eyePatch.getPrice().multiply(BigDecimal.TWO).negate());
    }

    @Test
    void aQuantityAboveTheStockIsRejected() {
        assertThatThrownBy(() -> service.preview(ownerKey, CartAssistantTools.ADD_TO_CART,
                "{\"productId\":" + eyePatch.getId() + ",\"quantity\":" + (eyePatch.getStockQuantity() + 1) + "}"))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void aQuantityBelowOneIsRejected() {
        assertThatThrownBy(() -> service.preview(ownerKey, CartAssistantTools.ADD_TO_CART,
                "{\"productId\":" + eyePatch.getId() + ",\"quantity\":0}"))
                .isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void removingAProductThatIsNotInTheCartIsRejected() {
        assertThatThrownBy(() -> service.preview(ownerKey, CartAssistantTools.REMOVE_FROM_CART,
                "{\"productId\":" + eyePatch.getId() + "}"))
                .isInstanceOf(CartItemNotFoundException.class);
    }
}
