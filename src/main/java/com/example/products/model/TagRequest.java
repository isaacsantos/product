package com.example.products.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagRequest {

    @NotBlank
    @Size(max = 100)
    private String name;
}
