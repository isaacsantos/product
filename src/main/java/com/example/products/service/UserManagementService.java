package com.example.products.service;

import com.example.products.model.CreateUserRequest;
import com.example.products.model.UserResponse;

public interface UserManagementService {
    UserResponse createUser(CreateUserRequest request);
}
