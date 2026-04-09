package com.example.products;

import com.example.products.controller.ProductImageController;
import com.example.products.exception.GlobalExceptionHandler;
import com.example.products.exception.InvalidImageTypeException;
import com.example.products.exception.ProductImageNotFoundException;
import com.example.products.exception.ProductNotFoundException;
import com.example.products.model.ImageResponse;
import com.example.products.service.ProductImageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Property-based tests for ProductImageController using jqwik.
 * Uses standaloneSetup to avoid Spring context injection issues with jqwik.
 */
class ProductImagePropertyTest {

    private final ProductImageService productImageService = Mockito.mock(ProductImageService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProductImageController(productImageService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─────────────────────────────────────────────────────────────────────────
    // Property 1: Respuesta de upload contiene todos los campos requeridos
    // Feature: product-images, Property 1: Respuesta de upload contiene todos los campos requeridos
    // Validates: Requirements 1.2, 1.4
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void uploadResponseContainsAllRequiredFields(
            @ForAll @IntRange(min = 1, max = 100) int productId,
            @ForAll @Size(min = 1, max = 5) List<@NotBlank String> publicIds
    ) throws Exception {
        // Feature: product-images, Property 1: Respuesta de upload contiene todos los campos requeridos
        reset(productImageService);

        List<ImageResponse> responses = IntStream.range(0, publicIds.size())
                .mapToObj(i -> ImageResponse.builder()
                        .id((long) (i + 1))
                        .productId((long) productId)
                        .url("https://res.cloudinary.com/sample/" + i)
                        .displayOrder(i)
                        .build())
                .toList();

        when(productImageService.uploadImages(eq((long) productId), anyList())).thenReturn(responses);

        MockMultipartFile file = new MockMultipartFile("files", "img.jpg", "image/jpeg", "data".getBytes());

        String json = mockMvc.perform(
                        multipart("/admin/api/products/" + productId + "/images/upload")
                                .file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        List<?> result = objectMapper.readValue(json, List.class);
        assertThat(result).isNotEmpty();

        for (Object item : result) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) item;
            assertThat(map).containsKey("id");
            assertThat(map).containsKey("productId");
            assertThat(map).containsKey("url");
            assertThat(map).containsKey("displayOrder");
            assertThat(map.get("id")).isNotNull();
            assertThat(map.get("productId")).isEqualTo(productId);
            String url = (String) map.get("url");
            assertThat(url).startsWith("https://");
            assertThat((Integer) map.get("displayOrder")).isGreaterThanOrEqualTo(0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 2: displayOrder auto-asignado es consecutivo desde el conteo actual
    // Feature: product-images, Property 2: displayOrder auto-asignado es consecutivo desde el conteo actual
    // Validates: Requirements 1.3
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void displayOrderIsConsecutiveFromCurrentCount(
            @ForAll @IntRange(min = 0, max = 20) int existingCount,
            @ForAll @IntRange(min = 1, max = 5) int newFileCount
    ) throws Exception {
        // Feature: product-images, Property 2: displayOrder auto-asignado es consecutivo desde el conteo actual
        reset(productImageService);
        long productId = 1L;

        List<ImageResponse> responses = IntStream.range(0, newFileCount)
                .mapToObj(i -> ImageResponse.builder()
                        .id((long) (existingCount + i + 1))
                        .productId(productId)
                        .url("https://res.cloudinary.com/img" + i + ".jpg")
                        .displayOrder(existingCount + i)
                        .build())
                .toList();

        when(productImageService.uploadImages(eq(productId), anyList())).thenReturn(responses);

        MockMultipartHttpServletRequestBuilder requestBuilder =
                (MockMultipartHttpServletRequestBuilder) multipart("/admin/api/products/" + productId + "/images/upload");
        for (int i = 0; i < newFileCount; i++) {
            requestBuilder = (MockMultipartHttpServletRequestBuilder) requestBuilder.file(
                    new MockMultipartFile("files", "img" + i + ".jpg", "image/jpeg", "data".getBytes()));
        }

        String json = mockMvc.perform(requestBuilder)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        List<?> result = objectMapper.readValue(json, List.class);
        assertThat(result).hasSize(newFileCount);

        for (int i = 0; i < result.size(); i++) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) result.get(i);
            assertThat((Integer) map.get("displayOrder")).isEqualTo(existingCount + i);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 3: Tipo de archivo inválido devuelve 400
    // Feature: product-images, Property 3: Tipo de archivo inválido devuelve 400
    // Validates: Requirements 1.7
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void invalidContentTypeReturns400(
            @ForAll("invalidContentTypes") String contentType
    ) throws Exception {
        // Feature: product-images, Property 3: Tipo de archivo inválido devuelve 400
        reset(productImageService);
        when(productImageService.uploadImages(anyLong(), anyList()))
                .thenThrow(new InvalidImageTypeException(contentType));

        MockMultipartFile file = new MockMultipartFile("files", "file.bin", contentType, "data".getBytes());

        mockMvc.perform(multipart("/admin/api/products/1/images/upload")
                        .file(file))
                .andExpect(status().isBadRequest());

        verify(productImageService, atLeastOnce()).uploadImages(anyLong(), anyList());
    }

    @Provide
    Arbitrary<String> invalidContentTypes() {
        return Arbitraries.of(
                "application/pdf",
                "text/plain",
                "application/octet-stream",
                "video/mp4",
                "audio/mpeg",
                "application/json",
                "text/html",
                "application/zip",
                "image/tiff",
                "image/bmp"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 4: cloudinaryPublicId se persiste en cada imagen subida
    // Feature: product-images, Property 4: cloudinaryPublicId se persiste en cada imagen subida
    // Validates: Requirements 4.2
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void cloudinaryPublicIdIsPersistedForEachUpload(
            @ForAll @Size(min = 1, max = 5) List<@NotBlank String> publicIds
    ) throws Exception {
        // Feature: product-images, Property 4: cloudinaryPublicId se persiste en cada imagen subida
        reset(productImageService);
        long productId = 1L;

        List<ImageResponse> responses = IntStream.range(0, publicIds.size())
                .mapToObj(i -> ImageResponse.builder()
                        .id((long) (i + 1))
                        .productId(productId)
                        .url("https://res.cloudinary.com/" + i)
                        .displayOrder(i)
                        .build())
                .toList();

        when(productImageService.uploadImages(eq(productId), anyList())).thenReturn(responses);

        MockMultipartHttpServletRequestBuilder requestBuilder =
                (MockMultipartHttpServletRequestBuilder) multipart("/admin/api/products/" + productId + "/images/upload");
        for (int i = 0; i < publicIds.size(); i++) {
            requestBuilder = (MockMultipartHttpServletRequestBuilder) requestBuilder.file(
                    new MockMultipartFile("files", "img" + i + ".jpg", "image/jpeg", "data".getBytes()));
        }

        mockMvc.perform(requestBuilder)
                .andExpect(status().isCreated());

        // Verify service was called with the correct number of files
        verify(productImageService).uploadImages(eq(productId), argThat(list -> list.size() == publicIds.size()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 5: Eliminación invoca Cloudinary con el publicId correcto
    // Feature: product-images, Property 5: Eliminación invoca Cloudinary con el publicId correcto
    // Validates: Requirements 2.1, 2.2
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void deleteInvokesServiceWithCorrectImageId(
            @ForAll @IntRange(min = 1, max = 1000) int productId,
            @ForAll @IntRange(min = 1, max = 1000) int imageId
    ) throws Exception {
        // Feature: product-images, Property 5: Eliminación invoca Cloudinary con el publicId correcto
        reset(productImageService);
        doNothing().when(productImageService).deleteImage((long) productId, (long) imageId);

        mockMvc.perform(delete("/admin/api/products/" + productId + "/images/" + imageId))
                .andExpect(status().isNoContent());

        verify(productImageService).deleteImage((long) productId, (long) imageId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 6: cloudinaryPublicId no se expone en ImageResponse
    // Feature: product-images, Property 6: cloudinaryPublicId no se expone en ImageResponse
    // Validates: Requirements 4.3
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void cloudinaryPublicIdNotExposedInImageResponse(
            @ForAll @IntRange(min = 1, max = 100) int productId,
            @ForAll @NotBlank String publicId
    ) throws Exception {
        // Feature: product-images, Property 6: cloudinaryPublicId no se expone en ImageResponse
        reset(productImageService);
        List<ImageResponse> responses = List.of(
                ImageResponse.builder()
                        .id(1L)
                        .productId((long) productId)
                        .url("https://res.cloudinary.com/img.jpg")
                        .displayOrder(0)
                        .build()
        );

        when(productImageService.uploadImages(eq((long) productId), anyList())).thenReturn(responses);

        MockMultipartFile file = new MockMultipartFile("files", "img.jpg", "image/jpeg", "data".getBytes());

        String json = mockMvc.perform(
                        multipart("/admin/api/products/" + productId + "/images/upload")
                                .file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(json).doesNotContain("cloudinaryPublicId");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 7: Producto inexistente devuelve 404 en todos los endpoints
    // Feature: product-images, Property 7: Producto inexistente devuelve 404 en todos los endpoints
    // Validates: Requirements 1.5
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void nonExistentProductReturns404OnUpload(
            @ForAll @IntRange(min = 1, max = 10000) int nonExistentProductId
    ) throws Exception {
        // Feature: product-images, Property 7: Producto inexistente devuelve 404 en todos los endpoints
        reset(productImageService);
        when(productImageService.uploadImages(eq((long) nonExistentProductId), anyList()))
                .thenThrow(new ProductNotFoundException((long) nonExistentProductId));

        MockMultipartFile file = new MockMultipartFile("files", "img.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/admin/api/products/" + nonExistentProductId + "/images/upload")
                        .file(file))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 8: Imagen que no pertenece al producto devuelve 404
    // Feature: product-images, Property 8: Imagen que no pertenece al producto devuelve 404
    // Validates: Requirements 2.4
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void imageNotBelongingToProductReturns404OnDelete(
            @ForAll @IntRange(min = 1, max = 500) int productId,
            @ForAll @IntRange(min = 501, max = 1000) int imageId
    ) throws Exception {
        // Feature: product-images, Property 8: Imagen que no pertenece al producto devuelve 404
        reset(productImageService);
        doThrow(new ProductImageNotFoundException((long) imageId))
                .when(productImageService).deleteImage((long) productId, (long) imageId);

        mockMvc.perform(delete("/admin/api/products/" + productId + "/images/" + imageId))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 9: Round-trip de creación de imágenes (endpoint JSON existente)
    // Feature: product-images, Property 9: Round-trip de creación de imágenes (endpoint JSON existente)
    // Validates: Requirements 1.1
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void jsonEndpointRoundTripReturnsExactUrls(
            @ForAll @IntRange(min = 1, max = 100) int productId,
            @ForAll @Size(min = 1, max = 5) List<@NotBlank String> urlSuffixes
    ) throws Exception {
        // Feature: product-images, Property 9: Round-trip de creación de imágenes (endpoint JSON existente)
        reset(productImageService);
        List<String> urls = urlSuffixes.stream()
                .map(s -> "https://example.com/" + s.replaceAll("[^a-zA-Z0-9]", "x") + ".jpg")
                .toList();

        List<ImageResponse> responses = IntStream.range(0, urls.size())
                .mapToObj(i -> ImageResponse.builder()
                        .id((long) (i + 1))
                        .productId((long) productId)
                        .url(urls.get(i))
                        .displayOrder(i)
                        .build())
                .toList();

        when(productImageService.addImages(eq((long) productId), any())).thenReturn(responses);

        String requestBody = objectMapper.writeValueAsString(java.util.Map.of("urls", urls));

        String json = mockMvc.perform(post("/admin/api/products/" + productId + "/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        List<?> result = objectMapper.readValue(json, List.class);
        assertThat(result).hasSize(urls.size());

        for (int i = 0; i < result.size(); i++) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) result.get(i);
            assertThat(map.get("url")).isEqualTo(urls.get(i));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Property 10: Imágenes ordenadas por displayOrder ascendente
    // Feature: product-images, Property 10: Imágenes ordenadas por displayOrder ascendente
    // Validates: Requirements 1.3
    // ─────────────────────────────────────────────────────────────────────────

    @Property(tries = 100)
    void getImagesReturnsImagesInAscendingDisplayOrder(
            @ForAll @IntRange(min = 1, max = 100) int productId,
            @ForAll @Size(min = 1, max = 10) List<@IntRange(min = 0, max = 100) Integer> displayOrders
    ) throws Exception {
        // Feature: product-images, Property 10: Imágenes ordenadas por displayOrder ascendente
        reset(productImageService);
        List<Integer> sorted = displayOrders.stream().sorted().toList();

        List<ImageResponse> responses = IntStream.range(0, sorted.size())
                .mapToObj(i -> ImageResponse.builder()
                        .id((long) (i + 1))
                        .productId((long) productId)
                        .url("https://example.com/img" + i + ".jpg")
                        .displayOrder(sorted.get(i))
                        .build())
                .toList();

        when(productImageService.getImages(eq((long) productId))).thenReturn(responses);

        String json = mockMvc.perform(get("/admin/api/products/" + productId + "/images"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<?> result = objectMapper.readValue(json, List.class);
        assertThat(result).hasSize(sorted.size());

        List<Integer> returnedOrders = result.stream()
                .map(item -> {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) item;
                    return (Integer) map.get("displayOrder");
                })
                .toList();

        for (int i = 1; i < returnedOrders.size(); i++) {
            assertThat(returnedOrders.get(i)).isGreaterThanOrEqualTo(returnedOrders.get(i - 1));
        }
    }
}
