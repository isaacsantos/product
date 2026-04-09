package com.example.products.service;

import com.example.products.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkImportServiceImplTest {

    @Mock
    private ProductService productService;

    @Mock
    private ProductImageService productImageService;

    @InjectMocks
    private BulkImportServiceImpl service;

    private AdminProductResponse mockProductResponse;

    @BeforeEach
    void setUp() {
        mockProductResponse = AdminProductResponse.builder()
                .id(42L)
                .name("Widget")
                .price(new BigDecimal("9.99"))
                .active(true)
                .build();
    }

    // --- Happy path ---

    @Test
    void singleValidRow_callsCreateAndAddImages_returnsSuccess() throws IOException {
        when(productService.createAdmin(any())).thenReturn(mockProductResponse);

        MockMultipartFile file = csv("Widget,A widget,9.99,https://example.com/img.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(0);

        ImportRowResult row = result.getRows().get(0);
        assertThat(row.getStatus()).isEqualTo(RowStatus.SUCCESS);
        assertThat(row.getProductId()).isEqualTo(42L);
        assertThat(row.getRowNumber()).isEqualTo(1);

        verify(productService).createAdmin(argThat(req ->
                "Widget".equals(req.getName()) &&
                new BigDecimal("9.99").compareTo(req.getPrice()) == 0
        ));
        verify(productImageService).addImages(eq(42L), argThat(req ->
                req.getUrls().equals(List.of("https://example.com/img.jpg"))
        ));
    }

    @Test
    void blankDescription_treatedAsValid_passedAsEmptyString() throws IOException {
        when(productService.createAdmin(any())).thenReturn(mockProductResponse);

        MockMultipartFile file = csv("Widget,,9.99,https://example.com/img.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(productService).createAdmin(argThat(req ->
                req.getDescription() == null || req.getDescription().isEmpty()
        ));
    }

    // --- Column count validation ---

    @Test
    void threeColumns_returnsFailed_processingContinues() throws IOException {
        when(productService.createAdmin(any())).thenReturn(mockProductResponse);

        MockMultipartFile file = csv("bad,row,only\nWidget,desc,9.99,https://example.com/img.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.FAILED);
        assertThat(result.getRows().get(1).getStatus()).isEqualTo(RowStatus.SUCCESS);
    }

    @Test
    void fiveColumns_returnsFailed_processingContinues() throws IOException {
        when(productService.createAdmin(any())).thenReturn(mockProductResponse);

        MockMultipartFile file = csv("a,b,c,d,e\nWidget,desc,9.99,https://example.com/img.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.FAILED);
        assertThat(result.getRows().get(1).getStatus()).isEqualTo(RowStatus.SUCCESS);
    }

    // --- Name validation ---

    @Test
    void blankName_returnsFailed_processingContinues() throws IOException {
        when(productService.createAdmin(any())).thenReturn(mockProductResponse);

        MockMultipartFile file = csv("  ,desc,9.99,https://example.com/img.jpg\nWidget,desc,9.99,https://example.com/img.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.FAILED);
        assertThat(result.getRows().get(1).getStatus()).isEqualTo(RowStatus.SUCCESS);
    }

    // --- Price validation ---

    @Test
    void nonNumericPrice_returnsFailed() throws IOException {
        MockMultipartFile file = csv("Widget,desc,notanumber,https://example.com/img.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.FAILED);
        assertThat(result.getRows().get(0).getErrorMessage()).isNotBlank();
    }

    @Test
    void zeroPrice_returnsFailed() throws IOException {
        MockMultipartFile file = csv("Widget,desc,0.00,https://example.com/img.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.FAILED);
    }

    @Test
    void negativePrice_returnsFailed() throws IOException {
        MockMultipartFile file = csv("Widget,desc,-1,https://example.com/img.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.FAILED);
    }

    // --- URL validation ---

    @Test
    void urlWithNoScheme_returnsFailed() throws IOException {
        MockMultipartFile file = csv("Widget,desc,9.99,example.com/img.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.FAILED);
    }

    @Test
    void ftpUrl_returnsFailed() throws IOException {
        MockMultipartFile file = csv("Widget,desc,9.99,ftp://example.com/img.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.FAILED);
    }

    @Test
    void blankUrl_returnsFailed() throws IOException {
        MockMultipartFile file = csv("Widget,desc,9.99,  ");
        ImportResult result = service.importProducts(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.FAILED);
    }

    // --- Exception isolation ---

    @Test
    void productServiceThrows_rowFailed_nextRowStillProcessed() throws IOException {
        when(productService.createAdmin(any()))
                .thenThrow(new RuntimeException("DB error"))
                .thenReturn(mockProductResponse);

        MockMultipartFile file = csv("Widget,desc,9.99,https://example.com/img.jpg\nWidget2,desc,5.00,https://example.com/img2.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.FAILED);
        assertThat(result.getRows().get(1).getStatus()).isEqualTo(RowStatus.SUCCESS);
    }

    @Test
    void productImageServiceThrows_rowFailed_nextRowStillProcessed() throws IOException {
        when(productService.createAdmin(any())).thenReturn(mockProductResponse);
        when(productImageService.addImages(anyLong(), any()))
                .thenThrow(new RuntimeException("Image error"))
                .thenReturn(List.of());

        MockMultipartFile file = csv("Widget,desc,9.99,https://example.com/img.jpg\nWidget2,desc,5.00,https://example.com/img2.jpg");
        ImportResult result = service.importProducts(file);

        assertThat(result.getRows().get(0).getStatus()).isEqualTo(RowStatus.FAILED);
        assertThat(result.getRows().get(1).getStatus()).isEqualTo(RowStatus.SUCCESS);
    }

    // --- Summary invariant ---

    @Test
    void summaryInvariant_totalRowsEqualsSumOfSuccessAndFailed() throws IOException {
        when(productService.createAdmin(any())).thenReturn(mockProductResponse);

        MockMultipartFile file = csv(
                "Widget,desc,9.99,https://example.com/img.jpg\n" +
                "bad,row,only\n" +
                "Widget2,desc,5.00,https://example.com/img2.jpg"
        );
        ImportResult result = service.importProducts(file);

        assertThat(result.getTotalRows()).isEqualTo(result.getSuccessCount() + result.getFailedCount());
    }

    // --- Row numbers ---

    @Test
    void rowNumbers_areOneBased_andMatchCsvLinePosition() throws IOException {
        when(productService.createAdmin(any())).thenReturn(mockProductResponse);

        MockMultipartFile file = csv(
                "Widget,desc,9.99,https://example.com/img.jpg\n" +
                "Widget2,desc,5.00,https://example.com/img2.jpg"
        );
        ImportResult result = service.importProducts(file);

        assertThat(result.getRows().get(0).getRowNumber()).isEqualTo(1);
        assertThat(result.getRows().get(1).getRowNumber()).isEqualTo(2);
    }

    // --- Helper ---

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv", content.getBytes());
    }
}
