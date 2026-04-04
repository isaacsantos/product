package com.example.products.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRowResult {
    private int rowNumber;
    private RowStatus status;
    private Long productId;
    private String errorMessage;
}
