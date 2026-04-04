package com.example.products.service;

import com.example.products.model.DisplayOrderRequest;
import com.example.products.model.ImageRequest;
import com.example.products.model.ImageResponse;

import java.util.List;

public interface ProductImageService {

    List<ImageResponse> addImages(Long productId, ImageRequest request);

    List<ImageResponse> getImages(Long productId);

    ImageResponse updateDisplayOrder(Long productId, Long imageId, DisplayOrderRequest request);

    void deleteImage(Long productId, Long imageId);
}
