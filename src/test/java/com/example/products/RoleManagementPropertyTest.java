package com.example.products;

import com.example.products.controller.RoleManagementController;
import com.example.products.exception.FirebaseUserNotFoundException;
import com.example.products.exception.GlobalExceptionHandler;
import com.example.products.model.RoleRequest;
import com.example.products.service.RoleManagementService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoleManagementController.class)
@Import(GlobalExceptionHandler.class)
@EnableMethodSecurity
@WithMockUser(roles = "ADMIN")
class RoleManagementPropertyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleManagementService roleManagementService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Property 5 — Delegación correcta de roles al service
    // -------------------------------------------------------------------------

    record UidAndRoles(String uid, List<String> roles) {}

    static Stream<UidAndRoles> uidAndRolesSamples() {
        return Stream.of(
            new UidAndRoles("abc",          List.of("ADMIN")),
            new UidAndRoles("user123",      List.of("USER", "MANAGER")),
            new UidAndRoles("A",            List.of("EDITOR")),
            new UidAndRoles("ZZZzzz",       List.of("ADMIN", "USER")),
            new UidAndRoles("firebaseUID",  List.of("VIEWER")),
            new UidAndRoles("testUid",      List.of("ADMIN", "EDITOR", "VIEWER")),
            new UidAndRoles("aBcDeFgH",     List.of("ROLE")),
            new UidAndRoles("uid00000001",  List.of("X", "Y")),
            new UidAndRoles("longUidValue", List.of("MANAGER")),
            new UidAndRoles("shortU",       List.of("ADMIN", "USER", "MANAGER"))
        );
    }

    // Feature: firebase-auth-integration, Property 5: Delegación correcta de roles al service
    @ParameterizedTest
    @MethodSource("uidAndRolesSamples")
    void controllerDelegatesExactRolesToService(UidAndRoles sample) throws Exception {
        RoleRequest request = new RoleRequest();
        request.setRoles(sample.roles());

        mockMvc.perform(post("/admin/api/users/{uid}/roles", sample.uid())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(roleManagementService).assignRoles(eq(sample.uid()), eq(sample.roles()));
    }

    // -------------------------------------------------------------------------
    // Property 6 — UID inexistente resulta en HTTP 404
    // -------------------------------------------------------------------------

    static Stream<String> unknownUidSamples() {
        return Stream.of(
            "unknownA",
            "notFound",
            "missingUser",
            "ghostUID",
            "noSuchUser",
            "abcdefgh",
            "ZZZZZZZZ",
            "uid404",
            "testMissing",
            "xYzAbCdE"
        );
    }

    // Feature: firebase-auth-integration, Property 6: UID inexistente resulta en HTTP 404
    @ParameterizedTest
    @MethodSource("unknownUidSamples")
    void unknownUidReturns404(String uid) throws Exception {
        doThrow(new FirebaseUserNotFoundException(uid))
                .when(roleManagementService).assignRoles(eq(uid), any());

        RoleRequest request = new RoleRequest();
        request.setRoles(List.of("ADMIN"));

        mockMvc.perform(post("/admin/api/users/{uid}/roles", uid)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }

    // -------------------------------------------------------------------------
    // Property 7 — Error del SDK resulta en HTTP 500
    // -------------------------------------------------------------------------

    static Stream<String> sdkErrorUidSamples() {
        return Stream.of(
            "sdkErrorA",
            "sdkErrorB",
            "firebaseDown",
            "networkFail",
            "timeoutUID",
            "internalErr",
            "crashUID",
            "fatalError",
            "unexpectedX",
            "runtimeFail"
        );
    }

    // Feature: firebase-auth-integration, Property 7: Error del SDK resulta en HTTP 500
    @ParameterizedTest
    @MethodSource("sdkErrorUidSamples")
    void sdkErrorReturns500(String uid) throws Exception {
        doThrow(new RuntimeException("Firebase SDK error"))
                .when(roleManagementService).assignRoles(eq(uid), any());

        RoleRequest request = new RoleRequest();
        request.setRoles(List.of("ADMIN"));

        mockMvc.perform(post("/admin/api/users/{uid}/roles", uid)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }
}
