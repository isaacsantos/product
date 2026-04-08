package com.example.products;

import com.example.products.config.CorsProperties;
import com.example.products.config.SecurityConfig;
import com.example.products.controller.BulkImportController;
import com.example.products.model.ImportResult;
import com.example.products.model.ImportRowResult;
import com.example.products.model.RowStatus;
import com.example.products.service.BulkImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BulkImportController.class)
@Import({SecurityConfig.class, BulkImportControllerTest.TestConfig.class})
@TestPropertySource(properties = {
        "firebase.project-id=test-project",
        "security.cors.allowed-origins=http://localhost:3000"
})
class BulkImportControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(CorsProperties.class)
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    BulkImportService bulkImportService;

    private static final String IMPORT_URL = "/admin/api/products/import";

    private MockMultipartFile validCsvFile() {
        return new MockMultipartFile("file", "import.csv", "text/csv",
                "Widget,A widget,9.99,https://example.com/img.jpg".getBytes());
    }

    private ImportResult sampleResult() {
        return ImportResult.builder()
                .totalRows(1)
                .successCount(1)
                .failedCount(0)
                .rows(List.of(ImportRowResult.builder()
                        .rowNumber(1)
                        .status(RowStatus.SUCCESS)
                        .productId(1L)
                        .build()))
                .build();
    }

    // --- Authentication ---

    @Test
    void noJwt_returns401() throws Exception {
        mockMvc.perform(multipart(IMPORT_URL).file(validCsvFile()))
                .andExpect(status().isUnauthorized());
    }

    // --- Authorization ---

    @Test
    void jwtWithoutAdminOrManagerRole_returns403() throws Exception {
        mockMvc.perform(multipart(IMPORT_URL)
                        .file(validCsvFile())
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")
                        )))
                .andExpect(status().isForbidden());
    }

    // --- File validation ---

    @Test
    void missingFilePart_returns400() throws Exception {
        mockMvc.perform(multipart(IMPORT_URL)
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")
                        )))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongContentType_returns400() throws Exception {
        MockMultipartFile pngFile = new MockMultipartFile("file", "image.png", "image/png",
                "fake image data".getBytes());

        mockMvc.perform(multipart(IMPORT_URL)
                        .file(pngFile)
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")
                        )))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart(IMPORT_URL)
                        .file(emptyFile)
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")
                        )))
                .andExpect(status().isBadRequest());
    }

    // --- Successful import ---

    @Test
    void validCsvWithAdminJwt_returns200WithImportResult() throws Exception {
        when(bulkImportService.importProducts(any())).thenReturn(sampleResult());

        mockMvc.perform(multipart(IMPORT_URL)
                        .file(validCsvFile())
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(0));
    }

    @Test
    void validCsvWithManagerJwt_returns200WithImportResult() throws Exception {
        when(bulkImportService.importProducts(any())).thenReturn(sampleResult());

        mockMvc.perform(multipart(IMPORT_URL)
                        .file(validCsvFile())
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MANAGER")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1));
    }
}
