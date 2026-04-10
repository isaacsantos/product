package com.example.products.service;

import com.example.products.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiProductCreationServiceImpl implements AiProductCreationService {

    private final CloudinaryService cloudinaryService;
    private final GeminiService geminiService;
    private final ProductService productService;
    private final ProductImageService productImageService;
    private final TagService tagService;

    @Override
    @Async
    @Transactional
    public void createProductsFromImages(List<MultipartFile> files) {
        try {
            log.info("Starting async AI product creation for {} images", files.size());

            // 1. Upload all images to Cloudinary
            List<CloudinaryUploadResult> uploadResults = files.stream()
                    .map(cloudinaryService::upload)
                    .toList();

            List<String> imageUrls = uploadResults.stream()
                    .map(CloudinaryUploadResult::url)
                    .toList();

            // 2. Get available tags
            List<TagResponse> availableTags = tagService.findAll();

            // 3. Ask Gemini to classify images
            log.info("Classifying {} images with Gemini AI", imageUrls.size());
            List<AiClassifiedProduct> classifiedProducts = geminiService.classifyImages(imageUrls, availableTags);

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
}
