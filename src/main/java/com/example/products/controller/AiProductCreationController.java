package com.example.products.controller;

import com.example.products.model.ErrorResponse;
import com.example.products.model.InMemoryFile;
import com.example.products.service.AiProductCreationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/products")
@RequiredArgsConstructor
public class AiProductCreationController {

    private final AiProductCreationService aiProductCreationService;

    @PostMapping(value = "/ai-create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> createProductsFromImages(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return badRequest("At least one image file is required");
        }

        // Read bytes into memory before returning response,
        // because temp files are cleaned up after the request completes
        List<InMemoryFile> inMemoryFiles;
        try {
            inMemoryFiles = files.stream()
                    .map(f -> {
                        try {
                            return new InMemoryFile(f.getOriginalFilename(), f.getContentType(), f.getBytes());
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read uploaded file", e);
                        }
                    })
                    .toList();
        } catch (RuntimeException e) {
            return badRequest("Failed to read uploaded files: " + e.getMessage());
        }

        aiProductCreationService.createProductsFromImages(inMemoryFiles);

        return ResponseEntity.accepted().body(Map.of(
                "message", "Processing started. Products will be created in the background as inactive.",
                "totalImages", files.size()
        ));
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
