package com.example.products.controller;

import com.example.products.model.ErrorResponse;
import com.example.products.model.ImportResult;
import com.example.products.service.BulkImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;

@RestController
@RequestMapping("/admin/api/products")
public class BulkImportController {

    private final BulkImportService bulkImportService;

    public BulkImportController(BulkImportService bulkImportService) {
        this.bulkImportService = bulkImportService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> importProducts(@RequestParam("file") MultipartFile file) throws IOException {
        // Validate file presence
        if (file == null || file.isEmpty()) {
            return badRequest("File is missing or empty");
        }

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("text/csv") && !contentType.equals("application/octet-stream"))) {
            return badRequest("Invalid content type: only text/csv or application/octet-stream are accepted");
        }

        // Validate file has non-blank content
        if (file.getSize() == 0 || hasOnlyBlankLines(file)) {
            return badRequest("File is empty or contains only blank lines");
        }

        ImportResult result = bulkImportService.importProducts(file);
        return ResponseEntity.ok(result);
    }

    private boolean hasOnlyBlankLines(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            return reader.lines().allMatch(String::isBlank);
        }
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(message)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.badRequest().body(error);
    }
}
