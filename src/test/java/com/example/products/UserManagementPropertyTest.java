package com.example.products;

import com.example.products.controller.UserManagementController;
import com.example.products.exception.FirebaseUserAlreadyExistsException;
import com.example.products.exception.GlobalExceptionHandler;
import com.example.products.model.UserResponse;
import com.example.products.service.UserManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserManagementController.class)
@Import(GlobalExceptionHandler.class)
@EnableMethodSecurity
@WithMockUser(roles = "ADMIN")
class UserManagementPropertyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserManagementService userManagementService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Property 1 — Creación válida retorna respuesta completa
    // Feature: user-management, Property 1: Creación válida retorna respuesta completa
    // Validates: Requirements 1.2, 1.4, 5.1, 5.3
    // -------------------------------------------------------------------------

    static Stream<String> validEmailSamples() {
        return Stream.of(
            "user@example.com",
            "test.user@domain.org",
            "admin@company.io",
            "hello+tag@mail.net",
            "firstname.lastname@subdomain.example.com",
            "user123@test.co",
            "a@b.com",
            "contact@my-domain.es",
            "info@startup.dev",
            "support@service.app"
        );
    }

    @ParameterizedTest
    @MethodSource("validEmailSamples")
    void validEmailReturnsCompleteResponse(String email) throws Exception {
        UserResponse response = UserResponse.builder()
                .uid("uid-" + email)
                .email(email)
                .displayName(null)
                .roles(List.of())
                .build();
        when(userManagementService.createUser(any())).thenReturn(response);

        String body = objectMapper.writeValueAsString(java.util.Map.of("email", email));

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uid").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles").isArray());
    }

    // -------------------------------------------------------------------------
    // Property 2 — Email inválido es rechazado con 400
    // Feature: user-management, Property 2: Email inválido es rechazado con 400
    // Validates: Requirements 2.1, 2.3
    // -------------------------------------------------------------------------

    static Stream<String> invalidEmailSamples() {
        return Stream.of(
            "notanemail",
            "missing@",
            "@nodomain",
            "spaces in@email.com",
            "double@@domain.com",
            "plaintext",
            "no-at-sign",
            "@",
            "just@@double",
            "a".repeat(255) + "@x.com"
        );
    }

    @ParameterizedTest
    @MethodSource("invalidEmailSamples")
    void invalidEmailIsRejectedWith400(String invalidEmail) throws Exception {
        String body = "{\"email\": \"" + invalidEmail + "\"}";

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Property 3 — displayName excesivo es rechazado con 400
    // Feature: user-management, Property 3: displayName excesivo es rechazado con 400
    // Validates: Requirements 2.2
    // -------------------------------------------------------------------------

    static Stream<String> longDisplayNameSamples() {
        return Stream.of(
            "a".repeat(257),
            "b".repeat(300),
            "c".repeat(500),
            "d".repeat(1000),
            "e".repeat(258),
            "f".repeat(260),
            "g".repeat(400),
            "h".repeat(512),
            "i".repeat(2000),
            "j".repeat(257)
        );
    }

    @ParameterizedTest
    @MethodSource("longDisplayNameSamples")
    void excessiveDisplayNameIsRejectedWith400(String longName) throws Exception {
        String body = "{\"email\": \"valid@example.com\", \"displayName\": \"" + longName + "\"}";

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Property 4 — Roles del request se reflejan en la respuesta
    // Feature: user-management, Property 4: Roles del request se reflejan en la respuesta
    // Validates: Requirements 6.2, 6.5
    // -------------------------------------------------------------------------

    static Stream<List<String>> nonEmptyRoleListSamples() {
        return Stream.of(
            List.of("ADMIN"),
            List.of("USER", "MANAGER"),
            List.of("EDITOR"),
            List.of("ADMIN", "USER"),
            List.of("VIEWER"),
            List.of("ADMIN", "EDITOR", "VIEWER"),
            List.of("MANAGER", "USER"),
            List.of("ROLE_CUSTOM"),
            List.of("ADMIN", "USER", "MANAGER"),
            List.of("SUPER_ADMIN", "EDITOR")
        );
    }

    @ParameterizedTest
    @MethodSource("nonEmptyRoleListSamples")
    void rolesInRequestAreReflectedInResponse(List<String> roleList) throws Exception {
        UserResponse response = UserResponse.builder()
                .uid("uid1")
                .email("test@example.com")
                .roles(roleList)
                .build();
        when(userManagementService.createUser(any())).thenReturn(response);

        java.util.Map<String, Object> requestMap = new java.util.HashMap<>();
        requestMap.put("email", "test@example.com");
        requestMap.put("roles", roleList);
        String body = objectMapper.writeValueAsString(requestMap);

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles.length()").value(roleList.size()));
    }

    // -------------------------------------------------------------------------
    // Property 5 — Ausencia de roles produce lista vacía en respuesta
    // Feature: user-management, Property 5: Ausencia de roles produce lista vacía en respuesta
    // Validates: Requirements 6.3, 6.4, 6.6
    // -------------------------------------------------------------------------

    static Stream<String> nullOrEmptyRolesSamples() {
        return Stream.of(
            "{\"email\": \"user1@example.com\", \"roles\": null}",
            "{\"email\": \"user2@example.com\", \"roles\": null}",
            "{\"email\": \"user3@example.com\", \"roles\": null}",
            "{\"email\": \"user4@example.com\", \"roles\": null}",
            "{\"email\": \"user5@example.com\", \"roles\": null}",
            "{\"email\": \"user6@example.com\", \"roles\": []}",
            "{\"email\": \"user7@example.com\", \"roles\": []}",
            "{\"email\": \"user8@example.com\", \"roles\": []}",
            "{\"email\": \"user9@example.com\", \"roles\": []}",
            "{\"email\": \"user10@example.com\", \"roles\": []}"
        );
    }

    @ParameterizedTest
    @MethodSource("nullOrEmptyRolesSamples")
    void nullOrEmptyRolesProducesEmptyRolesInResponse(String jsonBody) throws Exception {
        UserResponse response = UserResponse.builder()
                .uid("uid1")
                .email("test@example.com")
                .roles(List.of())
                .build();
        when(userManagementService.createUser(any())).thenReturn(response);

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    // -------------------------------------------------------------------------
    // Property 6 — Email duplicado produce HTTP 409
    // Feature: user-management, Property 6: Email duplicado produce HTTP 409
    // Validates: Requirements 3.1, 3.2
    // -------------------------------------------------------------------------

    static Stream<String> duplicateEmailSamples() {
        return Stream.of(
            "duplicate@example.com",
            "existing@domain.org",
            "taken@company.io",
            "already@registered.net",
            "inuse@mail.com",
            "conflict@test.co",
            "repeat@service.app",
            "double@startup.dev",
            "used@mysite.es",
            "occupied@platform.io"
        );
    }

    @ParameterizedTest
    @MethodSource("duplicateEmailSamples")
    void duplicateEmailProduces409(String email) throws Exception {
        doThrow(new FirebaseUserAlreadyExistsException(email))
                .when(userManagementService).createUser(any());

        String body = objectMapper.writeValueAsString(java.util.Map.of("email", email));

        mockMvc.perform(post("/admin/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").exists());
    }
}
