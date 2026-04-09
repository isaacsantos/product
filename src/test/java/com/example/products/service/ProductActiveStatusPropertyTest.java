package com.example.products.service;

import com.example.products.model.AdminProductResponse;
import com.example.products.model.Product;
import com.example.products.model.ProductRequest;
import com.example.products.model.PublicProductResponse;
import com.example.products.repository.ProductRepository;
import com.example.products.repository.TagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for the product-active-status feature.
 * Uses JUnit 5 @ParameterizedTest + @MethodSource pattern.
 */
@ExtendWith(MockitoExtension.class)
class ProductActiveStatusPropertyTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private ProductServiceImpl service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Property 1: Default active en creación
    // Validates: Requirements 1.2, 3.1, 4.5
    // -------------------------------------------------------------------------

    static Stream<ProductRequest> defaultActiveRequests() {
        return Stream.of(
            ProductRequest.builder().name("Producto A").description("Desc A").price(new BigDecimal("1.00")).build(),
            ProductRequest.builder().name("Producto B").description("Desc B").price(new BigDecimal("2.50")).build(),
            ProductRequest.builder().name("Producto C").description(null).price(new BigDecimal("9.99")).build(),
            ProductRequest.builder().name("Producto D").description("Largo description aquí").price(new BigDecimal("100.00")).build(),
            ProductRequest.builder().name("X").description("").price(new BigDecimal("0.01")).build(),
            ProductRequest.builder().name("Producto E").description("Desc E").price(new BigDecimal("49.99")).build(),
            ProductRequest.builder().name("Producto F").description("Desc F").price(new BigDecimal("999.99")).build(),
            ProductRequest.builder().name("Producto G").description("Desc G").price(new BigDecimal("5.00")).build(),
            ProductRequest.builder().name("Producto H").description(null).price(new BigDecimal("15.00")).build(),
            ProductRequest.builder().name("Producto I").description("Desc I").price(new BigDecimal("0.99")).build()
        );
    }

    // Feature: product-active-status, Property 1: Default active en creación
    @ParameterizedTest
    @MethodSource("defaultActiveRequests")
    void createAdminWithoutExplicitActiveSetsActiveTrue(ProductRequest request) {
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminProductResponse response = service.createAdmin(request);

        assertThat(response.isActive()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Property 2: El valor explícito de active se preserva
    // Validates: Requirements 4.3, 4.4
    // -------------------------------------------------------------------------

    record RequestWithExpectedActive(ProductRequest request, boolean expectedActive) {}

    static Stream<RequestWithExpectedActive> explicitActiveRequests() {
        return Stream.of(
            new RequestWithExpectedActive(ProductRequest.builder().name("P1").price(new BigDecimal("1.00")).active(true).build(), true),
            new RequestWithExpectedActive(ProductRequest.builder().name("P2").price(new BigDecimal("2.00")).active(true).build(), true),
            new RequestWithExpectedActive(ProductRequest.builder().name("P3").price(new BigDecimal("3.00")).active(true).build(), true),
            new RequestWithExpectedActive(ProductRequest.builder().name("P4").price(new BigDecimal("4.00")).active(true).build(), true),
            new RequestWithExpectedActive(ProductRequest.builder().name("P5").price(new BigDecimal("5.00")).active(true).build(), true),
            new RequestWithExpectedActive(ProductRequest.builder().name("P6").price(new BigDecimal("6.00")).active(false).build(), false),
            new RequestWithExpectedActive(ProductRequest.builder().name("P7").price(new BigDecimal("7.00")).active(false).build(), false),
            new RequestWithExpectedActive(ProductRequest.builder().name("P8").price(new BigDecimal("8.00")).active(false).build(), false),
            new RequestWithExpectedActive(ProductRequest.builder().name("P9").price(new BigDecimal("9.00")).active(false).build(), false),
            new RequestWithExpectedActive(ProductRequest.builder().name("P10").price(new BigDecimal("10.00")).active(false).build(), false)
        );
    }

    // Feature: product-active-status, Property 2: El valor explícito de active se preserva
    @ParameterizedTest
    @MethodSource("explicitActiveRequests")
    void explicitActiveValueIsPreservedInCreateAdmin(RequestWithExpectedActive sample) {
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminProductResponse response = service.createAdmin(sample.request());

        assertThat(response.isActive()).isEqualTo(sample.expectedActive());
    }

    // -------------------------------------------------------------------------
    // Property 3: Round-trip de active en AdminProductResponse
    // Validates: Requirements 4.1, 6.1, 6.5
    // -------------------------------------------------------------------------

    static Stream<Product> productsForRoundTrip() {
        return Stream.of(
            Product.builder().id(1L).name("P1").price(new BigDecimal("1.00")).active(true).build(),
            Product.builder().id(2L).name("P2").price(new BigDecimal("2.00")).active(true).build(),
            Product.builder().id(3L).name("P3").price(new BigDecimal("3.00")).active(true).build(),
            Product.builder().id(4L).name("P4").price(new BigDecimal("4.00")).active(true).build(),
            Product.builder().id(5L).name("P5").price(new BigDecimal("5.00")).active(true).build(),
            Product.builder().id(6L).name("P6").price(new BigDecimal("6.00")).active(false).build(),
            Product.builder().id(7L).name("P7").price(new BigDecimal("7.00")).active(false).build(),
            Product.builder().id(8L).name("P8").price(new BigDecimal("8.00")).active(false).build(),
            Product.builder().id(9L).name("P9").price(new BigDecimal("9.00")).active(false).build(),
            Product.builder().id(10L).name("P10").price(new BigDecimal("10.00")).active(false).build()
        );
    }

    // Feature: product-active-status, Property 3: Round-trip de active en AdminProductResponse
    @ParameterizedTest
    @MethodSource("productsForRoundTrip")
    void adminResponseActiveRoundTrip(Product product) {
        when(repository.findById(product.getId())).thenReturn(Optional.of(product));

        AdminProductResponse response = service.findByIdAdmin(product.getId());

        assertThat(response.isActive()).isEqualTo(product.isActive());
    }

    // -------------------------------------------------------------------------
    // Property 4: PublicProductResponse nunca expone active
    // Validates: Requirements 5.1, 6.2
    // -------------------------------------------------------------------------

    static Stream<Product> productsForPublicResponse() {
        return Stream.of(
            Product.builder().id(1L).name("P1").price(new BigDecimal("1.00")).active(true).build(),
            Product.builder().id(2L).name("P2").price(new BigDecimal("2.00")).active(true).build(),
            Product.builder().id(3L).name("P3").price(new BigDecimal("3.00")).active(true).build(),
            Product.builder().id(4L).name("P4").price(new BigDecimal("4.00")).active(true).build(),
            Product.builder().id(5L).name("P5").price(new BigDecimal("5.00")).active(true).build(),
            Product.builder().id(6L).name("P6").price(new BigDecimal("6.00")).active(false).build(),
            Product.builder().id(7L).name("P7").price(new BigDecimal("7.00")).active(false).build(),
            Product.builder().id(8L).name("P8").price(new BigDecimal("8.00")).active(false).build(),
            Product.builder().id(9L).name("P9").price(new BigDecimal("9.00")).active(false).build(),
            Product.builder().id(10L).name("P10").price(new BigDecimal("10.00")).active(false).build()
        );
    }

    // Feature: product-active-status, Property 4: PublicProductResponse nunca expone active
    @ParameterizedTest
    @MethodSource("productsForPublicResponse")
    void publicResponseNeverExposesActive(Product product) throws Exception {
        when(repository.findById(product.getId())).thenReturn(Optional.of(product));

        PublicProductResponse response = service.findById(product.getId());

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).doesNotContain("\"active\"");
    }
}
