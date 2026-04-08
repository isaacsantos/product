package com.example.products;

import com.example.products.config.CorsProperties;
import com.example.products.config.SecurityConfig;
import com.example.products.controller.ProductImageController;
import com.example.products.service.ProductImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductImageController.class)
@Import({SecurityConfig.class, ProductImageControllerTest.TestConfig.class})
@TestPropertySource(properties = {
        "firebase.project-id=test-project",
        "security.cors.allowed-origins=http://localhost:3000"
})
class ProductImageControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(CorsProperties.class)
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    ProductImageService productImageService;

    // ── Unauthenticated requests ─────────────────────────────────────────────

    @Test
    void postImages_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/admin/api/products/1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"https://example.com/img.jpg\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putDisplayOrder_withoutJwt_returns401() throws Exception {
        mockMvc.perform(put("/admin/api/products/1/images/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteImage_withoutJwt_returns401() throws Exception {
        mockMvc.perform(delete("/admin/api/products/1/images/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── Public GET endpoint ──────────────────────────────────────────────────

    @Test
    void getImages_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/admin/api/products/1/images"))
                .andExpect(status().isUnauthorized());
    }

    // ── Validation with valid JWT ────────────────────────────────────────────

    @Test
    void postImages_withValidJwt_invalidUrl_returns400() throws Exception {
        mockMvc.perform(post("/admin/api/products/1/images")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"ftp://bad\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postImages_withValidJwt_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/admin/api/products/1/images")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putDisplayOrder_withValidJwt_negativeDisplayOrder_returns400() throws Exception {
        mockMvc.perform(put("/admin/api/products/1/images/1")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putDisplayOrder_withValidJwt_nullDisplayOrder_returns400() throws Exception {
        mockMvc.perform(put("/admin/api/products/1/images/1")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":null}"))
                .andExpect(status().isBadRequest());
    }
}
