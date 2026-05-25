package com.example.products.service;

import com.example.products.model.AdminProductResponse;
import com.example.products.model.ConditionType;
import com.example.products.model.Product;
import com.example.products.model.ProductRequest;
import com.example.products.repository.ProductRepository;
import com.example.products.repository.TagRepository;
import net.jqwik.api.*;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-based test: Condition fields round-trip preservation (Property 2).
 *
 * For any valid ConditionType and any integer in [1, 10], creating a product
 * with those values and reading back the response SHALL return the same
 * conditionType and conditionRating values.
 *
 * Validates: Requirements 4.1–4.8, 5.1–5.4, 7.1–7.4
 */
class ConditionFieldsRoundTripPropertyTest {

    private final ProductRepository repository = Mockito.mock(ProductRepository.class);
    private final TagRepository tagRepository = Mockito.mock(TagRepository.class);
    private final ProductServiceImpl service = new ProductServiceImpl(repository, tagRepository);

    /**
     * Property 2: Condition fields round-trip preservation
     *
     * For any valid ConditionType and any conditionRating in [1, 10],
     * creating a product with those values returns a response with the same values.
     *
     * Validates: Requirements 4.1–4.8, 5.1–5.4, 7.1–7.4
     */
    @Property
    void conditionFieldsArePreservedThroughCreateAdmin(
            @ForAll("conditionTypes") ConditionType conditionType,
            @ForAll("validConditionRatings") Integer conditionRating
    ) {
        when(repository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        ProductRequest request = ProductRequest.builder()
                .name("Test Product")
                .description("A test product")
                .price(new BigDecimal("19.99"))
                .active(true)
                .conditionType(conditionType)
                .conditionRating(conditionRating)
                .build();

        AdminProductResponse response = service.createAdmin(request);

        assertThat(response.getConditionType()).isEqualTo(conditionType);
        assertThat(response.getConditionRating()).isEqualTo(conditionRating);
    }

    @Provide
    Arbitrary<ConditionType> conditionTypes() {
        return Arbitraries.of(ConditionType.values());
    }

    @Provide
    Arbitrary<Integer> validConditionRatings() {
        return Arbitraries.integers().between(1, 10);
    }
}
