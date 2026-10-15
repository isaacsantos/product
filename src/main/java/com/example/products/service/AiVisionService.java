package com.example.products.service;

import com.example.products.model.AiClassifiedProduct;
import com.example.products.model.TagResponse;

import java.util.List;

public interface AiVisionService {

    /**
     * Classifies a set of images that all belong to a SINGLE product.
     * The AI identifies one product name, writes a Spanish description, assigns tags,
     * and estimates an MXN market price. All provided images are associated with this one product.
     *
     * @param imageUrls     the (low-res) image URLs for the single product
     * @param availableTags tags the AI may choose from
     * @return the classified product (never multiple)
     */
    AiClassifiedProduct classifyProduct(List<String> imageUrls, List<TagResponse> availableTags);
}
