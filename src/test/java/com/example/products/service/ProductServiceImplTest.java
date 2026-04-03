package com.example.products.service;

import com.example.products.exception.ProductNotFoundException;
import com.example.products.model.Product;
import com.example.products.model.ProductRequest;
import com.example.products.model.ProductResponse;
import com.example.products.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(repository.findAll()).thenReturn(List.of(product, second));

        List<ProductResponse> result = service.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }

    @Test
    void findAll_returnsEmptyListWhenNoProducts() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(service.findAll()).isEmpty();
    }

    // --- findById ---

    @Test
    void findById_returnsProductWhenFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = service.findById(1L);

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
}
