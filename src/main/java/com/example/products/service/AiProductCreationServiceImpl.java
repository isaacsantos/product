package com.example.products.service;

import com.example.products.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiProductCreationServiceImpl implements AiProductCreationService {

    private final CloudinaryService cloudinaryService;
    private final AiVisionService aiVisionService;
    private final ProductService productService;
    private final ProductImageService productImageService;
    private final TagService tagService;

    @Override
    @Async
    @Transactional
    public void createProductsFromImages(List<InMemoryFile> files) {
        try {
            log.info("Starting async AI product creation for {} images", files.size());

            // 1. Upload all images to Cloudinary
            List<CloudinaryUploadResult> uploadResults = files.stream()
                    .map(f -> cloudinaryService.upload(f.getBytes(), f.getContentType()))
                    .toList();

            List<String> imageUrls = uploadResults.stream()
                    .map(CloudinaryUploadResult::url)
                    .toList();

            // Generate low-res URLs for AI analysis (saves tokens/quota)
            List<String> lowResUrls = imageUrls.stream()
                    .map(this::toLowResUrl)
                    .toList();

            // 2. Get available tags
            List<TagResponse> availableTags = tagService.findAll();

            // 3. Ask AI to classify images (using low-res versions)
            log.info("Classifying {} images with AI vision service", imageUrls.size());
            List<AiClassifiedProduct> classifiedProducts = aiVisionService.classifyImages(lowResUrls, availableTags);

            // 4. Create products as INACTIVE with price = null
            log.info("Creating {} products from AI classification", classifiedProducts.size());

            for (AiClassifiedProduct classified : classifiedProducts) {
                ProductRequest productRequest = ProductRequest.builder()
                        .name(classified.getName())
                        .description(classified.getDescription())
                        .price(null)
                        .active(false)
                        .build();

                AdminProductResponse product = productService.createAdmin(productRequest);

                List<String> productImageUrls = classified.getImageIndices().stream()
                        .map(imageUrls::get)
                        .toList();

                productImageService.addImages(product.getId(), new ImageRequest(productImageUrls));

                if (classified.getTagIds() != null && !classified.getTagIds().isEmpty()) {
                    productService.setTagsAdmin(product.getId(), new HashSet<>(classified.getTagIds()));
                }
            }

            log.info("AI product creation completed: {} products created", classifiedProducts.size());
        } catch (Exception e) {
            log.error("AI product creation failed", e);
        }
    }

    /**
     * Transforms a Cloudinary URL to a low-resolution version for AI analysis.
     * Inserts w_600,q_auto transformation to reduce image size significantly.
     * Original: https://res.cloudinary.com/cloud/image/upload/v123/file.jpg
     * Result:   https://res.cloudinary.com/cloud/image/upload/w_600,q_auto/v123/file.jpg
     */
    private String toLowResUrl(String originalUrl) {
        String marker = "/upload/";
        int idx = originalUrl.indexOf(marker);
        if (idx == -1) {
            return originalUrl; // Not a standard Cloudinary URL, return as-is
        }
        int insertPos = idx + marker.length();
        return originalUrl.substring(0, insertPos) + "w_600,q_auto/" + originalUrl.substring(insertPos);
    }
}
