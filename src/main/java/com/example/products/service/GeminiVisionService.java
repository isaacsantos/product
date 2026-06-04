package com.example.products.service;

import com.example.products.config.GeminiProperties;
import com.example.products.model.AiClassifiedProduct;
import com.example.products.model.Phase1Result;
import com.example.products.model.Phase2Result;
import com.example.products.model.TagResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class GeminiVisionService implements AiVisionService {

    private final Client client;
    private final String model;
    private final Gson gson = new Gson();

    public GeminiVisionService(GeminiProperties properties) {
        this.client = Client.builder().apiKey(properties.getApiKey()).build();
        this.model = properties.getModel();
    }

    // Package-private constructor for testing
    GeminiVisionService(Client client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public List<AiClassifiedProduct> classifyImages(List<String> imageUrls, List<TagResponse> availableTags) {
        // Phase 1: Vision-based product identification (propagates immediately on failure)
        List<Phase1Result> phase1Results = executePhase1(imageUrls);

        // Validate image index coverage
        validatePhase1Coverage(phase1Results, imageUrls.size());

        // Phase 2: Text-based description and tag assignment
        List<String> productNames = phase1Results.stream()
                .map(Phase1Result::getName)
                .collect(Collectors.toList());

        List<Phase2Result> phase2Results;
        try {
            phase2Results = executePhase2(productNames, availableTags);
        } catch (Exception e) {
            log.warn("Phase 2 failed. Phase 1 results were: {}", phase1Results, e);
            throw e;
        }

        // Merge results
        return mergeResults(phase1Results, phase2Results);
    }

    List<Phase1Result> executePhase1(List<String> imageUrls) {
        HttpClient httpClient = HttpClient.newHttpClient();
        List<Part> parts = new ArrayList<>();

        for (int i = 0; i < imageUrls.size(); i++) {
            byte[] imageBytes = downloadImage(httpClient, imageUrls.get(i));
            String mimeType = guessMimeType(imageUrls.get(i));
            parts.add(Part.fromBytes(imageBytes, mimeType));
        }

        parts.add(Part.fromText(buildPhase1Prompt(imageUrls.size())));
        Content content = Content.fromParts(parts.toArray(new Part[0]));

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(buildPhase1Schema())
                .build();

        GenerateContentResponse response = client.models.generateContent(model, content, config);
        String jsonResponse = response.text();
        log.info("Gemini Phase 1 response: {}", jsonResponse);

        return gson.fromJson(jsonResponse, new TypeToken<List<Phase1Result>>() {}.getType());
    }

    private byte[] downloadImage(HttpClient httpClient, String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to download image from " + imageUrl + ". Status: " + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error downloading image from " + imageUrl, e);
        }
    }

    private String guessMimeType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".webp")) return "image/webp";
        if (lower.contains(".gif")) return "image/gif";
        return "image/jpeg";
    }

    static void validatePhase1Coverage(List<Phase1Result> results, int imageCount) {
        List<Integer> allIndices = results.stream()
                .flatMap(r -> r.getImageIndices().stream())
                .collect(Collectors.toList());

        Set<Integer> expectedIndices = IntStream.range(0, imageCount)
                .boxed()
                .collect(Collectors.toSet());

        // Check for out-of-range indices
        List<Integer> outOfRange = allIndices.stream()
                .filter(i -> i < 0 || i >= imageCount)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // Check for missing indices
        Set<Integer> presentIndices = new HashSet<>(allIndices);
        List<Integer> missing = expectedIndices.stream()
                .filter(i -> !presentIndices.contains(i))
                .sorted()
                .collect(Collectors.toList());

        // Check for duplicate indices
        Set<Integer> seen = new HashSet<>();
        List<Integer> duplicates = allIndices.stream()
                .filter(i -> !seen.add(i))
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (!outOfRange.isEmpty() || !missing.isEmpty() || !duplicates.isEmpty()) {
            StringBuilder message = new StringBuilder("Phase 1 image index coverage validation failed:");
            if (!missing.isEmpty()) {
                message.append(" Missing indices: ").append(missing).append(".");
            }
            if (!duplicates.isEmpty()) {
                message.append(" Duplicate indices: ").append(duplicates).append(".");
            }
            if (!outOfRange.isEmpty()) {
                message.append(" Out-of-range indices: ").append(outOfRange).append(".");
            }
            throw new IllegalStateException(message.toString());
        }
    }

    static List<AiClassifiedProduct> mergeResults(List<Phase1Result> phase1, List<Phase2Result> phase2) {
        // Build a set of Phase 1 product names for quick lookup
        Set<String> phase1Names = phase1.stream()
                .map(Phase1Result::getName)
                .collect(Collectors.toSet());

        // Build a map of Phase 2 results keyed by name
        Map<String, Phase2Result> phase2Map = new HashMap<>();
        for (Phase2Result p2 : phase2) {
            if (!phase1Names.contains(p2.getName())) {
                log.warn("Phase 2 returned product '{}' with no matching Phase 1 entry. Ignoring.", p2.getName());
            } else {
                phase2Map.put(p2.getName(), p2);
            }
        }

        // Iterate over Phase 1 results and merge
        List<AiClassifiedProduct> merged = new ArrayList<>();
        for (Phase1Result p1 : phase1) {
            Phase2Result p2 = phase2Map.get(p1.getName());
            if (p2 == null) {
                throw new IllegalStateException(
                        "Phase 1 product '" + p1.getName() + "' has no matching Phase 2 result.");
            }
            merged.add(AiClassifiedProduct.builder()
                    .name(p1.getName())
                    .description(p2.getDescription())
                    .imageIndices(p1.getImageIndices())
                    .tagIds(p2.getTagIds())
                    .price(roundToNearest50(p2.getPrice()))
                    .build());
        }

        return merged;
    }

    List<Phase2Result> executePhase2(List<String> productNames, List<TagResponse> tags) {
        String tagListJson = tags.stream()
                .map(t -> String.format("{\"id\": %d, \"name\": \"%s\"}", t.getId(), t.getName()))
                .collect(Collectors.joining(", ", "[", "]"));

        String prompt = buildPhase2Prompt(productNames, tagListJson);
        Content content = Content.fromParts(Part.fromText(prompt));

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(buildPhase2Schema())
                .build();

        GenerateContentResponse response = client.models.generateContent(model, content, config);
        String jsonResponse = response.text();
        log.info("Gemini Phase 2 response: {}", jsonResponse);

        return gson.fromJson(jsonResponse, new TypeToken<List<Phase2Result>>() {}.getType());
    }

    static String buildPhase2Prompt(List<String> productNames, String tagListJson) {
        String productList = productNames.stream()
                .map(name -> "- " + name)
                .collect(Collectors.joining("\n"));

        return String.format("""
                You are a product catalog assistant. You will be given a list of product names and a list of available tags. \
                Your task is to:
                
                1. For each product name, we will get the description by finding the synopsis in Spanish of the product \
                (3-5 sentences max). ONLY describe what the product name implies. Do NOT invent features, accessories, or \
                capabilities that are not evident from the name.
                
                2. For each product, assign the most relevant tag IDs from the available tags list.
                
                3. For each product, estimate its market price in MXN (Mexican Pesos). To do this:
                   - Search for the product on online stores like eBay, Amazon, or similar marketplaces.
                   - Find a reasonable average selling price in USD.
                   - Convert the price to MXN using an approximate exchange rate of 17.5 MXN per 1 USD.
                   - Round the final price to the nearest multiple of 50 (e.g. 1434 → 1400, 5555 → 5600, 1725 → 1750).
                   - Return the final rounded price in MXN as a whole number (no decimals, no currency symbol).
                   - If you cannot determine a price, estimate a reasonable price based on the product type and round it.
                
                Product names:
                %s
                
                Available tags: %s
                
                Important rules:
                - Descriptions must be in Spanish.
                - Descriptions normally are the synopsis of the product videogame.
                - Descriptions must generated based on the synopsis of the product videogame..
                - Keep descriptions brief and factual. No marketing fluff.
                - Only use tag IDs from the provided list.
                - If no tags match a product, return an empty tagIds array.
                - Return one result per product name, preserving the exact product name as given.
                - The price must be in MXN (Mexican Pesos). Use approximate eBay/Amazon prices converted at ~17.5 MXN/USD.
                - Price must be a whole number rounded to the nearest multiple of 50 (no decimals).
                """, productList, tagListJson);
    }

    static Schema buildPhase1Schema() {
        Schema imageIndicesSchema = Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.INTEGER))
                .build();

        Schema productSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.of(
                        "name", Schema.builder().type(Type.Known.STRING).build(),
                        "imageIndices", imageIndicesSchema
                ))
                .required(ImmutableList.of("name", "imageIndices"))
                .build();

        return Schema.builder()
                .type(Type.Known.ARRAY)
                .items(productSchema)
                .build();
    }

    static Schema buildPhase2Schema() {
        Schema tagIdsSchema = Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.INTEGER))
                .build();

        Schema productSchema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.of(
                        "name", Schema.builder().type(Type.Known.STRING).build(),
                        "description", Schema.builder().type(Type.Known.STRING).build(),
                        "tagIds", tagIdsSchema,
                        "price", Schema.builder().type(Type.Known.NUMBER).build()
                ))
                .required(ImmutableList.of("name", "description", "tagIds", "price"))
                .build();

        return Schema.builder()
                .type(Type.Known.ARRAY)
                .items(productSchema)
                .build();
    }

    static String buildPhase1Prompt(int imageCount) {
        return String.format("""
                You are analyzing %d product images. Your task is to:
                
                1. GROUP the images by product. Images that show the same product (possibly from different angles, \
                colors, or arrangements) should be grouped together.
                
                2. For each product group, provide:
                   - "name": A concise, commercial product name in Spanish. Only name what you can clearly see in the image.
                   - "imageIndices": The 0-based indices of the images that belong to this product.
                
                Important rules:
                - Each image index (0 to %d) must appear in exactly one product group.
                - Product names must be in Spanish.
                - NEVER hallucinate or invent information. Only name what you can clearly see.
                """, imageCount, imageCount - 1);
    }

    /**
     * Rounds a price to the nearest multiple of 50.
     * Examples: 1434.56 → 1450, 5555.55 → 5550, 1725 → 1750, null → null
     */
    static BigDecimal roundToNearest50(BigDecimal price) {
        if (price == null) {
            return null;
        }
        long rounded = Math.round(price.doubleValue() / 50.0) * 50;
        return BigDecimal.valueOf(rounded);
    }

}
