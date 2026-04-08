package com.example.products;

import com.example.products.config.CorsProperties;
import com.example.products.config.SecurityConfig;
import com.example.products.controller.ProductController;
import com.example.products.controller.PublicProductController;
import com.example.products.service.ProductService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@WebMvcTest({ProductController.class, PublicProductController.class})
@Import({SecurityConfig.class, SecurityConfigIT.TestConfig.class})
@TestPropertySource(properties = {
        "firebase.project-id=test-project",
        "security.cors.allowed-origins=http://localhost:3000"
})
class SecurityConfigIT {

    @TestConfiguration
    @EnableConfigurationProperties(CorsProperties.class)
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void getProductsWithoutToken_returns200() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    void getProductByIdWithoutToken_returns200orNotFound() throws Exception {
        mockMvc.perform(get("/api/products/1"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 200 || status == 404
                            : "Expected 200 or 404 but got " + status;
                });
    }

    @Test
    void postWithoutToken_returns401() throws Exception {
        mockMvc.perform(post("/admin/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":1.00}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void putWithoutToken_returns401() throws Exception {
        mockMvc.perform(put("/admin/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":1.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteWithoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/admin/api/products/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithValidJwt_isAllowed() throws Exception {
        mockMvc.perform(post("/admin/api/products")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":1.00}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status != 401 && status != 403
                            : "Expected not 401/403 but got " + status;
                });
    }

    @Test
    void postWithNoToken_returns401() throws Exception {
        mockMvc.perform(post("/admin/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":1.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void optionsPreflightWithoutToken_returns200() throws Exception {
        mockMvc.perform(options("/admin/api/products")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    void noSessionCreated() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
