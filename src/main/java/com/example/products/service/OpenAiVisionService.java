package com.example.products.service;

import com.example.products.config.OpenAiProperties;
import com.example.products.model.AiClassifiedProduct;
import com.example.products.model.TagResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
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
    public AiClassifiedProduct classifyProduct(List<String> imageUrls, List<TagResponse> availableTags) {
        String tagList = availableTags.stream()
                .map(t -> String.format("{\"id\": %d, \"name\": \"%s\"}", t.getId(), t.getName()))
                .collect(Collectors.joining(", ", "[", "]"));

        JsonObject requestBody = buildRequest(imageUrls, tagList);

        log.info("Sending {} images to OpenAI for single-product classification", imageUrls.size());

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

        AiClassifiedProduct product = gson.fromJson(content, AiClassifiedProduct.class);
        product.setPrice(roundToNearest50(product.getPrice()));
        return product;
    }

    /** Rounds a price to the nearest multiple of 50. null-safe. */
    static BigDecimal roundToNearest50(BigDecimal price) {
        if (price == null) {
            return null;
        }
        long rounded = Math.round(price.doubleValue() / 50.0) * 50;
        return BigDecimal.valueOf(rounded);
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

        // Single product object: { name, description, tagIds, price }
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        properties.add("name", typeString());
        properties.add("description", typeString());
        properties.add("tagIds", typeArrayOfIntegers());
        properties.add("price", typeNumber());
        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("name");
        required.add("description");
        required.add("tagIds");
        required.add("price");
        schema.add("required", required);
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

    private JsonObject typeNumber() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "number");
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
                You are a product catalog assistant for an online videogame/collectibles store in Mexico. \
                You are given %d image(s) that ALL show the SAME single product (possibly from different angles, \
                sides, or with its box/manual/accessories). Treat every image as the same one product — never split \
                them into multiple products.

                Return a single JSON object describing that one product with:
                   - "name": A concise, commercial product name in Spanish. Only name what you can clearly see.
                   - "description": A product description in Spanish (español de México) using a LIGHT text format \
                (NOT HTML) with: section headings on their own line starting with "## ", bullet lines starting with \
                "- ", metadata lines "**Etiqueta:** valor", and a blank line between sections. It MUST contain exactly \
                these three sections in this order:
                       ## Historia
                       brief synopsis of the game's story/premise (2-3 sentences, plain paragraph).
                       ## Contenido
                       one "- " bullet per physical item VISIBLE in the images and part of the product (e.g. \
                "- Cartucho", "- Caja original", "- Instructivo", "- Folleto de seguridad"). Do NOT count gameplay \
                screenshots or a console/hardware shown only for reference. If you only see the cartridge, output just \
                "- Cartucho".
                       ## Detalles
                       exactly these four lines, each on its own line:
                       **Consola:** the console/system where THIS game is played
                       **Clasificación ESRB:** the rating if known ("(aproximada)" if uncertain)
                       **Género:** the genre(s)
                       **Región:** NTSC-U, NTSC-J, PAL o Región Libre (or "No especificada")
                   - "tagIds": The IDs of the tags that best categorize this product, chosen ONLY from the available \
                tags list. Prefer the matching Console, ESRB, Genre, and Region tags consistent with the description.
                   - "price": The estimated market price in MXN (Mexican Pesos) for the Mexican resale market. Base it on \
                typical Mercado Libre México selling prices (you may cross-check eBay at ~17.5 MXN/USD). This is a \
                best-effort estimate from your knowledge — do not claim to have browsed. Return a whole number in MXN \
                (no decimals, no currency symbol), rounded to the nearest multiple of 50.

                Available tags: %s

                Important rules:
                - Name and the ENTIRE description must be in Spanish (español de México).
                - The description must use the exact light-text format with headings "## Historia", "## Contenido", \
                "## Detalles". Do NOT output HTML tags of any kind.
                - "## Contenido" must be "- " bullet lines; only physical items visible in the images; NEVER count \
                gameplay screenshots or a console shown for context.
                - "## Detalles" must contain exactly the four "**Etiqueta:** valor" lines.
                - Only use tag IDs from the provided list. If none match, return an empty array.
                - NEVER hallucinate or invent information. Only describe what you can see / what the product is.
                - Keep it factual and concise. No marketing fluff.
                - price must be a whole number in MXN rounded to the nearest multiple of 50.
                """, imageCount, tagList);
    }
}
