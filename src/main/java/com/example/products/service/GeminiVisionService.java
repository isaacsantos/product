package com.example.products.service;

import com.example.products.config.GeminiProperties;
import com.example.products.model.AiClassifiedProduct;
import com.example.products.model.TagResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    // Package-private constructor for testing
    GeminiVisionService(Client client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public AiClassifiedProduct classifyProduct(List<String> imageUrls, List<TagResponse> availableTags) {
        HttpClient httpClient = HttpClient.newHttpClient();
        List<Part> parts = new ArrayList<>();

        for (String imageUrl : imageUrls) {
            byte[] imageBytes = downloadImage(httpClient, imageUrl);
            String mimeType = guessMimeType(imageUrl);
            parts.add(Part.fromBytes(imageBytes, mimeType));
        }

        String tagListJson = availableTags.stream()
                .map(t -> String.format("{\"id\": %d, \"name\": \"%s\"}", t.getId(), t.getName()))
                .collect(Collectors.joining(", ", "[", "]"));

        parts.add(Part.fromText(buildPrompt(imageUrls.size(), tagListJson)));
        Content content = Content.fromParts(parts.toArray(new Part[0]));

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(buildSchema())
                .build();

        GenerateContentResponse response = client.models.generateContent(model, content, config);
        String jsonResponse = response.text();
        log.info("Gemini single-product response: {}", jsonResponse);

        AiClassifiedProduct product = gson.fromJson(jsonResponse, AiClassifiedProduct.class);
        product.setPrice(roundToNearest50(product.getPrice()));
        return product;
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

    static Schema buildSchema() {
        Schema tagIdsSchema = Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.INTEGER))
                .build();

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(ImmutableMap.of(
                        "name", Schema.builder().type(Type.Known.STRING).build(),
                        "description", Schema.builder().type(Type.Known.STRING).build(),
                        "tagIds", tagIdsSchema,
                        "price", Schema.builder().type(Type.Known.NUMBER).build()
                ))
                .required(ImmutableList.of("name", "description", "tagIds", "price"))
                .build();
    }

    static String buildPrompt(int imageCount, String tagListJson) {
        return String.format("""
                You are a product catalog assistant for an online videogame/collectibles store in Mexico. \
                You are given %d image(s) that ALL show the SAME single product (possibly from different angles, \
                sides, or with its box/manual/accessories). Treat every image as the same one product — never split \
                them into multiple products.

                Return a single JSON object describing that one product with:

                1. "name": A concise, commercial product name in Spanish. Only name what you can clearly see across \
                the images. Do NOT invent variants or editions you cannot see.

                2. "description": A product description written in Spanish (español de México). It MUST be structured \
                in the following order, as a few short natural paragraphs (not a bulleted list):

                   a) HISTORIA: A brief synopsis of the game's story/premise (2-3 sentences).

                   b) CONTENIDO (lo que incluye el producto): Describe ONLY the physical items you can actually SEE in \
                   the images that are part of this product — for example: cartucho, disco, caja/estuche, instructivo \
                   (manual de instrucciones), folleto de seguridad, inserto, mapa, etc. List only what is visible. \
                   IMPORTANT EXCLUSIONS: do NOT list as included anything that is only shown for reference/context — \
                   specifically, screenshots of gameplay and any console/hardware shown to indicate where the game is \
                   played are NOT part of the product and must NOT be listed as included. If you only see the cartridge, \
                   say it includes only the cartridge.

                   c) CONSOLA: State the specific console/system on which THIS game is played (e.g. "Se juega en \
                   Game Boy Advance").

                   d) CLASIFICACIÓN ESRB: State the game's ESRB rating if known for this title (e.g. "Clasificación \
                   ESRB: E (Everyone)"). If you are not certain of the exact rating, say it is approximate.

                   e) GÉNERO: State the game's genre(s) (e.g. "Género: RPG / Aventura").

                   f) REGIÓN: State the region of this copy if determinable from the images or packaging (NTSC-U, \
                   NTSC-J, PAL, o Región Libre). If it cannot be determined, say the region is not specified.

                3. "tagIds": The IDs of the tags that best categorize this product, chosen ONLY from the available \
                tags list. Prefer selecting the matching Console, ESRB, Genre, and Region tags consistent with the \
                description. If none match, return an empty array.

                4. "price": Estimate the product's market price in MXN (Mexican Pesos) for the Mexican resale market. \
                Base the estimate on what this product typically sells for on Mercado Libre México (the dominant \
                marketplace in Mexico); you may cross-check against eBay prices converted at ~17.5 MXN per USD. \
                This is a best-effort estimate from your knowledge — do not claim to have browsed. \
                Return a whole number in MXN (no decimals, no currency symbol), rounded to the nearest multiple of 50 \
                (e.g. 1434 → 1450, 5555 → 5550, 1725 → 1750). If you cannot determine a price, estimate a reasonable \
                one from the product type and round it.

                Available tags: %s

                Important rules:
                - Name and the ENTIRE description must be in Spanish (español de México).
                - The description must follow the a-f structure above (historia, contenido, consola, ESRB, género, región).
                - For CONTENIDO, only list physical items visible in the images; NEVER count gameplay screenshots or a \
                console shown for context as included items.
                - Keep it factual and concise. No marketing fluff.
                - NEVER hallucinate. Only describe what the images and the product's identity support.
                - Only use tag IDs from the provided list.
                - Price must be a whole number in MXN rounded to the nearest multiple of 50.
                """, imageCount, tagListJson);
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
