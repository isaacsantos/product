package com.example.products.model;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClassifiedProduct {
    private String name;
    private String description;
    private List<Integer> imageIndices;
    private List<Long> tagIds;
    private BigDecimal price;
}
