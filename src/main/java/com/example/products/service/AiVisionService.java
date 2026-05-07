package com.example.products.service;

import com.example.products.model.AiClassifiedProduct;
import com.example.products.model.TagResponse;

import java.util.List;

public interface AiVisionService {
    List<AiClassifiedProduct> classifyImages(List<String> imageUrls, List<TagResponse> availableTags);
}
