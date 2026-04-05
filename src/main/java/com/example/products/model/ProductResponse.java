package com.example.products.model;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;

    @Builder.Default
    private List<ImageResponse> images = new ArrayList<>();

    @Builder.Default
    private List<TagResponse> tags = new ArrayList<>();
}
