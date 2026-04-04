package com.example.products.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisplayOrderRequest {

    @NotNull
    @Min(0)
    private Integer displayOrder;
}
