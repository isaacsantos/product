package com.example.products.service;

import com.example.products.config.OpenAiProperties;
import com.example.products.model.AiClassifiedProduct;
import com.example.products.model.TagResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class OpenAiVisionService implements AiVisionService {

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    private final RestClient restClient;
    private final String model;
    private final Gson gson = new Gson();

    public OpenAiVisionService(OpenAiProperties properties) {
        this.model = properties.getModel();
        this.restClient = RestClient.builder()
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    @Override
    public List<AiClassifiedProduct> classifyImages(List<String> imageUrls, List<TagResponse> availableTags) {
        String tagList = availableTags.stream()
                .map(t -> String.format("{\"id\": %d, \"name\": \"%s\"}", t.getId(), t.getName()))
                .collect(Collectors.joining(", ", "[", "]"));

        JsonObject requestBody = buildRequest(imageUrls, tagList);

        log.info("Sending {} images to OpenAI for classification", imageUrls.size());

        String responseBody = restClient.post()
                .uri(OPENAI_API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody.toString())
                .retrieve()
                .body(String.class);

        // Parse the response to extract the JSON content
        JsonObject response = gson.fromJson(responseBody, JsonObject.class);
        String content = response.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

        log.info("OpenAI classification response: {}", content);

        // The structured output wraps in {"products": [...]}
        JsonObject parsed = gson.fromJson(content, JsonObject.class);
        return gson.fromJson(parsed.getAsJsonArray("products"),
                new TypeToken<List<AiClassifiedProduct>>() {}.getType());
    }

    private JsonObject buildRequest(List<String> imageUrls, String tagList) {
        String systemPrompt = """
                You are a product classification AI. You analyze product images and return structured JSON.
                Always respond with valid JSON only, no markdown, no explanation.""";

        String userPrompt = buildPrompt(imageUrls.size(), tagList);

        // Build content array with text + image URLs
        JsonArray contentArray = new JsonArray();

        // Text part
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", userPrompt);
        contentArray.add(textPart);

        // Image parts
        for (String url : imageUrls) {
            JsonObject imagePart = new JsonObject();
            imagePart.addProperty("type", "image_url");
            JsonObject imageUrl = new JsonObject();
            imageUrl.addProperty("url", url);
            imageUrl.addProperty("detail", "low");
            imagePart.add("image_url", imageUrl);
            contentArray.add(imagePart);
        }

        // Build messages
        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.add("content", contentArray);
        messages.add(userMessage);

        // Build response format (structured output)
        JsonObject responseFormat = buildResponseFormat();

        // Build request body
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.add("messages", messages);
        request.add("response_format", responseFormat);

        return request;
    }

    private JsonObject buildResponseFormat() {
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_schema");

        JsonObject jsonSchema = new JsonObject();
        jsonSchema.addProperty("name", "product_classification");
        jsonSchema.addProperty("strict", true);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        // products array
        JsonObject productsArray = new JsonObject();
        productsArray.addProperty("type", "array");

        JsonObject itemSchema = new JsonObject();
        itemSchema.addProperty("type", "object");

        JsonObject itemProperties = new JsonObject();
        itemProperties.add("name", typeString());
        itemProperties.add("description", typeString());
        itemProperties.add("imageIndices", typeArrayOfIntegers());
        itemProperties.add("tagIds", typeArrayOfIntegers());

        itemSchema.add("properties", itemProperties);
        JsonArray required = new JsonArray();
        required.add("name");
        required.add("description");
        required.add("imageIndices");
        required.add("tagIds");
        itemSchema.add("required", required);
        itemSchema.addProperty("additionalProperties", false);

        productsArray.add("items", itemSchema);
        properties.add("products", productsArray);

        schema.add("properties", properties);
        JsonArray topRequired = new JsonArray();
        topRequired.add("products");
        schema.add("required", topRequired);
        schema.addProperty("additionalProperties", false);

        jsonSchema.add("schema", schema);
        responseFormat.add("json_schema", jsonSchema);

        return responseFormat;
    }

    private JsonObject typeString() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "string");
        return obj;
    }

    private JsonObject typeArrayOfIntegers() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "array");
        JsonObject items = new JsonObject();
        items.addProperty("type", "integer");
        obj.add("items", items);
        return obj;
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
