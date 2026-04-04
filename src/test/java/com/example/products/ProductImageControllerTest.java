package com.example.products;

import com.example.products.config.CorsProperties;
import com.example.products.config.SecurityConfig;
import com.example.products.controller.ProductImageController;
import com.example.products.model.ImageResponse;
import com.example.products.service.ProductImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductImageController.class)
@Import({SecurityConfig.class, ProductImageControllerTest.TestConfig.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:certs/public.pem",
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
    ProductImageService productImageService;

    // ── Unauthenticated requests ─────────────────────────────────────────────

    @Test
    void postImages_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/products/1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"https://example.com/img.jpg\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putDisplayOrder_withoutJwt_returns401() throws Exception {
        mockMvc.perform(put("/api/products/1/images/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteImage_withoutJwt_returns401() throws Exception {
        mockMvc.perform(delete("/api/products/1/images/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── Public GET endpoint ──────────────────────────────────────────────────

    @Test
    void getImages_withoutJwt_returns200() throws Exception {
        when(productImageService.getImages(anyLong())).thenReturn(List.of());

        mockMvc.perform(get("/api/products/1/images"))
                .andExpect(status().isOk());
    }

    // ── Validation with valid JWT ────────────────────────────────────────────

    @Test
    void postImages_withValidJwt_invalidUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/products/1/images")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"ftp://bad\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postImages_withValidJwt_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/products/1/images")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putDisplayOrder_withValidJwt_negativeDisplayOrder_returns400() throws Exception {
        mockMvc.perform(put("/api/products/1/images/1")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putDisplayOrder_withValidJwt_nullDisplayOrder_returns400() throws Exception {
        mockMvc.perform(put("/api/products/1/images/1")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":null}"))
                .andExpect(status().isBadRequest());
    }
}
