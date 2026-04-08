package com.example.products.service;

import java.util.List;

public interface RoleManagementService {
    void assignRoles(String uid, List<String> roles);
}
