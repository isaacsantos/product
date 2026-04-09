package com.example.products.service;

import com.example.products.model.AdminProductResponse;
import com.example.products.model.PageResponse;
import com.example.products.model.ProductRequest;
import com.example.products.model.ProductResponse;
import com.example.products.model.PublicProductResponse;

import java.util.Set;

public interface ProductService {

    // Legacy method — kept for backward compatibility (used by BulkImportServiceImpl until task 6.1)
    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);

    ProductResponse setTags(Long productId, Set<Long> tagIds);

    // Public endpoints — return PublicProductResponse (no active field)
    PublicProductResponse findById(Long id);

    PageResponse<PublicProductResponse> findAll(int page, int size, Set<Long> tagIds);

    // Admin endpoints — return AdminProductResponse (includes active field)
    AdminProductResponse createAdmin(ProductRequest request);

    PageResponse<AdminProductResponse> findAllAdmin(int page, int size, Set<Long> tagIds);

    AdminProductResponse findByIdAdmin(Long id);

    AdminProductResponse updateAdmin(Long id, ProductRequest request);

    AdminProductResponse setTagsAdmin(Long productId, Set<Long> tagIds);
}
