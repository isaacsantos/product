package com.example.products;

import com.example.products.model.ProductRequest;
import com.example.products.model.ProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based integration tests for Product CRUD operations.
 *
 * Property 1: Create then retrieve round-trip
 * Validates: Requirements 1.1, 1.2, 3.1
 */
class ProductCrudPropertyIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    static Stream<ProductRequest> validProductRequests() {
        return Stream.of(
            ProductRequest.builder()
                .name("Wireless Mouse")
                .description("Ergonomic wireless mouse")
                .price(new BigDecimal("29.99"))
                .build(),
            ProductRequest.builder()
                .name("Mechanical Keyboard")
                .description(null)
                .price(new BigDecimal("89.99"))
                .build(),
            ProductRequest.builder()
                .name("USB-C Hub")
                .description("7-in-1 USB-C hub with HDMI and PD charging")
                .price(new BigDecimal("0.01"))
                .build(),
            ProductRequest.builder()
                .name("Monitor Stand")
                .description("")
                .price(new BigDecimal("149.00"))
                .build()
        );
    }

    static Stream<ProductRequest> updatePayloads() {
        return Stream.of(
            ProductRequest.builder()
                .name("Updated Wireless Mouse")
                .description("Updated ergonomic wireless mouse")
                .price(new BigDecimal("39.99"))
                .build(),
            ProductRequest.builder()
                .name("Mechanical Keyboard Pro")
                .description(null)
                .price(new BigDecimal("129.99"))
                .build(),
            ProductRequest.builder()
                .name("USB-C Hub Slim")
                .description("")
                .price(new BigDecimal("0.01"))
                .build(),
            ProductRequest.builder()
                .name("Adjustable Monitor Stand")
                .description("Heavy-duty adjustable stand for monitors up to 32 inches")
                .price(new BigDecimal("199.99"))
                .build()
        );
    }

    /**
     * Property 2: Update then retrieve round-trip
     *
     * For any existing product and any valid ProductRequest, updating via
     * PUT /api/products/{id} and then retrieving via GET /api/products/{id}
     * should return a ProductResponse whose fields exactly match the update request values.
     *
     * Validates: Requirements 4.1
     */
    @ParameterizedTest
    @MethodSource("updatePayloads")
    void updateThenRetrieveRoundTrip(ProductRequest updateRequest) throws Exception {
        // First create a product to update
        ProductRequest initial = ProductRequest.builder()
            .name("Initial Product")
            .description("Initial description")
            .price(new BigDecimal("9.99"))
            .build();

        MvcResult createResult = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initial)))
            .andExpect(status().isCreated())
            .andReturn();

        ProductResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(),
            ProductResponse.class
        );

        Long id = created.getId();

        // PUT /api/products/{id} with the update payload
        MvcResult updateResult = mockMvc.perform(put("/api/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andReturn();

        ProductResponse updated = objectMapper.readValue(
            updateResult.getResponse().getContentAsString(),
            ProductResponse.class
        );

        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getName()).isEqualTo(updateRequest.getName());
        assertThat(updated.getDescription()).isEqualTo(updateRequest.getDescription());
        assertThat(updated.getPrice()).isEqualByComparingTo(updateRequest.getPrice());

        // GET /api/products/{id} and assert fields match update values
        MvcResult getResult = mockMvc.perform(get("/api/products/{id}", id))
            .andExpect(status().isOk())
            .andReturn();

        ProductResponse retrieved = objectMapper.readValue(
            getResult.getResponse().getContentAsString(),
            ProductResponse.class
        );

        assertThat(retrieved.getId()).isEqualTo(id);
        assertThat(retrieved.getName()).isEqualTo(updateRequest.getName());
        assertThat(retrieved.getDescription()).isEqualTo(updateRequest.getDescription());
        assertThat(retrieved.getPrice()).isEqualByComparingTo(updateRequest.getPrice());
    }

    /**
     * Property 1: Create then retrieve round-trip
     *
     * For any valid ProductRequest, POSTing to /api/products and then GETting
     * by the returned id should return a ProductResponse with all fields equal
     * to those in the original request.
     *
     * Validates: Requirements 1.1, 1.2, 3.1
     */
    @ParameterizedTest
    @MethodSource("validProductRequests")
    void createThenRetrieveRoundTrip(ProductRequest request) throws Exception {
        // POST /api/products
        MvcResult createResult = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        ProductResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(),
            ProductResponse.class
        );

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo(request.getName());
        assertThat(created.getDescription()).isEqualTo(request.getDescription());
        assertThat(created.getPrice()).isEqualByComparingTo(request.getPrice());

        // GET /api/products/{id}
        MvcResult getResult = mockMvc.perform(get("/api/products/{id}", created.getId()))
            .andExpect(status().isOk())
            .andReturn();

        ProductResponse retrieved = objectMapper.readValue(
            getResult.getResponse().getContentAsString(),
            ProductResponse.class
        );

        assertThat(retrieved.getId()).isEqualTo(created.getId());
        assertThat(retrieved.getName()).isEqualTo(request.getName());
        assertThat(retrieved.getDescription()).isEqualTo(request.getDescription());
        assertThat(retrieved.getPrice()).isEqualByComparingTo(request.getPrice());
    }

    /**
     * Property 3: Delete then retrieve returns 404
     *
     * For any valid ProductRequest, POSTing to /api/products to create a product,
     * then DELETEing via DELETE /api/products/{id} should return 204, and a
     * subsequent GET /api/products/{id} should return 404.
     *
     * Validates: Requirements 5.1, 3.2
     */
    @ParameterizedTest
    @MethodSource("validProductRequests")
    void deleteThenRetrieveReturns404(ProductRequest request) throws Exception {
        // POST /api/products to create
        MvcResult createResult = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        ProductResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(),
            ProductResponse.class
        );

        Long id = created.getId();
        assertThat(id).isNotNull();

        // DELETE /api/products/{id} — expect 204
        mockMvc.perform(delete("/api/products/{id}", id))
            .andExpect(status().isNoContent());

        // GET /api/products/{id} — expect 404
        mockMvc.perform(get("/api/products/{id}", id))
            .andExpect(status().isNotFound());
    }

    static Stream<ProductRequest> invalidProductRequests() {
        return Stream.of(
            // blank name (empty string)
            ProductRequest.builder().name("").price(new BigDecimal("9.99")).build(),
            // blank name (whitespace only)
            ProductRequest.builder().name("  ").price(new BigDecimal("9.99")).build(),
            // null price
            ProductRequest.builder().name("Valid Name").price(null).build(),
            // zero price
            ProductRequest.builder().name("Valid Name").price(new BigDecimal("0.00")).build(),
            // negative price
            ProductRequest.builder().name("Valid Name").price(new BigDecimal("-1.00")).build()
        );
    }

    /**
     * Property 4: Invalid requests are rejected with 400 (POST)
     *
     * For any POST request where name is blank or price is null or non-positive,
     * the API should return 400 Bad Request and the product catalogue should remain unchanged.
     *
     * Validates: Requirements 1.3, 1.4
     */
    @ParameterizedTest
    @MethodSource("invalidProductRequests")
    void invalidPostRequestRejectedWith400(ProductRequest invalidRequest) throws Exception {
        // Count products before
        MvcResult beforeResult = mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andReturn();
        List<?> before = objectMapper.readValue(beforeResult.getResponse().getContentAsString(), List.class);
        int countBefore = before.size();

        // POST with invalid payload — expect 400
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        // Count products after — should be unchanged
        MvcResult afterResult = mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andReturn();
        List<?> after = objectMapper.readValue(afterResult.getResponse().getContentAsString(), List.class);
        assertThat(after.size()).isEqualTo(countBefore);
    }

    static Stream<Long> nonExistentIds() {
        return Stream.of(Long.MAX_VALUE, 999999L, 1000000L, 9999999L, Long.MAX_VALUE - 1);
    }

    /**
     * Property 5: Non-existent id returns 404
     *
     * For any id that does not correspond to an existing product,
     * GET /api/products/{id}, PUT /api/products/{id}, and DELETE /api/products/{id}
     * should all return 404 Not Found.
     *
     * **Validates: Requirements 3.2, 4.2, 5.2**
     */
    @ParameterizedTest
    @MethodSource("nonExistentIds")
    void nonExistentIdReturns404(Long id) throws Exception {
        // GET /api/products/{id} — expect 404
        mockMvc.perform(get("/api/products/{id}", id))
            .andExpect(status().isNotFound());

        // PUT /api/products/{id} with a valid body — expect 404
        ProductRequest validRequest = ProductRequest.builder()
            .name("Some Product")
            .description("Some description")
            .price(new BigDecimal("9.99"))
            .build();

        mockMvc.perform(put("/api/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
            .andExpect(status().isNotFound());

        // DELETE /api/products/{id} — expect 404
        mockMvc.perform(delete("/api/products/{id}", id))
            .andExpect(status().isNotFound());
    }

    static Stream<Integer> productCounts() {
        return Stream.of(0, 1, 3, 5);
    }

    /**
     * Property 6: Get all returns complete list
     *
     * Insert N products, call GET /api/products, assert array length equals countBefore + N.
     * Other tests may have already inserted products, so we capture the count before inserting.
     *
     * **Validates: Requirements 2.1, 2.2**
     */
    @ParameterizedTest
    @MethodSource("productCounts")
    void getAllReturnsCompleteList(int n) throws Exception {
        // Capture count before inserting
        MvcResult beforeResult = mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andReturn();
        List<?> before = objectMapper.readValue(beforeResult.getResponse().getContentAsString(), List.class);
        int countBefore = before.size();

        // Insert N products
        for (int i = 0; i < n; i++) {
            ProductRequest req = ProductRequest.builder()
                .name("GetAll Product " + i)
                .description("Description " + i)
                .price(new BigDecimal("10.00").add(new BigDecimal(i)))
                .build();
            mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        }

        // GET /api/products — assert length equals countBefore + N
        MvcResult afterResult = mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andReturn();
        List<?> after = objectMapper.readValue(afterResult.getResponse().getContentAsString(), List.class);
        assertThat(after.size()).isEqualTo(countBefore + n);
    }

    /**
     * Property 4: Invalid requests are rejected with 400 (PUT)
     *
     * For any PUT request where name is blank or price is null or non-positive,
     * the API should return 400 Bad Request and the original product should remain unchanged.
     *
     * Validates: Requirements 4.3, 4.4
     */
    @ParameterizedTest
    @MethodSource("invalidProductRequests")
    void invalidPutRequestRejectedWith400(ProductRequest invalidRequest) throws Exception {
        // Create a valid product first
        ProductRequest valid = ProductRequest.builder()
            .name("Original Product")
            .description("Original description")
            .price(new BigDecimal("19.99"))
            .build();

        MvcResult createResult = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(valid)))
            .andExpect(status().isCreated())
            .andReturn();

        ProductResponse created = objectMapper.readValue(
            createResult.getResponse().getContentAsString(),
            ProductResponse.class
        );
        Long id = created.getId();

        // Count products before PUT
        MvcResult beforeResult = mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andReturn();
        List<?> before = objectMapper.readValue(beforeResult.getResponse().getContentAsString(), List.class);
        int countBefore = before.size();

        // PUT with invalid payload — expect 400
        mockMvc.perform(put("/api/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());

        // Original product should be unchanged
        MvcResult getResult = mockMvc.perform(get("/api/products/{id}", id))
            .andExpect(status().isOk())
            .andReturn();
        ProductResponse retrieved = objectMapper.readValue(
            getResult.getResponse().getContentAsString(),
            ProductResponse.class
        );
        assertThat(retrieved.getName()).isEqualTo(valid.getName());
        assertThat(retrieved.getDescription()).isEqualTo(valid.getDescription());
        assertThat(retrieved.getPrice()).isEqualByComparingTo(valid.getPrice());

        // Product count should be unchanged
        MvcResult afterResult = mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andReturn();
        List<?> after = objectMapper.readValue(afterResult.getResponse().getContentAsString(), List.class);
        assertThat(after.size()).isEqualTo(countBefore);
    }
}
