package com.example.products.controller;

import com.example.products.model.DisplayOrderRequest;
import com.example.products.model.ImageRequest;
import com.example.products.model.ImageResponse;
import com.example.products.service.ProductImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/admin/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<ImageResponse>> addImages(@PathVariable Long productId,
                                                         @Valid @RequestBody ImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productImageService.addImages(productId, request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<ImageResponse>> uploadImages(@PathVariable Long productId,
                                                            @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productImageService.uploadImages(productId, files));
    }

    @GetMapping
    public ResponseEntity<List<ImageResponse>> getImages(@PathVariable Long productId) {
        return ResponseEntity.ok(productImageService.getImages(productId));
    }

    @PutMapping("/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ImageResponse> updateDisplayOrder(@PathVariable Long productId,
                                                            @PathVariable Long imageId,
                                                            @Valid @RequestBody DisplayOrderRequest request) {
        return ResponseEntity.ok(productImageService.updateDisplayOrder(productId, imageId, request));
    }

    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteImage(@PathVariable Long productId,
                                            @PathVariable Long imageId) {
        productImageService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
