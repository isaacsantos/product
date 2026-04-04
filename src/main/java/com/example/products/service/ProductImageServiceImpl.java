package com.example.products.service;

import com.example.products.exception.ProductImageNotFoundException;
import com.example.products.exception.ProductNotFoundException;
import com.example.products.model.DisplayOrderRequest;
import com.example.products.model.ImageRequest;
import com.example.products.model.ImageResponse;
import com.example.products.model.ProductImage;
import com.example.products.repository.ProductImageRepository;
import com.example.products.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;

    public ProductImageServiceImpl(ProductImageRepository imageRepository, ProductRepository productRepository) {
        this.imageRepository = imageRepository;
        this.productRepository = productRepository;
    }

    @Override
    public List<ImageResponse> addImages(Long productId, ImageRequest request) {
        verifyProductExists(productId);
        List<ProductImage> existing = imageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        int nextOrder = existing.size();
        List<ProductImage> images = new java.util.ArrayList<>();
        for (String url : request.getUrls()) {
            images.add(ProductImage.builder()
                    .productId(productId)
                    .url(url)
                    .displayOrder(nextOrder++)
                    .build());
        }
        return imageRepository.saveAll(images).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ImageResponse> getImages(Long productId) {
        verifyProductExists(productId);
        return imageRepository.findByProductIdOrderByDisplayOrderAsc(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ImageResponse updateDisplayOrder(Long productId, Long imageId, DisplayOrderRequest request) {
        verifyProductExists(productId);
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ProductImageNotFoundException(imageId));
        if (!image.getProductId().equals(productId)) {
            throw new ProductImageNotFoundException(imageId);
        }
        image.setDisplayOrder(request.getDisplayOrder());
        return toResponse(imageRepository.save(image));
    }

    @Override
    public void deleteImage(Long productId, Long imageId) {
        verifyProductExists(productId);
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ProductImageNotFoundException(imageId));
        if (!image.getProductId().equals(productId)) {
            throw new ProductImageNotFoundException(imageId);
        }
        imageRepository.deleteById(imageId);
    }

    private void verifyProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
    }

    private ImageResponse toResponse(ProductImage image) {
        return ImageResponse.builder()
                .id(image.getId())
                .productId(image.getProductId())
                .url(image.getUrl())
                .displayOrder(image.getDisplayOrder())
                .build();
    }
}
