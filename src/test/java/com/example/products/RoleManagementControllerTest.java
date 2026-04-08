package com.example.products;

import com.example.products.controller.RoleManagementController;
import com.example.products.exception.GlobalExceptionHandler;
import com.example.products.model.RoleRequest;
import com.example.products.service.RoleManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoleManagementController.class)
@Import(GlobalExceptionHandler.class)
@EnableMethodSecurity
class RoleManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private RoleManagementService roleManagementService;

    @Test
    @WithMockUser(roles = "USER")
    void assignRoles_withoutAdminRole_returns403() throws Exception {
        RoleRequest request = new RoleRequest();
        request.setRoles(List.of("ADMIN"));

        mockMvc.perform(post("/admin/api/users/uid123/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void assignRoles_withAdminRoleAndValidBody_returns200() throws Exception {
        RoleRequest request = new RoleRequest();
        request.setRoles(List.of("ADMIN"));

        mockMvc.perform(post("/admin/api/users/uid123/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(roleManagementService).assignRoles("uid123", List.of("ADMIN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void assignRoles_withEmptyRoles_returns400() throws Exception {
        RoleRequest request = new RoleRequest();
        request.setRoles(List.of());

        mockMvc.perform(post("/admin/api/users/uid123/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
