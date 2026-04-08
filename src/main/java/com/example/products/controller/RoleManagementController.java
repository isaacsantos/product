package com.example.products.controller;

import com.example.products.model.RoleRequest;
import com.example.products.service.RoleManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/users")
public class RoleManagementController {

    private final RoleManagementService roleManagementService;

    public RoleManagementController(RoleManagementService roleManagementService) {
        this.roleManagementService = roleManagementService;
    }

    @PostMapping("/{uid}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignRoles(
            @PathVariable String uid,
            @RequestBody @Valid RoleRequest request) {
        roleManagementService.assignRoles(uid, request.getRoles());
        return ResponseEntity.ok().build();
    }
}
