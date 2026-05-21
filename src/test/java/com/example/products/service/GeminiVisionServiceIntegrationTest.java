package com.example.products.service;

import com.example.products.model.AiClassifiedProduct;
import com.example.products.model.TagResponse;
import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeminiVisionService Integration - End-to-End Two-Phase Flow")
class GeminiVisionServiceIntegrationTest {

    @Mock
    private Client client;

    @Mock
    private Models models;

    private GeminiVisionService service;

    private static final String PHASE1_RESPONSE = """
            [{"name":"Consola PS5","imageIndices":[0,1]},{"name":"Control Xbox","imageIndices":[2]}]""";

    private static final String PHASE2_RESPONSE = """
            [{"name":"Consola PS5","description":"Consola de videojuegos de última generación.","tagIds":[1,3]},\
            {"name":"Control Xbox","description":"Control inalámbrico para Xbox.","tagIds":[2]}]""";

    private static final List<String> IMAGE_URLS = List.of(
            "http://example.com/ps5-front.jpg",
            "http://example.com/ps5-back.jpg",
            "http://example.com/xbox-controller.jpg"
    );

    private static final List<TagResponse> TAGS = List.of(
            TagResponse.builder().id(1L).name("Electrónica").type("category").build(),
            TagResponse.builder().id(2L).name("Accesorios").type("category").build(),
            TagResponse.builder().id(3L).name("Gaming").type("category").build()
    );

    @BeforeEach
    void setUp() throws Exception {
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);

        service = new GeminiVisionService(client, "gemini-2.5-flash");
    }

    @Test
    @DisplayName("classifyImages returns correctly merged products from Phase 1 and Phase 2")
    void classifyImagesReturnsMergedProducts() {
        // Arrange
        GenerateContentResponse phase1Response = mock(GenerateContentResponse.class);
        when(phase1Response.text()).thenReturn(PHASE1_RESPONSE);

        GenerateContentResponse phase2Response = mock(GenerateContentResponse.class);
        when(phase2Response.text()).thenReturn(PHASE2_RESPONSE);

        when(models.generateContent(eq("gemini-2.5-flash"), any(Content.class), any(GenerateContentConfig.class)))
                .thenReturn(phase1Response)
                .thenReturn(phase2Response);

        // Act
        List<AiClassifiedProduct> results = service.classifyImages(IMAGE_URLS, TAGS);

        // Assert
        assertThat(results).hasSize(2);

        AiClassifiedProduct product1 = results.get(0);
        assertThat(product1.getName()).isEqualTo("Consola PS5");
        assertThat(product1.getDescription()).isEqualTo("Consola de videojuegos de última generación.");
        assertThat(product1.getImageIndices()).containsExactly(0, 1);
        assertThat(product1.getTagIds()).containsExactly(1L, 3L);

        AiClassifiedProduct product2 = results.get(1);
        assertThat(product2.getName()).isEqualTo("Control Xbox");
        assertThat(product2.getDescription()).isEqualTo("Control inalámbrico para Xbox.");
        assertThat(product2.getImageIndices()).containsExactly(2);
        assertThat(product2.getTagIds()).containsExactly(2L);
    }

    @Test
    @DisplayName("Phase 1 request contains image parts (fileData) and a text part with vision prompt")
    void phase1RequestContainsImagePartsAndTextPrompt() {
        // Arrange
        GenerateContentResponse phase1Response = mock(GenerateContentResponse.class);
        when(phase1Response.text()).thenReturn(PHASE1_RESPONSE);

        GenerateContentResponse phase2Response = mock(GenerateContentResponse.class);
        when(phase2Response.text()).thenReturn(PHASE2_RESPONSE);

        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
        when(models.generateContent(eq("gemini-2.5-flash"), contentCaptor.capture(), any(GenerateContentConfig.class)))
                .thenReturn(phase1Response)
                .thenReturn(phase2Response);

        // Act
        service.classifyImages(IMAGE_URLS, TAGS);

        // Assert - Phase 1 content (first captured argument)
        List<Content> capturedContents = contentCaptor.getAllValues();
        assertThat(capturedContents).hasSize(2);

        Content phase1Content = capturedContents.get(0);
        assertThat(phase1Content.parts()).isPresent();
        List<Part> phase1Parts = phase1Content.parts().get();

        // Should have 3 image parts + 1 text part = 4 parts total
        assertThat(phase1Parts).hasSize(4);

        // First 3 parts should be image parts (fileData present)
        for (int i = 0; i < 3; i++) {
            assertThat(phase1Parts.get(i).fileData())
                    .as("Part %d should have fileData (image)", i)
                    .isPresent();
            assertThat(phase1Parts.get(i).text())
                    .as("Part %d should not have text", i)
                    .isEmpty();
        }

        // Last part should be text (the vision prompt)
        Part textPart = phase1Parts.get(3);
        assertThat(textPart.text()).isPresent();
        assertThat(textPart.fileData()).isEmpty();
        assertThat(textPart.text().get()).contains("product");
    }

    @Test
    @DisplayName("Phase 2 request contains only text parts (no fileData, no inlineData)")
    void phase2RequestContainsOnlyTextParts() {
        // Arrange
        GenerateContentResponse phase1Response = mock(GenerateContentResponse.class);
        when(phase1Response.text()).thenReturn(PHASE1_RESPONSE);

        GenerateContentResponse phase2Response = mock(GenerateContentResponse.class);
        when(phase2Response.text()).thenReturn(PHASE2_RESPONSE);

        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
        when(models.generateContent(eq("gemini-2.5-flash"), contentCaptor.capture(), any(GenerateContentConfig.class)))
                .thenReturn(phase1Response)
                .thenReturn(phase2Response);

        // Act
        service.classifyImages(IMAGE_URLS, TAGS);

        // Assert - Phase 2 content (second captured argument)
        List<Content> capturedContents = contentCaptor.getAllValues();
        Content phase2Content = capturedContents.get(1);
        assertThat(phase2Content.parts()).isPresent();

        List<Part> phase2Parts = phase2Content.parts().get();
        for (Part part : phase2Parts) {
            // No file data (URI-based images)
            assertThat(part.fileData())
                    .as("Phase 2 parts should not have fileData")
                    .isEmpty();
            // No inline data (byte-based images)
            assertThat(part.inlineData())
                    .as("Phase 2 parts should not have inlineData")
                    .isEmpty();
            // All parts should be text-only
            assertThat(part.text())
                    .as("Phase 2 parts should be text-only")
                    .isPresent();
        }
    }

    @Test
    @DisplayName("Both phases are called exactly once during classifyImages")
    void bothPhasesCalledExactlyOnce() {
        // Arrange
        GenerateContentResponse phase1Response = mock(GenerateContentResponse.class);
        when(phase1Response.text()).thenReturn(PHASE1_RESPONSE);

        GenerateContentResponse phase2Response = mock(GenerateContentResponse.class);
        when(phase2Response.text()).thenReturn(PHASE2_RESPONSE);

        when(models.generateContent(eq("gemini-2.5-flash"), any(Content.class), any(GenerateContentConfig.class)))
                .thenReturn(phase1Response)
                .thenReturn(phase2Response);

        // Act
        service.classifyImages(IMAGE_URLS, TAGS);

        // Assert - exactly 2 calls: Phase 1 + Phase 2
        verify(models, times(2)).generateContent(
                eq("gemini-2.5-flash"), any(Content.class), any(GenerateContentConfig.class));
    }
}
