package com.example.products.model;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Phase2Result {
    private String name;
    private String description;
    private List<Long> tagIds;
}
