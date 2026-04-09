package com.example.products.service;

import com.example.products.model.AdminProductResponse;
import com.example.products.model.ImportResult;
import com.example.products.model.ProductRequest;
import com.example.products.model.RowStatus;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.stream.Stream;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for BulkImportService active status behaviour.
 */
@ExtendWith(MockitoExtension.class)
class BulkImportActiveStatusPropertyTest {

    @Mock
    private ProductService productService;

    @Mock
    private ProductImageService productImageService;

    @InjectMocks
    private BulkImportServiceImpl bulkImportService;

    // -------------------------------------------------------------------------
    // Shared CSV row data source — 10 valid rows
    // -------------------------------------------------------------------------

    static Stream<String> validCsvRows() {
        return Stream.of(
            "Rosa Roja,Flor roja clásica,9.99,https://example.com/rosa-roja.jpg",
            "Tulipán Amarillo,Tulipán de primavera,14.50,https://example.com/tulipan.jpg",
            "Orquídea Blanca,Orquídea elegante,29.99,https://example.com/orquidea.jpg",
            "Girasol,Flor del sol,7.00,https://example.com/girasol.jpg",
            "Lavanda,Aroma relajante,12.00,https://example.com/lavanda.jpg",
            "Margarita,Flor silvestre,5.50,https://example.com/margarita.jpg",
            "Clavel Rojo,Clavel tradicional,8.25,https://example.com/clavel.jpg",
            "Lirio Azul,Lirio de jardín,19.99,https://example.com/lirio.jpg",
            "Peonia Rosa,Peonia de verano,22.00,https://example.com/peonia.jpg",
            "Crisantemo,Flor otoñal,11.75,https://example.com/crisantemo.jpg"
        );
    }

    // -------------------------------------------------------------------------
    // Property 5: BulkImport crea productos con active = true
    // Validates: Requirements 3.2, 7.1
    // -------------------------------------------------------------------------

    /**
     * Feature: product-active-status, Property 5: BulkImport crea productos con active = true
     * Validates: Requirements 3.2, 7.1
     */
    @ParameterizedTest
    @MethodSource("validCsvRows")
    void bulkImportCreatesProductWithActiveTrue(String csvRow) throws IOException {
        AdminProductResponse mockResponse = AdminProductResponse.builder()
                .id(1L)
                .active(true)
                .build();

        ArgumentCaptor<ProductRequest> captor = ArgumentCaptor.forClass(ProductRequest.class);
        when(productService.createAdmin(captor.capture())).thenReturn(mockResponse);
        when(productImageService.addImages(any(), any())).thenReturn(List.of());

        MockMultipartFile file = new MockMultipartFile(
                "file", "import.csv", "text/csv", csvRow.getBytes());

        bulkImportService.importProducts(file);

        ProductRequest captured = captor.getValue();
        assertThat(captured.isActive()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Property 6: Import result refleja el estado active del producto
    // Validates: Requirements 7.2
    // -------------------------------------------------------------------------

    /**
     * Feature: product-active-status, Property 6: Import result refleja el estado active del producto
     * Validates: Requirements 7.2
     */
    @ParameterizedTest
    @MethodSource("validCsvRows")
    void bulkImportResultRowIsSuccessAndResponseHasActiveTrue(String csvRow) throws IOException {
        AdminProductResponse mockResponse = AdminProductResponse.builder()
                .id(42L)
                .active(true)
                .build();

        when(productService.createAdmin(any())).thenReturn(mockResponse);
        when(productImageService.addImages(any(), any())).thenReturn(List.of());

        MockMultipartFile file = new MockMultipartFile(
                "file", "import.csv", "text/csv", csvRow.getBytes());

        ImportResult result = bulkImportService.importProducts(file);

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.SUCCESS);
        assertThat(mockResponse.isActive()).isTrue();
    }
}
