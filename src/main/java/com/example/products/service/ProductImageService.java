package com.example.products.service;

import com.example.products.model.DisplayOrderRequest;
import com.example.products.model.ImageRequest;
import com.example.products.model.ImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductImageService {

    List<ImageResponse> addImages(Long productId, ImageRequest request);

    List<ImageResponse> uploadImages(Long productId, List<MultipartFile> files);

    List<ImageResponse> getImages(Long productId);

    ImageResponse updateDisplayOrder(Long productId, Long imageId, DisplayOrderRequest request);

    void deleteImage(Long productId, Long imageId);
}
