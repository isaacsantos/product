package com.example.products.service;

import com.example.products.model.PageResponse;
import com.example.products.model.ProductRequest;
import com.example.products.model.ProductResponse;

import java.util.Set;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    PageResponse<ProductResponse> findAll(int page, int size);

    ProductResponse findById(Long id);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);

    ProductResponse setTags(Long productId, Set<Long> tagIds);
}
