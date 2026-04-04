package com.example.products;

import com.example.products.model.DisplayOrderRequest;
import com.example.products.model.ImageRequest;
import com.example.products.model.ImageResponse;
import com.example.products.model.ProductRequest;
import com.example.products.model.ProductResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based integration tests for Product Images feature.
 * Extends AbstractIntegrationTest to use a real PostgreSQL database via Testcontainers.
 * Named *IT so it is excluded from non-Docker test runs.
 */
class ProductImagePropertyIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static PrivateKey testPrivateKey;

    @BeforeAll
    static void loadPrivateKey() throws Exception {
        String pem = new String(
                ProductImagePropertyIT.class.getResourceAsStream("/certs/private.pem").readAllBytes()
        );
        String stripped = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        testPrivateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    // ── JWT helper ───────────────────────────────────────────────────────────

    private String validJwt() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user")
                .issuer("test-issuer")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .issueTime(new Date())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(testPrivateKey));
        return jwt.serialize();
    }

    // ── Product helper ───────────────────────────────────────────────────────

    private ProductResponse createProduct() throws Exception {
        ProductRequest req = ProductRequest.builder()
                .name("Test Product " + System.nanoTime())
                .description("desc")
                .price(new BigDecimal("9.99"))
                .build();
        MvcResult result = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ProductResponse.class);
    }

    // ── Arbitraries ──────────────────────────────────────────────────────────

    @Provide
    Arbitrary<List<String>> validUrlLists() {
        Arbitrary<String> validUrl = Arbitraries.integers().between(1, 9999)
                .map(n -> "https://cdn.example.com/image-" + n + ".jpg");
        return validUrl.list().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<String> invalidUrls() {
        return Arbitraries.of(
                "ftp://example.com/image.jpg",
                "file:///etc/passwd",
                "not-a-url",
                "//missing-scheme.com/img.jpg",
                "",
                "   "
        );
    }

    @Provide
    Arbitrary<Integer> negativeDisplayOrders() {
        return Arbitraries.integers().between(Integer.MIN_VALUE, -1);
    }

    @Provide
    Arbitrary<Integer> validDisplayOrders() {
        return Arbitraries.integers().between(0, 1000);
    }

    @Provide
    Arbitrary<Long> nonExistentProductIds() {
        return Arbitraries.longs().between(Long.MAX_VALUE - 1000L, Long.MAX_VALUE);
    }

    // ── Properties ───────────────────────────────────────────────────────────

    // Feature: product-images, Property 1: Round-trip de creación de imágenes
    // Validates: Requirements 3.1, 4.1
    @Property(tries = 100)
    void addImagesThenGetReturnsAllUrls(@ForAll("validUrlLists") List<String> urls) throws Exception {
        ProductResponse product = createProduct();
        Long productId = product.getId();

        ImageRequest imageRequest = ImageRequest.builder().urls(urls).build();
        mockMvc.perform(post("/api/products/{id}/images", productId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest)))
                .andExpect(status().isCreated());

        MvcResult getResult = mockMvc.perform(get("/api/products/{id}/images", productId))
                .andExpect(status().isOk())
                .andReturn();

        List<ImageResponse> images = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        List<String> returnedUrls = images.stream().map(ImageResponse::getUrl).toList();
        assertThat(returnedUrls).containsExactlyInAnyOrderElementsOf(urls);
    }

    // Feature: product-images, Property 2: Imágenes ordenadas por displayOrder ascendente
    // Validates: Requirements 4.1, 7.1, 7.2
    @Property(tries = 100)
    void getImagesReturnsListOrderedByDisplayOrderAsc(@ForAll("validUrlLists") List<String> urls) throws Exception {
        ProductResponse product = createProduct();
        Long productId = product.getId();

        ImageRequest imageRequest = ImageRequest.builder().urls(urls).build();
        mockMvc.perform(post("/api/products/{id}/images", productId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest)))
                .andExpect(status().isCreated());

        MvcResult getResult = mockMvc.perform(get("/api/products/{id}/images", productId))
                .andExpect(status().isOk())
                .andReturn();

        List<ImageResponse> images = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        for (int i = 1; i < images.size(); i++) {
            assertThat(images.get(i).getDisplayOrder())
                    .isGreaterThanOrEqualTo(images.get(i - 1).getDisplayOrder());
        }
    }

    // Feature: product-images, Property 3: Producto inexistente devuelve 404
    // Validates: Requirements 3.2, 4.3, 5.2, 6.2
    @Property(tries = 100)
    void nonExistentProductReturns404ForAllEndpoints(@ForAll("nonExistentProductIds") Long productId) throws Exception {
        ImageRequest imageRequest = ImageRequest.builder()
                .urls(List.of("https://cdn.example.com/img.jpg"))
                .build();

        mockMvc.perform(post("/api/products/{id}/images", productId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/products/{id}/images", productId))
                .andExpect(status().isNotFound());

        DisplayOrderRequest displayOrderRequest = DisplayOrderRequest.builder().displayOrder(0).build();
        mockMvc.perform(put("/api/products/{id}/images/{imageId}", productId, Long.MAX_VALUE)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(displayOrderRequest)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/products/{id}/images/{imageId}", productId, Long.MAX_VALUE)
                        .header("Authorization", "Bearer " + validJwt()))
                .andExpect(status().isNotFound());
    }

    // Feature: product-images, Property 4: Validación de pertenencia de imagen al producto
    // Validates: Requirements 8.2, 8.3, 5.3, 6.3
    @Property(tries = 100)
    void imageOwnershipValidationReturns404(@ForAll("validDisplayOrders") Integer newDisplayOrder) throws Exception {
        ProductResponse productA = createProduct();
        ProductResponse productB = createProduct();

        ImageRequest imageRequest = ImageRequest.builder()
                .urls(List.of("https://cdn.example.com/img-ownership.jpg"))
                .build();

        MvcResult addResult = mockMvc.perform(post("/api/products/{id}/images", productA.getId())
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        List<ImageResponse> addedImages = objectMapper.readValue(
                addResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        Long imageId = addedImages.get(0).getId();

        DisplayOrderRequest displayOrderRequest = DisplayOrderRequest.builder()
                .displayOrder(newDisplayOrder)
                .build();

        mockMvc.perform(put("/api/products/{id}/images/{imageId}", productB.getId(), imageId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(displayOrderRequest)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/products/{id}/images/{imageId}", productB.getId(), imageId)
                        .header("Authorization", "Bearer " + validJwt()))
                .andExpect(status().isNotFound());
    }

    // Feature: product-images, Property 5: URLs inválidas o vacías son rechazadas con 400
    // Validates: Requirements 9.1, 9.2, 9.3, 3.3, 1.2
    @Property(tries = 100)
    void invalidUrlsAreRejectedWith400(@ForAll("invalidUrls") String invalidUrl) throws Exception {
        ProductResponse product = createProduct();
        Long productId = product.getId();

        MvcResult beforeResult = mockMvc.perform(get("/api/products/{id}/images", productId))
                .andExpect(status().isOk())
                .andReturn();
        List<ImageResponse> imagesBefore = objectMapper.readValue(
                beforeResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        int countBefore = imagesBefore.size();

        ImageRequest imageRequest = ImageRequest.builder().urls(List.of(invalidUrl)).build();
        mockMvc.perform(post("/api/products/{id}/images", productId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest)))
                .andExpect(status().isBadRequest());

        MvcResult afterResult = mockMvc.perform(get("/api/products/{id}/images", productId))
                .andExpect(status().isOk())
                .andReturn();
        List<ImageResponse> imagesAfter = objectMapper.readValue(
                afterResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        assertThat(imagesAfter.size()).isEqualTo(countBefore);
    }

    // Feature: product-images, Property 6: displayOrder inválido es rechazado con 400
    // Validates: Requirements 5.4, 1.3
    @Property(tries = 100)
    void negativeDisplayOrderIsRejectedWith400(@ForAll("negativeDisplayOrders") Integer negativeOrder) throws Exception {
        ProductResponse product = createProduct();
        Long productId = product.getId();

        ImageRequest imageRequest = ImageRequest.builder()
                .urls(List.of("https://cdn.example.com/img-order.jpg"))
                .build();
        MvcResult addResult = mockMvc.perform(post("/api/products/{id}/images", productId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        List<ImageResponse> addedImages = objectMapper.readValue(
                addResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        Long imageId = addedImages.get(0).getId();
        Integer originalDisplayOrder = addedImages.get(0).getDisplayOrder();

        DisplayOrderRequest badRequest = DisplayOrderRequest.builder().displayOrder(negativeOrder).build();
        mockMvc.perform(put("/api/products/{id}/images/{imageId}", productId, imageId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());

        MvcResult getResult = mockMvc.perform(get("/api/products/{id}/images", productId))
                .andExpect(status().isOk())
                .andReturn();
        List<ImageResponse> images = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        ImageResponse unchanged = images.stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow();
        assertThat(unchanged.getDisplayOrder()).isEqualTo(originalDisplayOrder);
    }

    // Feature: product-images, Property 7: Round-trip de actualización de displayOrder
    // Validates: Requirements 5.1
    @Property(tries = 100)
    void updateDisplayOrderThenGetReturnsUpdatedValue(@ForAll("validDisplayOrders") Integer newDisplayOrder) throws Exception {
        ProductResponse product = createProduct();
        Long productId = product.getId();

        ImageRequest imageRequest = ImageRequest.builder()
                .urls(List.of("https://cdn.example.com/img-update.jpg"))
                .build();
        MvcResult addResult = mockMvc.perform(post("/api/products/{id}/images", productId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        List<ImageResponse> addedImages = objectMapper.readValue(
                addResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        Long imageId = addedImages.get(0).getId();

        DisplayOrderRequest updateRequest = DisplayOrderRequest.builder().displayOrder(newDisplayOrder).build();
        mockMvc.perform(put("/api/products/{id}/images/{imageId}", productId, imageId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        MvcResult getResult = mockMvc.perform(get("/api/products/{id}/images", productId))
                .andExpect(status().isOk())
                .andReturn();
        List<ImageResponse> images = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        ImageResponse updated = images.stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow();
        assertThat(updated.getDisplayOrder()).isEqualTo(newDisplayOrder);
    }

    // Feature: product-images, Property 8: Eliminación de imagen
    // Validates: Requirements 6.1
    @Property(tries = 100)
    void deleteImageThenGetDoesNotContainDeletedImage(@ForAll("validUrlLists") List<String> urls) throws Exception {
        ProductResponse product = createProduct();
        Long productId = product.getId();

        ImageRequest imageRequest = ImageRequest.builder().urls(urls).build();
        MvcResult addResult = mockMvc.perform(post("/api/products/{id}/images", productId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        List<ImageResponse> addedImages = objectMapper.readValue(
                addResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        Long imageIdToDelete = addedImages.get(0).getId();

        mockMvc.perform(delete("/api/products/{id}/images/{imageId}", productId, imageIdToDelete)
                        .header("Authorization", "Bearer " + validJwt()))
                .andExpect(status().isNoContent());

        MvcResult getResult = mockMvc.perform(get("/api/products/{id}/images", productId))
                .andExpect(status().isOk())
                .andReturn();
        List<ImageResponse> remaining = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        assertThat(remaining).noneMatch(img -> img.getId().equals(imageIdToDelete));
    }

    // Feature: product-images, Property 9: Eliminación en cascada al borrar producto
    // Validates: Requirements 8.1
    @Property(tries = 100)
    void deleteProductCascadesImagesToZeroRows(@ForAll("validUrlLists") List<String> urls) throws Exception {
        ProductResponse product = createProduct();
        Long productId = product.getId();

        ImageRequest imageRequest = ImageRequest.builder().urls(urls).build();
        mockMvc.perform(post("/api/products/{id}/images", productId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + validJwt()))
                .andExpect(status().isNoContent());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_images WHERE product_id = ?",
                Integer.class,
                productId
        );
        assertThat(count).isZero();
    }

    // Feature: product-images, Property 10: ProductResponse incluye imágenes embebidas
    // Validates: Requirements 7.1, 7.2, 7.3, 7.4
    @Property(tries = 100)
    void productResponseIncludesEmbeddedImagesOrderedByDisplayOrder(
            @ForAll("validUrlLists") List<String> urls) throws Exception {
        ProductResponse product = createProduct();
        Long productId = product.getId();

        ImageRequest imageRequest = ImageRequest.builder().urls(urls).build();
        mockMvc.perform(post("/api/products/{id}/images", productId)
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest)))
                .andExpect(status().isCreated());

        MvcResult listResult = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();
        List<ProductResponse> products = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        ProductResponse found = products.stream()
                .filter(p -> p.getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Product not found in list response"));

        assertThat(found.getImages()).isNotNull();
        assertThat(found.getImages()).hasSize(urls.size());

        List<Integer> orders = found.getImages().stream()
                .map(ImageResponse::getDisplayOrder)
                .toList();
        for (int i = 1; i < orders.size(); i++) {
            assertThat(orders.get(i)).isGreaterThanOrEqualTo(orders.get(i - 1));
        }

        MvcResult singleResult = mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andReturn();
        ProductResponse single = objectMapper.readValue(
                singleResult.getResponse().getContentAsString(),
                ProductResponse.class
        );
        assertThat(single.getImages()).isNotNull();
        assertThat(single.getImages()).hasSize(urls.size());
    }
}
