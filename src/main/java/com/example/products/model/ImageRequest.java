package com.example.products.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageRequest {

    @NotEmpty
    private List<@NotBlank @Pattern(regexp = "https?://.*", message = "must be a valid HTTP or HTTPS URL") String> urls;
}
