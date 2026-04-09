package com.example.products.service;

import com.example.products.exception.ProductImageNotFoundException;
import com.example.products.exception.ProductNotFoundException;
import com.example.products.model.*;
import com.example.products.repository.ProductImageRepository;
import com.example.products.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final CloudinaryService cloudinaryService;

    public ProductImageServiceImpl(ProductImageRepository imageRepository,
                                   ProductRepository productRepository,
                                   CloudinaryService cloudinaryService) {
        this.imageRepository = imageRepository;
        this.productRepository = productRepository;
        this.cloudinaryService = cloudinaryService;
    }

    @Override
    public List<ImageResponse> addImages(Long productId, ImageRequest request) {
        verifyProductExists(productId);
        List<ProductImage> existing = imageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        int nextOrder = existing.size();
        List<ProductImage> images = new ArrayList<>();
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
    public List<ImageResponse> uploadImages(Long productId, List<MultipartFile> files) {
        verifyProductExists(productId);
        List<ProductImage> existing = imageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        int nextOrder = existing.size();
        List<ProductImage> images = new ArrayList<>();
        for (MultipartFile file : files) {
            CloudinaryUploadResult result = cloudinaryService.upload(file);
            images.add(ProductImage.builder()
                    .productId(productId)
                    .url(result.url())
                    .cloudinaryPublicId(result.publicId())
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
        String cloudinaryPublicId = image.getCloudinaryPublicId();
        imageRepository.deleteById(imageId);
        if (cloudinaryPublicId != null) {
            try {
                cloudinaryService.delete(cloudinaryPublicId);
            } catch (Exception e) {
                log.error("Failed to delete Cloudinary asset with publicId: {}", cloudinaryPublicId, e);
            }
        }
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
