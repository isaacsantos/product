package com.example.products.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResponse {
    private Long id;
    private Long productId;
    private String url;
    private Integer displayOrder;
}
