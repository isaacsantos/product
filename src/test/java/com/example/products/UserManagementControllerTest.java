package com.example.products;

import com.example.products.controller.UserManagementController;
import com.example.products.exception.FirebaseUserAlreadyExistsException;
import com.example.products.exception.GlobalExceptionHandler;
import com.example.products.model.CreateUserRequest;
import com.example.products.model.UserResponse;
import com.example.products.service.UserManagementService;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserManagementController.class)
@Import(GlobalExceptionHandler.class)
@EnableMethodSecurity
class UserManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserManagementService userManagementService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_success_noRoles_returns201() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");

        UserResponse response = UserResponse.builder()
                .uid("uid1")
                .email("test@example.com")
                .displayName(null)
                .roles(List.of())
                .build();

        when(userManagementService.createUser(any())).thenReturn(response);

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uid").value("uid1"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_success_withRoles_returns201() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");
        request.setRoles(List.of("ADMIN"));

        UserResponse response = UserResponse.builder()
                .uid("uid1")
                .email("test@example.com")
                .roles(List.of("ADMIN"))
                .build();

        when(userManagementService.createUser(any())).thenReturn(response);

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_invalidEmail_returns400() throws Exception {
        String body = "{\"email\":\"not-an-email\"}";

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_duplicateEmail_returns409() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");

        when(userManagementService.createUser(any()))
                .thenThrow(new FirebaseUserAlreadyExistsException("test@example.com"));

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_sdkError_returns500() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");

        when(userManagementService.createUser(any()))
                .thenThrow(new RuntimeException("SDK error"));

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void createUser_noToken_returns401() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createUser_insufficientRole_returns403() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_nullDisplayName_returns201WithNullDisplayName() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");
        request.setDisplayName(null);

        UserResponse response = UserResponse.builder()
                .uid("uid1")
                .email("test@example.com")
                .displayName(null)
                .roles(List.of())
                .build();

        when(userManagementService.createUser(any())).thenReturn(response);

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value(nullValue()));
    }
}
