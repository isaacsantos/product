package com.example.products.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank
    private String name;

    private String description;

    @DecimalMin("0.01")
    private BigDecimal price;

    @Builder.Default
    private boolean active = true;

    private ConditionType conditionType;

    @Min(1)
    @Max(10)
    private Integer conditionRating;
}
