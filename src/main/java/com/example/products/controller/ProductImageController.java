package com.example.products.controller;

import com.example.products.model.DisplayOrderRequest;
import com.example.products.model.ImageRequest;
import com.example.products.model.ImageResponse;
import com.example.products.service.ProductImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    @PostMapping
    public ResponseEntity<List<ImageResponse>> addImages(@PathVariable Long productId,
                                                         @Valid @RequestBody ImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productImageService.addImages(productId, request));
    }

    @GetMapping
    public ResponseEntity<List<ImageResponse>> getImages(@PathVariable Long productId) {
        return ResponseEntity.ok(productImageService.getImages(productId));
    }

    @PutMapping("/{imageId}")
    public ResponseEntity<ImageResponse> updateDisplayOrder(@PathVariable Long productId,
                                                            @PathVariable Long imageId,
                                                            @Valid @RequestBody DisplayOrderRequest request) {
        return ResponseEntity.ok(productImageService.updateDisplayOrder(productId, imageId, request));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long productId,
                                            @PathVariable Long imageId) {
        productImageService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
