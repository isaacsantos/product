package com.example.products;

import com.example.products.config.CorsProperties;
import com.example.products.config.SecurityConfig;
import com.example.products.controller.ProductController;
import com.example.products.service.ProductService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, SecurityConfigIT.TestConfig.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:certs/public.pem",
        "security.cors.allowed-origins=http://localhost:3000"
})
class SecurityConfigIT {

    // ── RSA private key loaded once for all tests ────────────────────────────
    private static PrivateKey testPrivateKey;

    @BeforeAll
    static void loadPrivateKey() throws Exception {
        String pem = new String(
                SecurityConfigIT.class.getResourceAsStream("/certs/private.pem").readAllBytes()
        );
        String stripped = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        testPrivateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    // ── Minimal test config: only enables CorsProperties binding ────────────
    @TestConfiguration
    @EnableConfigurationProperties(CorsProperties.class)
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    // ── JWT helpers ──────────────────────────────────────────────────────────

    private String buildJwt(PrivateKey signingKey, Date expiry) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user")
                .issuer("test-issuer")
                .expirationTime(expiry)
                .issueTime(new Date())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private String validJwt() throws Exception {
        return buildJwt(testPrivateKey, new Date(System.currentTimeMillis() + 60_000));
    }

    private String expiredJwt() throws Exception {
        return buildJwt(testPrivateKey, new Date(System.currentTimeMillis() - 60_000));
    }

    private String invalidSignatureJwt() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        return buildJwt(pair.getPrivate(), new Date(System.currentTimeMillis() + 60_000));
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void getProductsWithoutToken_returns200() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    void getProductByIdWithoutToken_returns200orNotFound() throws Exception {
        mockMvc.perform(get("/api/products/1"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 200 || status == 404
                            : "Expected 200 or 404 but got " + status;
                });
    }

    @Test
    void postWithoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":1.00}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void putWithoutToken_returns401() throws Exception {
        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":1.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteWithoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithValidJwt_isAllowed() throws Exception {
        String token = validJwt();
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":1.00}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status != 401 && status != 403
                            : "Expected not 401/403 but got " + status;
                });
    }

    @Test
    void postWithExpiredJwt_returns401() throws Exception {
        String token = expiredJwt();
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":1.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithInvalidSignature_returns401() throws Exception {
        String token = invalidSignatureJwt();
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"price\":1.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void optionsPreflightWithoutToken_returns200() throws Exception {
        mockMvc.perform(options("/api/products")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    void noSessionCreated() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
