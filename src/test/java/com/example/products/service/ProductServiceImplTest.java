package com.example.products.service;

import com.example.products.exception.ProductNotFoundException;
import com.example.products.model.AdminProductResponse;
import com.example.products.model.ConditionType;
import com.example.products.model.ImageResponse;
import com.example.products.model.PageResponse;
import com.example.products.model.Product;
import com.example.products.model.ProductImage;
import com.example.products.model.ProductRequest;
import com.example.products.model.ProductResponse;
import com.example.products.model.PublicProductResponse;
import com.example.products.repository.ProductRepository;
import com.example.products.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private ProductServiceImpl service;

    private Product product;
    private ProductRequest request;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L)
                .name("Widget")
                .description("A useful widget")
                .price(new BigDecimal("9.99"))
                .build();

        request = ProductRequest.builder()
                .name("Widget")
                .description("A useful widget")
                .price(new BigDecimal("9.99"))
                .build();
    }

    // --- create ---

    @Test
    void create_mapsAllFieldsAndReturnsResponse() {
        when(repository.save(any(Product.class))).thenReturn(product);

        ProductResponse response = service.create(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Widget");
        assertThat(response.getDescription()).isEqualTo("A useful widget");
        assertThat(response.getPrice()).isEqualByComparingTo("9.99");
    }

    @Test
    void create_savesProductWithCorrectFields() {
        when(repository.save(any(Product.class))).thenReturn(product);

        service.create(request);

        verify(repository).save(argThat(p ->
                "Widget".equals(p.getName()) &&
                "A useful widget".equals(p.getDescription()) &&
                new BigDecimal("9.99").compareTo(p.getPrice()) == 0
        ));
    }

    // --- findAll ---

    @Test
    void findAll_returnsAllProductsMapped() {
        Product second = Product.builder().id(2L).name("Gadget").description(null).price(new BigDecimal("19.99")).build();
        when(repository.findByActiveTrue(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(product, second)));

        PageResponse<PublicProductResponse> result = service.findAll(0, 20, null);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getContent().get(1).getId()).isEqualTo(2L);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    void findAll_returnsEmptyPageWhenNoProducts() {
        when(repository.findByActiveTrue(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<PublicProductResponse> result = service.findAll(0, 20, null);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // --- findById ---

    @Test
    void findById_returnsProductWhenFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        PublicProductResponse response = service.findById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Widget");
    }

    @Test
    void findById_throwsProductNotFoundExceptionWhenAbsent() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- update ---

    @Test
    void update_appliesAllFieldsAndReturnsUpdatedResponse() {
        ProductRequest updateRequest = ProductRequest.builder()
                .name("Updated Widget")
                .description("New description")
                .price(new BigDecimal("14.99"))
                .build();
        Product updated = Product.builder().id(1L).name("Updated Widget").description("New description").price(new BigDecimal("14.99")).build();

        when(repository.findById(1L)).thenReturn(Optional.of(product));
        when(repository.save(any(Product.class))).thenReturn(updated);

        ProductResponse response = service.update(1L, updateRequest);

        assertThat(response.getName()).isEqualTo("Updated Widget");
        assertThat(response.getDescription()).isEqualTo("New description");
        assertThat(response.getPrice()).isEqualByComparingTo("14.99");
    }

    @Test
    void update_throwsProductNotFoundExceptionWhenAbsent() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- delete ---

    @Test
    void delete_callsDeleteByIdWhenProductExists() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void delete_throwsProductNotFoundExceptionWhenAbsent() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");

        verify(repository, never()).deleteById(any());
    }

    // --- toResponse image mapping ---

    @Test
    void toResponse_mapsImagesCorrectlyWhenProductHasImages() {
        ProductImage img1 = ProductImage.builder().id(10L).productId(1L).url("http://example.com/a.jpg").displayOrder(1).build();
        ProductImage img2 = ProductImage.builder().id(11L).productId(1L).url("http://example.com/b.jpg").displayOrder(2).build();
        product.getImages().addAll(List.of(img1, img2));

        when(repository.findById(1L)).thenReturn(Optional.of(product));

        PublicProductResponse response = service.findById(1L);

        assertThat(response.getImages()).hasSize(2);

        ImageResponse first = response.getImages().get(0);
        assertThat(first.getId()).isEqualTo(10L);
        assertThat(first.getProductId()).isEqualTo(1L);
        assertThat(first.getUrl()).isEqualTo("http://example.com/a.jpg");
        assertThat(first.getDisplayOrder()).isEqualTo(1);

        ImageResponse second = response.getImages().get(1);
        assertThat(second.getId()).isEqualTo(11L);
        assertThat(second.getProductId()).isEqualTo(1L);
        assertThat(second.getUrl()).isEqualTo("http://example.com/b.jpg");
        assertThat(second.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void toResponse_imagesIsEmptyWhenProductHasNoImages() {
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        PublicProductResponse response = service.findById(1L);

        assertThat(response.getImages()).isEmpty();
    }

    // --- createAdmin condition field mapping ---

    @Test
    void createAdmin_mapsProvidedConditionTypeAndRatingToResponse() {
        ProductRequest conditionRequest = ProductRequest.builder()
                .name("Used Guitar")
                .description("A vintage guitar")
                .price(new BigDecimal("250.00"))
                .conditionType(ConditionType.NEW)
                .conditionRating(8)
                .build();

        Product savedProduct = Product.builder()
                .id(2L)
                .name("Used Guitar")
                .description("A vintage guitar")
                .price(new BigDecimal("250.00"))
                .conditionType(ConditionType.NEW)
                .conditionRating(8)
                .build();

        when(repository.save(any(Product.class))).thenReturn(savedProduct);

        AdminProductResponse response = service.createAdmin(conditionRequest);

        assertThat(response.getConditionType()).isEqualTo(ConditionType.NEW);
        assertThat(response.getConditionRating()).isEqualTo(8);
    }

    @Test
    void createAdmin_persistsProvidedConditionFieldsOnEntity() {
        ProductRequest conditionRequest = ProductRequest.builder()
                .name("Used Guitar")
                .description("A vintage guitar")
                .price(new BigDecimal("250.00"))
                .conditionType(ConditionType.USED)
                .conditionRating(3)
                .build();

        Product savedProduct = Product.builder()
                .id(2L)
                .name("Used Guitar")
                .description("A vintage guitar")
                .price(new BigDecimal("250.00"))
                .conditionType(ConditionType.USED)
                .conditionRating(3)
                .build();

        when(repository.save(any(Product.class))).thenReturn(savedProduct);

        service.createAdmin(conditionRequest);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(captor.capture());
        Product persisted = captor.getValue();
        assertThat(persisted.getConditionType()).isEqualTo(ConditionType.USED);
        assertThat(persisted.getConditionRating()).isEqualTo(3);
    }

    @Test
    void createAdmin_appliesDefaultsWhenConditionFieldsAreNull() {
        ProductRequest nullConditionRequest = ProductRequest.builder()
                .name("Default Product")
                .description("No condition specified")
                .price(new BigDecimal("50.00"))
                .conditionType(null)
                .conditionRating(null)
                .build();

        Product savedProduct = Product.builder()
                .id(3L)
                .name("Default Product")
                .description("No condition specified")
                .price(new BigDecimal("50.00"))
                .conditionType(ConditionType.USED)
                .conditionRating(10)
                .build();

        when(repository.save(any(Product.class))).thenReturn(savedProduct);

        AdminProductResponse response = service.createAdmin(nullConditionRequest);

        assertThat(response.getConditionType()).isEqualTo(ConditionType.USED);
        assertThat(response.getConditionRating()).isEqualTo(10);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(captor.capture());
        Product persisted = captor.getValue();
        assertThat(persisted.getConditionType()).isEqualTo(ConditionType.USED);
        assertThat(persisted.getConditionRating()).isEqualTo(10);
    }

    // --- updateAdmin condition field mapping ---

    @Test
    void updateAdmin_updatesConditionFieldsWhenProvided() {
        Product existingProduct = Product.builder()
                .id(1L)
                .name("Widget")
                .description("A useful widget")
                .price(new BigDecimal("9.99"))
                .conditionType(ConditionType.USED)
                .conditionRating(5)
                .build();

        ProductRequest updateRequest = ProductRequest.builder()
                .name("Widget")
                .description("A useful widget")
                .price(new BigDecimal("9.99"))
                .conditionType(ConditionType.NEW)
                .conditionRating(9)
                .build();

        Product updatedProduct = Product.builder()
                .id(1L)
                .name("Widget")
                .description("A useful widget")
                .price(new BigDecimal("9.99"))
                .conditionType(ConditionType.NEW)
                .conditionRating(9)
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(existingProduct));
        when(repository.save(any(Product.class))).thenReturn(updatedProduct);

        AdminProductResponse response = service.updateAdmin(1L, updateRequest);

        assertThat(response.getConditionType()).isEqualTo(ConditionType.NEW);
        assertThat(response.getConditionRating()).isEqualTo(9);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(captor.capture());
        Product persisted = captor.getValue();
        assertThat(persisted.getConditionType()).isEqualTo(ConditionType.NEW);
        assertThat(persisted.getConditionRating()).isEqualTo(9);
    }

    @Test
    void updateAdmin_preservesExistingConditionFieldsWhenRequestValuesAreNull() {
        Product existingProduct = Product.builder()
                .id(1L)
                .name("Widget")
                .description("A useful widget")
                .price(new BigDecimal("9.99"))
                .conditionType(ConditionType.NEW)
                .conditionRating(7)
                .build();

        ProductRequest updateRequest = ProductRequest.builder()
                .name("Widget Updated")
                .description("Updated description")
                .price(new BigDecimal("12.99"))
                .conditionType(null)
                .conditionRating(null)
                .build();

        Product updatedProduct = Product.builder()
                .id(1L)
                .name("Widget Updated")
                .description("Updated description")
                .price(new BigDecimal("12.99"))
                .conditionType(ConditionType.NEW)
                .conditionRating(7)
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(existingProduct));
        when(repository.save(any(Product.class))).thenReturn(updatedProduct);

        AdminProductResponse response = service.updateAdmin(1L, updateRequest);

        assertThat(response.getConditionType()).isEqualTo(ConditionType.NEW);
        assertThat(response.getConditionRating()).isEqualTo(7);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(captor.capture());
        Product persisted = captor.getValue();
        assertThat(persisted.getConditionType()).isEqualTo(ConditionType.NEW);
        assertThat(persisted.getConditionRating()).isEqualTo(7);
    }
}
