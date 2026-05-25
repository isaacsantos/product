package com.example.products;

import com.example.products.model.AdminProductResponse;
import com.example.products.model.ConditionType;
import com.example.products.model.ProductRequest;
import com.example.products.model.PublicProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Product Condition Fields end-to-end.
 *
 * Verifies condition fields (conditionType, conditionRating) persist correctly
 * through the full stack: admin create/update → database → admin/public GET responses.
 * Liquibase migration is implicitly verified by Testcontainers startup.
 *
 * Property 4: Invalid condition type rejection
 * Validates: Requirements 3.5, 6.1–6.4
 */
class ProductConditionFieldsIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    // ── Helper methods ───────────────────────────────────────────────────────

    private MvcResult createProductAdmin(ProductRequest request) throws Exception {
        return mockMvc.perform(post("/admin/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private AdminProductResponse getAdminProduct(Long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/api/products/{id}", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), AdminProductResponse.class);
    }

    private PublicProductResponse getPublicProduct(Long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), PublicProductResponse.class);
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void postWithConditionFields_getReturnsSameValues_adminAndPublic() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Vintage Guitar")
                .description("1965 Fender Stratocaster")
                .price(new BigDecimal("2500.00"))
                .conditionType(ConditionType.USED)
                .conditionRating(7)
                .build();

        MvcResult createResult = createProductAdmin(request);
        AdminProductResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), AdminProductResponse.class);

        assertThat(created.getConditionType()).isEqualTo(ConditionType.USED);
        assertThat(created.getConditionRating()).isEqualTo(7);

        // Verify admin GET returns same values
        AdminProductResponse adminGet = getAdminProduct(created.getId());
        assertThat(adminGet.getConditionType()).isEqualTo(ConditionType.USED);
        assertThat(adminGet.getConditionRating()).isEqualTo(7);

        // Verify public GET returns same values
        PublicProductResponse publicGet = getPublicProduct(created.getId());
        assertThat(publicGet.getConditionType()).isEqualTo(ConditionType.USED);
        assertThat(publicGet.getConditionRating()).isEqualTo(7);
    }

    @Test
    void postWithConditionTypeNew_getReturnsSameValues() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Brand New Laptop")
                .description("Factory sealed")
                .price(new BigDecimal("999.99"))
                .conditionType(ConditionType.NEW)
                .conditionRating(10)
                .build();

        MvcResult createResult = createProductAdmin(request);
        AdminProductResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), AdminProductResponse.class);

        assertThat(created.getConditionType()).isEqualTo(ConditionType.NEW);
        assertThat(created.getConditionRating()).isEqualTo(10);

        PublicProductResponse publicGet = getPublicProduct(created.getId());
        assertThat(publicGet.getConditionType()).isEqualTo(ConditionType.NEW);
        assertThat(publicGet.getConditionRating()).isEqualTo(10);
    }

    @Test
    void postWithoutConditionFields_getReturnsDefaults() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Default Condition Product")
                .description("No condition fields specified")
                .price(new BigDecimal("49.99"))
                .build();

        MvcResult createResult = createProductAdmin(request);
        AdminProductResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), AdminProductResponse.class);

        // Defaults: conditionType=USED, conditionRating=10
        assertThat(created.getConditionType()).isEqualTo(ConditionType.USED);
        assertThat(created.getConditionRating()).isEqualTo(10);

        // Verify admin GET returns defaults
        AdminProductResponse adminGet = getAdminProduct(created.getId());
        assertThat(adminGet.getConditionType()).isEqualTo(ConditionType.USED);
        assertThat(adminGet.getConditionRating()).isEqualTo(10);

        // Verify public GET returns defaults
        PublicProductResponse publicGet = getPublicProduct(created.getId());
        assertThat(publicGet.getConditionType()).isEqualTo(ConditionType.USED);
        assertThat(publicGet.getConditionRating()).isEqualTo(10);
    }

    @Test
    void putUpdatesConditionFields() throws Exception {
        // Create product with initial condition fields
        ProductRequest createRequest = ProductRequest.builder()
                .name("Updatable Product")
                .description("Will be updated")
                .price(new BigDecimal("75.00"))
                .conditionType(ConditionType.USED)
                .conditionRating(5)
                .build();

        MvcResult createResult = createProductAdmin(createRequest);
        AdminProductResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), AdminProductResponse.class);
        Long id = created.getId();

        // Update condition fields
        ProductRequest updateRequest = ProductRequest.builder()
                .name("Updatable Product")
                .description("Will be updated")
                .price(new BigDecimal("75.00"))
                .conditionType(ConditionType.NEW)
                .conditionRating(9)
                .build();

        MvcResult updateResult = mockMvc.perform(put("/admin/api/products/{id}", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AdminProductResponse updated = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(), AdminProductResponse.class);
        assertThat(updated.getConditionType()).isEqualTo(ConditionType.NEW);
        assertThat(updated.getConditionRating()).isEqualTo(9);

        // Verify GET reflects the update
        AdminProductResponse adminGet = getAdminProduct(id);
        assertThat(adminGet.getConditionType()).isEqualTo(ConditionType.NEW);
        assertThat(adminGet.getConditionRating()).isEqualTo(9);

        PublicProductResponse publicGet = getPublicProduct(id);
        assertThat(publicGet.getConditionType()).isEqualTo(ConditionType.NEW);
        assertThat(publicGet.getConditionRating()).isEqualTo(9);
    }

    @Test
    void postWithConditionRatingBelowRange_returns400() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Invalid Rating Low")
                .description("Rating too low")
                .price(new BigDecimal("10.00"))
                .conditionRating(0)
                .build();

        mockMvc.perform(post("/admin/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postWithConditionRatingAboveRange_returns400() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Invalid Rating High")
                .description("Rating too high")
                .price(new BigDecimal("10.00"))
                .conditionRating(11)
                .build();

        mockMvc.perform(post("/admin/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Property 4: Invalid condition type rejection
     *
     * For any string that is not a valid ConditionType enum name (i.e., not "NEW"
     * and not "USED"), submitting a product creation request with that string as
     * conditionType SHALL result in a 400 Bad Request response.
     *
     * Validates: Requirements 3.5
     */
    @Test
    void postWithInvalidConditionType_returns400() throws Exception {
        String invalidJson = """
                {
                    "name": "Invalid Condition Type",
                    "description": "Has invalid conditionType",
                    "price": 10.00,
                    "conditionType": "OLD",
                    "conditionRating": 5
                }
                """;

        mockMvc.perform(post("/admin/api/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putWithInvalidConditionType_returns400() throws Exception {
        // First create a valid product
        ProductRequest createRequest = ProductRequest.builder()
                .name("Product for Invalid Update")
                .description("Will attempt invalid update")
                .price(new BigDecimal("25.00"))
                .conditionType(ConditionType.USED)
                .conditionRating(5)
                .build();

        MvcResult createResult = createProductAdmin(createRequest);
        AdminProductResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), AdminProductResponse.class);
        Long id = created.getId();

        // Attempt update with invalid conditionType
        String invalidJson = """
                {
                    "name": "Product for Invalid Update",
                    "description": "Will attempt invalid update",
                    "price": 25.00,
                    "conditionType": "REFURBISHED",
                    "conditionRating": 5
                }
                """;

        mockMvc.perform(put("/admin/api/products/{id}", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        // Verify original product is unchanged
        AdminProductResponse afterInvalidUpdate = getAdminProduct(id);
        assertThat(afterInvalidUpdate.getConditionType()).isEqualTo(ConditionType.USED);
        assertThat(afterInvalidUpdate.getConditionRating()).isEqualTo(5);
    }
}
