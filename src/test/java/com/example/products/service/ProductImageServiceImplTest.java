package com.example.products.service;

import com.example.products.exception.ProductImageNotFoundException;
import com.example.products.exception.ProductNotFoundException;
import com.example.products.model.*;
import com.example.products.repository.ProductImageRepository;
import com.example.products.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceImplTest {

    @Mock
    private ProductImageRepository imageRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    private ProductImageServiceImpl service;

    private static final Long PRODUCT_ID = 1L;
    private static final Long IMAGE_ID = 10L;

    private ProductImage image;

    @BeforeEach
    void setUp() {
        service = new ProductImageServiceImpl(imageRepository, productRepository, cloudinaryService);
        image = ProductImage.builder()
                .id(IMAGE_ID)
                .productId(PRODUCT_ID)
                .url("https://example.com/image.jpg")
                .displayOrder(0)
                .build();
    }

    // --- addImages ---

    @Test
    void addImages_happyPath_savesAndReturnsImageResponsesWithAllFields() {
        ImageRequest request = ImageRequest.builder()
                .urls(List.of("https://example.com/a.jpg", "https://example.com/b.jpg"))
                .build();

        ProductImage saved1 = ProductImage.builder().id(10L).productId(PRODUCT_ID).url("https://example.com/a.jpg").displayOrder(0).build();
        ProductImage saved2 = ProductImage.builder().id(11L).productId(PRODUCT_ID).url("https://example.com/b.jpg").displayOrder(1).build();

        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findByProductIdOrderByDisplayOrderAsc(PRODUCT_ID)).thenReturn(List.of());
        when(imageRepository.saveAll(anyList())).thenReturn(List.of(saved1, saved2));

        List<ImageResponse> result = service.addImages(PRODUCT_ID, request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(result.get(0).getUrl()).isEqualTo("https://example.com/a.jpg");
        assertThat(result.get(0).getDisplayOrder()).isEqualTo(0);
        assertThat(result.get(1).getId()).isEqualTo(11L);
        assertThat(result.get(1).getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(result.get(1).getUrl()).isEqualTo("https://example.com/b.jpg");
        assertThat(result.get(1).getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void addImages_throwsProductNotFoundException_whenProductNotFound() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(false);
        ImageRequest request = ImageRequest.builder().urls(List.of("https://example.com/a.jpg")).build();

        assertThatThrownBy(() -> service.addImages(PRODUCT_ID, request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(PRODUCT_ID.toString());

        verify(imageRepository, never()).saveAll(any());
    }

    // --- uploadImages ---

    @Test
    void uploadImages_happyPath_persistsCloudinaryPublicIdAndReturnsResponses() {
        MockMultipartFile file1 = new MockMultipartFile("files", "a.jpg", "image/jpeg", "data".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "b.png", "image/png", "data".getBytes());

        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findByProductIdOrderByDisplayOrderAsc(PRODUCT_ID)).thenReturn(List.of());
        when(cloudinaryService.upload(file1)).thenReturn(new CloudinaryUploadResult("https://res.cloudinary.com/a.jpg", "pub1"));
        when(cloudinaryService.upload(file2)).thenReturn(new CloudinaryUploadResult("https://res.cloudinary.com/b.png", "pub2"));

        ProductImage saved1 = ProductImage.builder().id(10L).productId(PRODUCT_ID).url("https://res.cloudinary.com/a.jpg").cloudinaryPublicId("pub1").displayOrder(0).build();
        ProductImage saved2 = ProductImage.builder().id(11L).productId(PRODUCT_ID).url("https://res.cloudinary.com/b.png").cloudinaryPublicId("pub2").displayOrder(1).build();
        when(imageRepository.saveAll(anyList())).thenReturn(List.of(saved1, saved2));

        List<ImageResponse> result = service.uploadImages(PRODUCT_ID, List.of(file1, file2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUrl()).isEqualTo("https://res.cloudinary.com/a.jpg");
        assertThat(result.get(1).getUrl()).isEqualTo("https://res.cloudinary.com/b.png");
    }

    @Test
    void uploadImages_throwsProductNotFoundException_whenProductNotFound() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile("files", "a.jpg", "image/jpeg", "data".getBytes());

        assertThatThrownBy(() -> service.uploadImages(PRODUCT_ID, List.of(file)))
                .isInstanceOf(ProductNotFoundException.class);

        verify(cloudinaryService, never()).upload(any());
    }

    // --- getImages ---

    @Test
    void getImages_happyPath_returnsOrderedImageResponseList() {
        ProductImage img1 = ProductImage.builder().id(10L).productId(PRODUCT_ID).url("https://example.com/a.jpg").displayOrder(0).build();
        ProductImage img2 = ProductImage.builder().id(11L).productId(PRODUCT_ID).url("https://example.com/b.jpg").displayOrder(1).build();

        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findByProductIdOrderByDisplayOrderAsc(PRODUCT_ID)).thenReturn(List.of(img1, img2));

        List<ImageResponse> result = service.getImages(PRODUCT_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDisplayOrder()).isEqualTo(0);
        assertThat(result.get(1).getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void getImages_throwsProductNotFoundException_whenProductNotFound() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getImages(PRODUCT_ID))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(PRODUCT_ID.toString());
    }

    // --- updateDisplayOrder ---

    @Test
    void updateDisplayOrder_happyPath_updatesAndReturnsImageResponse() {
        DisplayOrderRequest request = new DisplayOrderRequest(5);
        ProductImage updated = ProductImage.builder().id(IMAGE_ID).productId(PRODUCT_ID).url("https://example.com/image.jpg").displayOrder(5).build();

        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(image));
        when(imageRepository.save(image)).thenReturn(updated);

        ImageResponse result = service.updateDisplayOrder(PRODUCT_ID, IMAGE_ID, request);

        assertThat(result.getDisplayOrder()).isEqualTo(5);
        verify(imageRepository).save(image);
    }

    @Test
    void updateDisplayOrder_throwsProductNotFoundException_whenProductNotFound() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.updateDisplayOrder(PRODUCT_ID, IMAGE_ID, new DisplayOrderRequest(1)))
                .isInstanceOf(ProductNotFoundException.class);

        verify(imageRepository, never()).save(any());
    }

    @Test
    void updateDisplayOrder_throwsProductImageNotFoundException_whenImageNotFound() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDisplayOrder(PRODUCT_ID, IMAGE_ID, new DisplayOrderRequest(1)))
                .isInstanceOf(ProductImageNotFoundException.class);
    }

    @Test
    void updateDisplayOrder_throwsProductImageNotFoundException_whenImageBelongsToDifferentProduct() {
        ProductImage otherProductImage = ProductImage.builder().id(IMAGE_ID).productId(99L).url("https://example.com/image.jpg").displayOrder(0).build();

        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(otherProductImage));

        assertThatThrownBy(() -> service.updateDisplayOrder(PRODUCT_ID, IMAGE_ID, new DisplayOrderRequest(1)))
                .isInstanceOf(ProductImageNotFoundException.class);
    }

    // --- deleteImage ---

    @Test
    void deleteImage_happyPath_deletesSuccessfully() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        service.deleteImage(PRODUCT_ID, IMAGE_ID);

        verify(imageRepository).deleteById(IMAGE_ID);
    }

    @Test
    void deleteImage_withCloudinaryPublicId_invokesCloudinaryDelete() {
        ProductImage imageWithPublicId = ProductImage.builder()
                .id(IMAGE_ID).productId(PRODUCT_ID).url("https://res.cloudinary.com/img.jpg")
                .displayOrder(0).cloudinaryPublicId("pub123").build();

        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(imageWithPublicId));

        service.deleteImage(PRODUCT_ID, IMAGE_ID);

        verify(imageRepository).deleteById(IMAGE_ID);
        verify(cloudinaryService).delete("pub123");
    }

    @Test
    void deleteImage_withNullCloudinaryPublicId_doesNotInvokeCloudinaryDelete() {
        // image has no cloudinaryPublicId (null)
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        service.deleteImage(PRODUCT_ID, IMAGE_ID);

        verify(imageRepository).deleteById(IMAGE_ID);
        verify(cloudinaryService, never()).delete(any());
    }

    @Test
    void deleteImage_whenCloudinaryFails_stillCompletesDbDeletion() {
        ProductImage imageWithPublicId = ProductImage.builder()
                .id(IMAGE_ID).productId(PRODUCT_ID).url("https://res.cloudinary.com/img.jpg")
                .displayOrder(0).cloudinaryPublicId("pub123").build();

        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(imageWithPublicId));
        doThrow(new RuntimeException("Cloudinary error")).when(cloudinaryService).delete("pub123");

        // Should not throw
        assertThatCode(() -> service.deleteImage(PRODUCT_ID, IMAGE_ID)).doesNotThrowAnyException();
        verify(imageRepository).deleteById(IMAGE_ID);
    }

    @Test
    void deleteImage_throwsProductNotFoundException_whenProductNotFound() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteImage(PRODUCT_ID, IMAGE_ID))
                .isInstanceOf(ProductNotFoundException.class);

        verify(imageRepository, never()).deleteById(any());
    }

    @Test
    void deleteImage_throwsProductImageNotFoundException_whenImageNotFound() {
        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteImage(PRODUCT_ID, IMAGE_ID))
                .isInstanceOf(ProductImageNotFoundException.class);

        verify(imageRepository, never()).deleteById(any());
    }

    @Test
    void deleteImage_throwsProductImageNotFoundException_whenImageBelongsToDifferentProduct() {
        ProductImage otherProductImage = ProductImage.builder().id(IMAGE_ID).productId(99L).url("https://example.com/image.jpg").displayOrder(0).build();

        when(productRepository.existsById(PRODUCT_ID)).thenReturn(true);
        when(imageRepository.findById(IMAGE_ID)).thenReturn(Optional.of(otherProductImage));

        assertThatThrownBy(() -> service.deleteImage(PRODUCT_ID, IMAGE_ID))
                .isInstanceOf(ProductImageNotFoundException.class);

        verify(imageRepository, never()).deleteById(any());
    }
}
