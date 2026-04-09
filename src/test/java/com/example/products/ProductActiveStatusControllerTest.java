package com.example.products;

import com.example.products.config.CorsProperties;
import com.example.products.config.SecurityConfig;
import com.example.products.controller.ProductController;
import com.example.products.controller.PublicProductController;
import com.example.products.model.AdminProductResponse;
import com.example.products.model.PublicProductResponse;
import com.example.products.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for ProductController and PublicProductController
 * verifying the active field exposure per requirements 4.1 and 5.1.
 */
@WebMvcTest({ProductController.class, PublicProductController.class})
@Import({SecurityConfig.class, ProductActiveStatusControllerTest.TestConfig.class})
@TestPropertySource(properties = {
        "firebase.project-id=test-project",
        "security.cors.allowed-origins=http://localhost:3000"
})
class ProductActiveStatusControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(CorsProperties.class)
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    ProductService productService;

    /**
     * Validates: Requirements 4.1
     * Admin endpoint POST /admin/api/products must return JSON containing the "active" field.
     */
    @Test
    void adminEndpoint_returnsActiveFieldInJson() throws Exception {
        AdminProductResponse response = AdminProductResponse.builder()
                .id(1L)
                .name("Test")
                .price(new BigDecimal("9.99"))
                .active(true)
                .build();

        when(productService.createAdmin(any())).thenReturn(response);

        mockMvc.perform(post("/admin/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":9.99,\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").exists());
    }

    /**
     * Validates: Requirements 5.1
     * Public endpoint GET /api/products/{id} must return JSON NOT containing the "active" field.
     */
    @Test
    void publicEndpoint_doesNotReturnActiveFieldInJson() throws Exception {
        PublicProductResponse response = PublicProductResponse.builder()
                .id(1L)
                .name("Test")
                .price(new BigDecimal("9.99"))
                .build();

        when(productService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"active\""))));
    }
}
