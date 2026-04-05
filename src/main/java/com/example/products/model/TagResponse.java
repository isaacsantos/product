package com.example.products.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagResponse {

    private Long id;
    private String name;
}
