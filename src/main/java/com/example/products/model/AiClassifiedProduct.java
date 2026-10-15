package com.example.products.model;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * A single product classified by the AI vision service from one or more images
 * that all depict the same product.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClassifiedProduct {
    private String name;
    private String description;
    private List<Long> tagIds;
    private BigDecimal price;
}
