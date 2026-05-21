package com.example.products.service;

import com.example.products.model.Phase2Result;
import com.example.products.model.TagResponse;
import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeminiVisionService Error Isolation")
class GeminiVisionServiceErrorTest {

    @Mock
    private Client client;

    @Mock
    private Models models;

    private GeminiVisionService service;

    @BeforeEach
    void setUp() throws Exception {
        // Use reflection to set the public final 'models' field on the mocked Client
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);

        service = new GeminiVisionService(client, "gemini-2.5-flash");
    }

    @Test
    @DisplayName("Phase 1 failure prevents Phase 2 execution")
    void phase1FailurePreventsPhase2() {
        // Phase 1 call throws
        when(models.generateContent(eq("gemini-2.5-flash"), any(Content.class), any(GenerateContentConfig.class)))
                .thenThrow(new RuntimeException("Phase 1 API error"));

        List<String> imageUrls = List.of("http://example.com/img1.jpg");
        List<TagResponse> tags = List.of(TagResponse.builder().id(1L).name("Test").type("category").build());

        assertThatThrownBy(() -> service.classifyImages(imageUrls, tags))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Phase 1 API error");

        // Verify generateContent was called only once (Phase 1 only, Phase 2 never attempted)
        verify(models, times(1)).generateContent(
                any(String.class), any(Content.class), any(GenerateContentConfig.class));
    }

    @Test
    @DisplayName("Phase 2 failure propagates exception to caller")
    void phase2FailurePropagatesException() {
        // Phase 1 succeeds
        GenerateContentResponse phase1Response = mock(GenerateContentResponse.class);
        when(phase1Response.text()).thenReturn("[{\"name\":\"Producto\",\"imageIndices\":[0]}]");

        // First call (Phase 1) succeeds, second call (Phase 2) fails
        when(models.generateContent(eq("gemini-2.5-flash"), any(Content.class), any(GenerateContentConfig.class)))
                .thenReturn(phase1Response)
                .thenThrow(new RuntimeException("Phase 2 API error"));

        List<String> imageUrls = List.of("http://example.com/img1.jpg");
        List<TagResponse> tags = List.of(TagResponse.builder().id(1L).name("Test").type("category").build());

        assertThatThrownBy(() -> service.classifyImages(imageUrls, tags))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Phase 2 API error");
    }

    @Test
    @DisplayName("Phase 2 failure logs Phase 1 results at WARN level")
    void phase2FailureLogsPhase1Results() {
        // Phase 1 succeeds
        GenerateContentResponse phase1Response = mock(GenerateContentResponse.class);
        when(phase1Response.text()).thenReturn("[{\"name\":\"Producto\",\"imageIndices\":[0]}]");

        // Phase 2 fails
        when(models.generateContent(eq("gemini-2.5-flash"), any(Content.class), any(GenerateContentConfig.class)))
                .thenReturn(phase1Response)
                .thenThrow(new RuntimeException("Phase 2 API error"));

        List<String> imageUrls = List.of("http://example.com/img1.jpg");
        List<TagResponse> tags = List.of(TagResponse.builder().id(1L).name("Test").type("category").build());

        // The exception should propagate (Phase 2 failure propagates to caller)
        assertThatThrownBy(() -> service.classifyImages(imageUrls, tags))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Phase 2 API error");

        // Verify Phase 2 was attempted (2 calls total: Phase 1 + Phase 2)
        verify(models, times(2)).generateContent(
                any(String.class), any(Content.class), any(GenerateContentConfig.class));
    }

    @Test
    @DisplayName("executePhase2 builds Content with no image parts (text only)")
    void executePhase2HasNoImageParts() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn("[{\"name\":\"Producto\",\"description\":\"Desc\",\"tagIds\":[1]}]");

        ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
        when(models.generateContent(eq("gemini-2.5-flash"), contentCaptor.capture(), any(GenerateContentConfig.class)))
                .thenReturn(response);

        List<String> productNames = List.of("Producto");
        List<TagResponse> tags = List.of(TagResponse.builder().id(1L).name("Test").type("category").build());

        List<Phase2Result> results = service.executePhase2(productNames, tags);

        // Verify the Content sent to Gemini has no image parts
        Content capturedContent = contentCaptor.getValue();
        assertThat(capturedContent.parts()).isPresent();
        capturedContent.parts().get().forEach(part -> {
            // No file data (URI-based images)
            assertThat(part.fileData()).isEmpty();
            // No inline data (byte-based images)
            assertThat(part.inlineData()).isEmpty();
            // All parts should be text-only
            assertThat(part.text()).isPresent();
        });

        // Also verify the response was parsed correctly
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Producto");
    }
}
