package com.example.products.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class UserResponse {

    private String uid;
    private String email;
    private String displayName;

    @Builder.Default
    private List<String> roles = new ArrayList<>();
}
