package com.example.products;

import com.example.products.model.ImportResult;
import com.example.products.model.ImportRowResult;
import com.example.products.model.ProductResponse;
import com.example.products.model.RowStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import net.jqwik.api.*;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based integration tests for Bulk Product Import feature.
 * Extends AbstractIntegrationTest to use a real PostgreSQL database via Testcontainers.
 * Named *IT so it is excluded from non-Docker test runs.
 */
class BulkImportPropertyIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static PrivateKey testPrivateKey;

    private static final String IMPORT_URL = "/api/products/import";

    @BeforeAll
    static void loadPrivateKey() throws Exception {
        String pem = new String(
                BulkImportPropertyIT.class.getResourceAsStream("/certs/private.pem").readAllBytes()
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

    private String adminJwt() throws Exception {
        return signedJwt("ROLE_ADMIN");
    }

    private String signedJwt(String role) throws Exception {
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

    // ── CSV helpers ──────────────────────────────────────────────────────────

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv", content.getBytes());
    }

    private String validCsvRow(String name, String description, String price, String url) {
        return name + "," + description + "," + price + "," + url;
    }

    private String defaultValidRow() {
        return "Widget,A widget,9.99,https://example.com/img.jpg";
    }

    // ── Arbitraries ──────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> csvContents() {
        return Arbitraries.strings().withCharRange(' ', '~').ofMinLength(0).ofMaxLength(200);
    }

    @Provide
    Arbitrary<String> nonAdminManagerRoles() {
        return Arbitraries.of("ROLE_USER", "ROLE_VIEWER", "ROLE_EDITOR", "ROLE_GUEST", "ROLE_READONLY");
    }

    @Provide
    Arbitrary<String> authorizedRoles() {
        return Arbitraries.of("ROLE_ADMIN", "ROLE_MANAGER");
    }

    @Provide
    Arbitrary<String> invalidContentTypes() {
        return Arbitraries.of(
                "image/png",
                "image/jpeg",
                "application/json",
                "application/xml",
                "text/plain",
                "text/html",
                "application/pdf",
                "multipart/form-data"
        );
    }

    /** Valid product name: no commas, non-blank, max 50 chars */
    @Provide
    Arbitrary<String> validProductNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30)
                .filter(s -> !s.isBlank());
    }

    /** Valid description: no commas */
    @Provide
    Arbitrary<String> validDescriptions() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(0)
                .ofMaxLength(50);
    }

    /** Valid price: >= 0.01, at most 2 decimal places */
    @Provide
    Arbitrary<BigDecimal> validPrices() {
        return Arbitraries.integers().between(1, 99999)
                .map(cents -> new BigDecimal(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    /** Valid URL: starts with https:// */
    @Provide
    Arbitrary<String> validUrls() {
        return Arbitraries.integers().between(1, 9999)
                .map(n -> "https://cdn.example.com/image-" + n + ".jpg");
    }

    @Provide
    Arbitrary<String> invalidRows() {
        return Arbitraries.of(
                // wrong column count (3 cols)
                "OnlyThree,cols,here",
                // wrong column count (5 cols)
                "Five,cols,here,extra,column",
                // blank name
                ",description,9.99,https://example.com/img.jpg",
                // bad price (non-numeric)
                "Widget,desc,notaprice,https://example.com/img.jpg",
                // bad price (zero)
                "Widget,desc,0.00,https://example.com/img.jpg",
                // bad price (negative)
                "Widget,desc,-1.00,https://example.com/img.jpg",
                // bad URL (no scheme)
                "Widget,desc,9.99,example.com/img.jpg",
                // bad URL (ftp)
                "Widget,desc,9.99,ftp://example.com/img.jpg"
        );
    }

    // ── Properties ───────────────────────────────────────────────────────────

    // Feature: bulk-product-import, Property 1: Unauthenticated requests are rejected
    // Validates: Requirements 1.1
    @Property(tries = 100)
    void unauthenticatedRequestsAreRejected(@ForAll("csvContents") String csvContent) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "import.csv", "text/csv",
                csvContent.getBytes());

        mockMvc.perform(multipart(IMPORT_URL).file(file))
                .andExpect(status().isUnauthorized());
    }

    // Feature: bulk-product-import, Property 2: Insufficient role is rejected
    // Validates: Requirements 1.2
    @Property(tries = 100)
    void insufficientRoleIsRejected(@ForAll("nonAdminManagerRoles") String role) throws Exception {
        mockMvc.perform(multipart(IMPORT_URL)
                        .file(csvFile(defaultValidRow()))
                        .with(jwt().authorities(new SimpleGrantedAuthority(role))))
                .andExpect(status().isForbidden());
    }

    // Feature: bulk-product-import, Property 3: Authorized roles allow processing
    // Validates: Requirements 1.3, 1.4
    @Property(tries = 100)
    void authorizedRolesAllowProcessing(@ForAll("authorizedRoles") String role) throws Exception {
        MvcResult result = mockMvc.perform(multipart(IMPORT_URL)
                        .file(csvFile(defaultValidRow()))
                        .with(jwt().authorities(new SimpleGrantedAuthority(role))))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status).isNotEqualTo(401);
        assertThat(status).isNotEqualTo(403);
    }

    // Feature: bulk-product-import, Property 4: Invalid content type is rejected
    // Validates: Requirements 2.3
    @Property(tries = 100)
    void invalidContentTypeIsRejected(@ForAll("invalidContentTypes") String contentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "import.csv", contentType,
                defaultValidRow().getBytes());

        mockMvc.perform(multipart(IMPORT_URL)
                        .file(file)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    // Feature: bulk-product-import, Property 5: Invalid rows are marked FAILED and processing continues
    // Validates: Requirements 3.3, 3.4, 3.5, 3.6
    @Property(tries = 100)
    void invalidRowsAreMarkedFailedAndProcessingContinues(
            @ForAll("invalidRows") String invalidRow) throws Exception {
        // Mix one valid row and one invalid row
        String csv = defaultValidRow() + "\n" + invalidRow;

        MvcResult result = mockMvc.perform(multipart(IMPORT_URL)
                        .file(csvFile(csv))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        ImportResult importResult = objectMapper.readValue(
                result.getResponse().getContentAsString(), ImportResult.class);

        // Find the invalid row result (row 2)
        ImportRowResult invalidRowResult = importResult.getRows().stream()
                .filter(r -> r.getRowNumber() == 2)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Row 2 not found in result"));

        assertThat(invalidRowResult.getStatus()).isEqualTo(RowStatus.FAILED);
        assertThat(invalidRowResult.getErrorMessage()).isNotBlank();

        // The valid row (row 1) should be SUCCESS
        ImportRowResult validRowResult = importResult.getRows().stream()
                .filter(r -> r.getRowNumber() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Row 1 not found in result"));

        assertThat(validRowResult.getStatus()).isEqualTo(RowStatus.SUCCESS);
    }

    // Feature: bulk-product-import, Property 6: Blank description is treated as valid
    // Validates: Requirements 3.7
    @Property(tries = 100)
    void blankDescriptionIsTreatedAsValid(
            @ForAll("validProductNames") String name,
            @ForAll("validPrices") BigDecimal price,
            @ForAll("validUrls") String url) throws Exception {
        // Use blank description
        String csv = name + ",," + price.toPlainString() + "," + url;

        MvcResult result = mockMvc.perform(multipart(IMPORT_URL)
                        .file(csvFile(csv))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        ImportResult importResult = objectMapper.readValue(
                result.getResponse().getContentAsString(), ImportResult.class);

        assertThat(importResult.getRows()).hasSize(1);
        ImportRowResult row = importResult.getRows().get(0);
        assertThat(row.getStatus()).isEqualTo(RowStatus.SUCCESS);
        assertThat(row.getProductId()).isNotNull();

        // Verify the created product has empty or null description
        MvcResult productResult = mockMvc.perform(get("/api/products/{id}", row.getProductId()))
                .andExpect(status().isOk())
                .andReturn();

        ProductResponse product = objectMapper.readValue(
                productResult.getResponse().getContentAsString(), ProductResponse.class);

        // Description should be empty string or null (blank description treated as empty)
        assertThat(product.getDescription() == null || product.getDescription().isEmpty()).isTrue();
    }

    // Feature: bulk-product-import, Property 7: Row-level failure isolation
    // Validates: Requirements 4.4, 4.5
    @Property(tries = 100)
    void rowLevelFailureIsolation(@ForAll("invalidRows") String invalidRow) throws Exception {
        // Build a CSV with: valid, invalid, valid
        String validRow1 = "ProductA,desc,1.99,https://example.com/a.jpg";
        String validRow2 = "ProductB,desc,2.99,https://example.com/b.jpg";
        String csv = validRow1 + "\n" + invalidRow + "\n" + validRow2;

        MvcResult result = mockMvc.perform(multipart(IMPORT_URL)
                        .file(csvFile(csv))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        ImportResult importResult = objectMapper.readValue(
                result.getResponse().getContentAsString(), ImportResult.class);

        assertThat(importResult.getRows()).hasSize(3);

        ImportRowResult row1 = importResult.getRows().get(0);
        ImportRowResult row2 = importResult.getRows().get(1);
        ImportRowResult row3 = importResult.getRows().get(2);

        assertThat(row1.getStatus()).isEqualTo(RowStatus.SUCCESS);
        assertThat(row2.getStatus()).isEqualTo(RowStatus.FAILED);
        assertThat(row2.getErrorMessage()).isNotBlank();
        assertThat(row3.getStatus()).isEqualTo(RowStatus.SUCCESS);
    }

    // Feature: bulk-product-import, Property 8: Import always returns 200 with ImportResult
    // Validates: Requirements 5.1
    @Property(tries = 100)
    void importAlwaysReturns200WithImportResult(
            @ForAll("validProductNames") String name,
            @ForAll("validPrices") BigDecimal price,
            @ForAll("validUrls") String url) throws Exception {
        String csv = name + ",desc," + price.toPlainString() + "," + url;

        MvcResult result = mockMvc.perform(multipart(IMPORT_URL)
                        .file(csvFile(csv))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        ImportResult importResult = objectMapper.readValue(
                result.getResponse().getContentAsString(), ImportResult.class);

        assertThat(importResult).isNotNull();
        assertThat(importResult.getRows()).isNotNull();
    }

    @Provide
    Arbitrary<List<String>> validCsvRowLists() {
        Arbitrary<String> row = Combinators.combine(
                validProductNames(),
                validPrices(),
                validUrls()
        ).as((name, price, url) -> name + ",desc," + price.toPlainString() + "," + url);
        return row.list().ofMinSize(1).ofMaxSize(5);
    }

    // Feature: bulk-product-import, Property 9: Result count invariant
    // Validates: Requirements 5.2, 5.5
    @Property(tries = 100)
    void resultCountInvariant(@ForAll("validCsvRowLists") List<String> rows) throws Exception {
        int n = rows.size();
        String csv = String.join("\n", rows);

        MvcResult result = mockMvc.perform(multipart(IMPORT_URL)
                        .file(csvFile(csv))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        ImportResult importResult = objectMapper.readValue(
                result.getResponse().getContentAsString(), ImportResult.class);

        assertThat(importResult.getRows()).hasSize(n);
        assertThat(importResult.getTotalRows()).isEqualTo(n);
        assertThat(importResult.getTotalRows())
                .isEqualTo(importResult.getSuccessCount() + importResult.getFailedCount());
    }

    // Feature: bulk-product-import, Property 10: ImportRowResult structure completeness
    // Validates: Requirements 5.3, 5.4
    @Property(tries = 100)
    void importRowResultStructureCompleteness(@ForAll("invalidRows") String invalidRow) throws Exception {
        String csv = defaultValidRow() + "\n" + invalidRow;

        MvcResult result = mockMvc.perform(multipart(IMPORT_URL)
                        .file(csvFile(csv))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        ImportResult importResult = objectMapper.readValue(
                result.getResponse().getContentAsString(), ImportResult.class);

        for (ImportRowResult row : importResult.getRows()) {
            if (row.getStatus() == RowStatus.SUCCESS) {
                assertThat(row.getProductId()).isNotNull();
                assertThat(row.getErrorMessage()).isNull();
            } else {
                assertThat(row.getErrorMessage()).isNotBlank();
                assertThat(row.getProductId()).isNull();
            }
        }
    }

    // Feature: bulk-product-import, Property 11: CSV round-trip data fidelity
    // Validates: Requirements 6.1, 6.2
    @Property(tries = 100)
    void csvRoundTripDataFidelity(
            @ForAll("validProductNames") String name,
            @ForAll("validDescriptions") String description,
            @ForAll("validPrices") BigDecimal price,
            @ForAll("validUrls") String url) throws Exception {
        String csv = name + "," + description + "," + price.toPlainString() + "," + url;

        MvcResult result = mockMvc.perform(multipart(IMPORT_URL)
                        .file(csvFile(csv))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        ImportResult importResult = objectMapper.readValue(
                result.getResponse().getContentAsString(), ImportResult.class);

        assertThat(importResult.getRows()).hasSize(1);
        ImportRowResult row = importResult.getRows().get(0);
        assertThat(row.getStatus()).isEqualTo(RowStatus.SUCCESS);
        assertThat(row.getProductId()).isNotNull();

        // GET the created product and verify field fidelity
        MvcResult productResult = mockMvc.perform(get("/api/products/{id}", row.getProductId()))
                .andExpect(status().isOk())
                .andReturn();

        ProductResponse product = objectMapper.readValue(
                productResult.getResponse().getContentAsString(), ProductResponse.class);

        assertThat(product.getName()).isEqualTo(name);
        assertThat(product.getPrice().compareTo(price)).isZero();

        // Description: blank description is stored as empty string or null
        if (description.isBlank()) {
            assertThat(product.getDescription() == null || product.getDescription().isEmpty()).isTrue();
        } else {
            assertThat(product.getDescription()).isEqualTo(description);
        }

        // GET images and verify URL matches
        MvcResult imagesResult = mockMvc.perform(get("/api/products/{id}/images", row.getProductId()))
                .andExpect(status().isOk())
                .andReturn();

        List<?> images = objectMapper.readValue(
                imagesResult.getResponse().getContentAsString(), new TypeReference<List<?>>() {});
        assertThat(images).hasSize(1);

        // Extract URL from the image response map
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> imageMap = (java.util.Map<String, Object>) images.get(0);
        assertThat(imageMap.get("url")).isEqualTo(url);
    }
}
