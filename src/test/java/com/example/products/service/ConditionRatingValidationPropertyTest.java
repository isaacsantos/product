package com.example.products.service;

import com.example.products.model.ProductRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for condition rating out-of-range rejection.
 *
 * **Validates: Requirements 3.3, 3.4**
 */
class ConditionRatingValidationPropertyTest {

    private final Validator validator;

    ConditionRatingValidationPropertyTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    /**
     * Property 3: Condition rating out-of-range rejection
     *
     * For any integer outside [1, 10], a ProductRequest with that conditionRating
     * must produce at least one constraint violation on the conditionRating field.
     *
     * **Validates: Requirements 3.3, 3.4**
     */
    @Property
    void conditionRatingOutsideValidRangeIsRejected(@ForAll("outOfRangeRatings") int invalidRating) {
        ProductRequest request = ProductRequest.builder()
                .name("Test Product")
                .price(new BigDecimal("9.99"))
                .conditionRating(invalidRating)
                .build();

        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);

        assertThat(violations)
                .isNotEmpty()
                .anyMatch(v -> v.getPropertyPath().toString().equals("conditionRating"));
    }

    @Provide
    Arbitrary<Integer> outOfRangeRatings() {
        return Arbitraries.oneOf(
                Arbitraries.integers().lessOrEqual(0),   // values < 1
                Arbitraries.integers().greaterOrEqual(11) // values > 10
        );
    }
}
