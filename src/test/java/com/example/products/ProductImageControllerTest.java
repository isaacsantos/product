package com.example.products;

import com.example.products.config.CorsProperties;
import com.example.products.config.SecurityConfig;
import com.example.products.controller.ProductImageController;
import com.example.products.exception.CloudinaryUploadException;
import com.example.products.exception.InvalidImageTypeException;
import com.example.products.model.ImageResponse;
import com.example.products.service.ProductImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    @Test
    void getImages_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/admin/api/products/1/images"))
                .andExpect(status().isUnauthorized());
    }

    // ── Validation with valid JWT ────────────────────────────────────────────

    @Test
    void postImages_withValidJwt_invalidUrl_returns400() throws Exception {
        mockMvc.perform(post("/admin/api/products/1/images")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"ftp://bad\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postImages_withValidJwt_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/admin/api/products/1/images")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putDisplayOrder_withValidJwt_negativeDisplayOrder_returns400() throws Exception {
        mockMvc.perform(put("/admin/api/products/1/images/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putDisplayOrder_withValidJwt_nullDisplayOrder_returns400() throws Exception {
        mockMvc.perform(put("/admin/api/products/1/images/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayOrder\":null}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /upload ─────────────────────────────────────────────────────────

    @Test
    void uploadImages_withoutJwt_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "img.jpg", "image/jpeg", "data".getBytes());
        mockMvc.perform(multipart("/admin/api/products/1/images/upload").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadImages_withInsufficientRole_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "img.jpg", "image/jpeg", "data".getBytes());
        mockMvc.perform(multipart("/admin/api/products/1/images/upload")
                        .file(file)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadImages_withInvalidContentType_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "doc.pdf", "application/pdf", "data".getBytes());
        when(productImageService.uploadImages(anyLong(), anyList()))
                .thenThrow(new InvalidImageTypeException("application/pdf"));

        mockMvc.perform(multipart("/admin/api/products/1/images/upload")
                        .file(file)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadImages_withNoFiles_returns400() throws Exception {
        mockMvc.perform(multipart("/admin/api/products/1/images/upload")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadImages_withCloudinaryError_returns502() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "img.jpg", "image/jpeg", "data".getBytes());
        when(productImageService.uploadImages(anyLong(), anyList()))
                .thenThrow(new CloudinaryUploadException("Cloudinary upload failed", new RuntimeException()));

        mockMvc.perform(multipart("/admin/api/products/1/images/upload")
                        .file(file)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadGateway());
    }

    @Test
    void uploadImages_success_returns201WithImageResponses() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "img.jpg", "image/jpeg", "data".getBytes());
        List<ImageResponse> responses = List.of(
                ImageResponse.builder().id(1L).productId(1L).url("https://res.cloudinary.com/img.jpg").displayOrder(0).build()
        );
        when(productImageService.uploadImages(eq(1L), anyList())).thenReturn(responses);

        mockMvc.perform(multipart("/admin/api/products/1/images/upload")
                        .file(file)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].url").value("https://res.cloudinary.com/img.jpg"))
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].displayOrder").value(0));
    }
}
