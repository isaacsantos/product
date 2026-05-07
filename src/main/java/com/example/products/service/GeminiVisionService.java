package com.example.products.service;

import com.example.products.config.GeminiProperties;
import com.example.products.model.AiClassifiedProduct;
import com.example.products.model.TagResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class GeminiVisionService implements AiVisionService {

    private final Client client;
    private final String model;
    private final Gson gson = new Gson();

    public GeminiVisionService(GeminiProperties properties) {
        this.client = Client.builder().apiKey(properties.getApiKey()).build();
        this.model = properties.getModel();
    }

    @Override
    public List<AiClassifiedProduct> classifyImages(List<String> imageUrls, List<TagResponse> availableTags) {
        List<Part> parts = new ArrayList<>();

        for (int i = 0; i < imageUrls.size(); i++) {
            parts.add(Part.fromUri(imageUrls.get(i), "image/jpeg"));
        }

        String tagList = availableTags.stream()
                .map(t -> String.format("{\"id\": %d, \"name\": \"%s\"}", t.getId(), t.getName()))
                .collect(Collectors.joining(", ", "[", "]"));

        parts.add(Part.fromText(buildPrompt(imageUrls.size(), tagList)));
        Content content = Content.fromParts(parts.toArray(new Part[0]));

        Schema imageIndicesSchema = Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.INTEGER))
                .build();

        Schema tagIdsSchema = Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.INTEGER))
                .build();

        Schema productSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.of(
                        "name", Schema.builder().type(Type.Known.STRING).build(),
                        "description", Schema.builder().type(Type.Known.STRING).build(),
                        "imageIndices", imageIndicesSchema,
                        "tagIds", tagIdsSchema
                ))
                .required(ImmutableList.of("name", "description", "imageIndices", "tagIds"))
                .build();

        Schema responseSchema = Schema.builder()
                .type(Type.Known.ARRAY)
                .items(productSchema)
                .build();

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .build();

        GenerateContentResponse response = client.models.generateContent(model, content, config);
        String jsonResponse = response.text();
        log.info("Gemini classification response: {}", jsonResponse);

        return gson.fromJson(jsonResponse, new TypeToken<List<AiClassifiedProduct>>() {}.getType());
    }

    private String buildPrompt(int imageCount, String tagList) {
        return String.format("""
                You are analyzing %d product images. Your task is to:
                
                1. GROUP the images by product. Images that show the same product (possibly from different angles, \
                colors, or arrangements) should be grouped together.
                
                2. For each product group, provide:
                   - "name": A concise, commercial product name in Spanish.
                   - "description": A brief product description in Spanish suitable for an e-commerce store.
                   - "imageIndices": The 0-based indices of the images that belong to this product.
                   - "tagIds": The IDs of the tags that best categorize this product from the available tags list.
                
                Available tags: %s
                
                Important rules:
                - Each image index (0 to %d) must appear in exactly one product group.
                - Only use tag IDs from the provided list.
                - If no tags match a product, return an empty tagIds array.
                - Names and descriptions must be in Spanish.
                """, imageCount, tagList, imageCount - 1);
    }
}
