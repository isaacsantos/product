package com.example.products.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateUserRequest {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @Size(max = 256)
    private String displayName;

    private List<String> roles;
}
