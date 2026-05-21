package com.example.products.model;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Phase1Result {
    private String name;
    private List<Integer> imageIndices;
}
