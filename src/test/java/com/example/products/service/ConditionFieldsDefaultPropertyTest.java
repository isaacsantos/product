package com.example.products.service;

import com.example.products.model.AdminProductResponse;
import com.example.products.model.ConditionType;
import com.example.products.model.Product;
import com.example.products.model.ProductRequest;
import com.example.products.repository.ProductRepository;
import com.example.products.repository.TagRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-based test for condition fields default application.
 *
 * Property 1: Condition fields default application
 * For any valid product creation request omitting both conditionType and conditionRating,
 * the resulting response SHALL have conditionType == USED and conditionRating == 10.
 *
 * **Validates: Requirements 1.3, 1.4, 2.2, 2.3, 7.5, 7.6**
 */
class ConditionFieldsDefaultPropertyTest {

    private final ProductRepository repository = Mockito.mock(ProductRepository.class);
    private final TagRepository tagRepository = Mockito.mock(TagRepository.class);
    private final ProductServiceImpl service = new ProductServiceImpl(repository, tagRepository);

    // -------------------------------------------------------------------------
    // Property 1: Condition fields default application
    // Validates: Requirements 1.3, 1.4, 2.2, 2.3, 7.5, 7.6
    // -------------------------------------------------------------------------

    @Property
    void conditionFieldsDefaultToUsedAndTenWhenOmitted(
            @ForAll("validProductNames") String name,
            @ForAll("validPrices") BigDecimal price
    ) {
        // Arrange: mock repository to return the saved product with an ID set
        when(repository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        // Build a request with null conditionType and null conditionRating
        ProductRequest request = ProductRequest.builder()
                .name(name)
                .description(null)
                .price(price)
                .conditionType(null)
                .conditionRating(null)
                .build();

        // Act
        AdminProductResponse response = service.createAdmin(request);

        // Assert: defaults are applied
        assertThat(response.getConditionType()).isEqualTo(ConditionType.USED);
        assertThat(response.getConditionRating()).isEqualTo(10);
    }

    @Provide
    Arbitrary<String> validProductNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(100);
    }

    @Provide
    Arbitrary<BigDecimal> validPrices() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("99999.99"))
                .ofScale(2);
    }
}
