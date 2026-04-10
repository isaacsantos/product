package com.example.products.model;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProductCreationResponse {
    private int totalImages;
    private int productsCreated;
    private List<AdminProductResponse> products;
}
