# Implementation Plan: Product Images

## Overview

Extend the `products` service to support multiple image URLs per product. Follows the existing layered architecture: new `ProductImageController` → `ProductImageService` → `ProductImageRepository` → `ProductImage` entity, with `ProductResponse` extended to embed images.

## Tasks

- [x] 1. Add Liquibase migration for `product_images` table
  - Create `002-create-product-images-table.yaml` under `src/main/resources/db/changelog/`
  - Define columns: `id` (BIGSERIAL PK), `product_id` (BIGINT NOT NULL FK → `products.id` ON DELETE CASCADE), `url` (TEXT NOT NULL), `display_order` (INTEGER NOT NULL)
  - Add index on `product_images(product_id)`
  - Register the new changelog in `db.changelog-master.yaml`
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 2. Create `ProductImage` entity and update `Product`
  - [x] 2.1 Create `ProductImage` JPA entity in `model/`
    - Fields: `id`, `productId` (`@Column`), `product` (`@ManyToOne(fetch = LAZY)`), `url`, `displayOrder`
    - Use `@Table(name = "product_images")`, Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
    - _Requirements: 1.1, 10.4_
  - [x] 2.2 Update `Product` entity to add `@OneToMany` relation
    - Add `List<ProductImage> images` with `cascade = CascadeType.ALL`, `orphanRemoval = true`, `@OrderBy("displayOrder ASC")`
    - _Requirements: 8.1, 10.4_

- [x] 3. Create DTOs for image operations
  - [x] 3.1 Create `ImageRequest` in `model/`
    - Field: `List<@NotBlank @URL String> urls` annotated with `@NotEmpty`
    - Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
    - _Requirements: 1.2, 9.1, 9.2, 9.3_
  - [x] 3.2 Create `DisplayOrderRequest` in `model/`
    - Field: `@NotNull @Min(0) Integer displayOrder`
    - _Requirements: 1.3_
  - [x] 3.3 Create `ImageResponse` in `model/`
    - Fields: `id`, `productId`, `url`, `displayOrder`
    - _Requirements: 1.4_
  - [x] 3.4 Update `ProductResponse` to include images
    - Add `@Builder.Default List<ImageResponse> images = new ArrayList<>()`
    - _Requirements: 7.4_

- [x] 4. Create `ProductImageRepository`
  - Create `ProductImageRepository` interface in `repository/` extending `JpaRepository<ProductImage, Long>`
  - Add derived query: `List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId)`
  - _Requirements: 10.3_

- [x] 5. Create `ProductImageService` and `ProductImageServiceImpl`
  - [x] 5.1 Create `ProductImageService` interface in `service/`
    - Methods: `addImages`, `getImages`, `updateDisplayOrder`, `deleteImage`
    - _Requirements: 10.1, 10.2_
  - [x] 5.2 Create `ProductImageServiceImpl` in `service/`
    - Inject `ProductImageRepository` and `ProductRepository`
    - `addImages`: verify product exists, build `ProductImage` entities (auto-assign `displayOrder` starting from current list size), `saveAll`, return `List<ImageResponse>`
    - `getImages`: verify product exists, call `findByProductIdOrderByDisplayOrderAsc`, map to `List<ImageResponse>`
    - `updateDisplayOrder`: verify product exists, find image, verify `image.getProductId().equals(productId)`, update and save, return `ImageResponse`
    - `deleteImage`: verify product exists, find image, verify ownership, `deleteById`
    - Throw `ProductNotFoundException` when product not found; throw `ProductImageNotFoundException` when image not found or doesn't belong to product
    - _Requirements: 3.1, 3.2, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 8.2, 8.3, 10.2_
  - [x] 5.3 Write unit tests for `ProductImageServiceImpl` (`ProductImageServiceImplTest`)
    - Use `@ExtendWith(MockitoExtension.class)`, mock `ProductImageRepository` and `ProductRepository`
    - Happy path: `addImages`, `getImages`, `updateDisplayOrder`, `deleteImage`
    - `ProductNotFoundException` when product not found (all four methods)
    - `ProductImageNotFoundException` when image not found or belongs to different product (`updateDisplayOrder`, `deleteImage`)
    - Verify correct entity→DTO mapping (all four fields of `ImageResponse`)
    - _Requirements: 3.1, 3.2, 4.1, 4.3, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 8.2, 8.3_

- [x] 6. Add `ProductImageNotFoundException` and update `GlobalExceptionHandler`
  - Create `ProductImageNotFoundException` in `exception/` with message `"Image not found: {imageId}"`
  - Add `@ExceptionHandler(ProductImageNotFoundException.class)` handler in `GlobalExceptionHandler` returning `404 Not Found` with `ErrorResponse`
  - _Requirements: 5.3, 6.3, 8.3_

- [x] 7. Create `ProductImageController`
  - Create `ProductImageController` in `controller/` annotated with `@RestController @RequestMapping("/api/products/{productId}/images")`
  - `POST /` → `addImages(@PathVariable Long productId, @Valid @RequestBody ImageRequest)` → `201 Created`
  - `GET /` → `getImages(@PathVariable Long productId)` → `200 OK`
  - `PUT /{imageId}` → `updateDisplayOrder(@PathVariable Long productId, @PathVariable Long imageId, @Valid @RequestBody DisplayOrderRequest)` → `200 OK`
  - `DELETE /{imageId}` → `deleteImage(@PathVariable Long productId, @PathVariable Long imageId)` → `204 No Content`
  - Delegate all logic to `ProductImageService`
  - _Requirements: 3.1, 3.4, 4.1, 4.4, 5.1, 5.5, 6.1, 6.4, 10.1_
  - [x] 7.1 Write `@WebMvcTest` tests for `ProductImageController` (`ProductImageControllerTest`)
    - Mock `ProductImageService` with `@MockitoBean`
    - `POST` without JWT → `401`
    - `PUT` without JWT → `401`
    - `DELETE` without JWT → `401`
    - `GET` without JWT → `200` (public endpoint)
    - `POST` with invalid URL (e.g. `ftp://bad`) → `400`
    - `POST` with blank URL → `400`
    - `PUT` with negative `displayOrder` → `400`
    - `PUT` with null `displayOrder` → `400`
    - _Requirements: 3.3, 3.4, 4.4, 5.4, 5.5, 6.4, 9.1, 9.3_

- [x] 8. Checkpoint — Ensure all tests pass
  - Run `./mvnw test -Dtest='!*IT,!*IntegrationTest'` and verify all unit and `@WebMvcTest` tests pass. Ask the user if questions arise.

- [x] 9. Update `ProductServiceImpl` to embed images in `ProductResponse`
  - Update `toResponse(Product product)` in `ProductServiceImpl` to map `product.getImages()` to `List<ImageResponse>` and set on `ProductResponse`
  - Ensure `ProductResponse` builder uses `@Builder.Default` so `images` defaults to empty list when no images exist
  - _Requirements: 7.1, 7.2, 7.3, 7.4_
  - [x] 9.1 Update `ProductServiceImplTest` to cover images in `toResponse`
    - Add test: product with images → `ProductResponse.images` contains mapped `ImageResponse` list
    - Add test: product with no images → `ProductResponse.images` is empty list
    - _Requirements: 7.3, 7.4_

- [x] 10. Write property-based tests with jqwik (`ProductImagePropertyTest`)
  - Use `@ExtendWith(JqwikExtension.class)` (or rely on jqwik's auto-discovery), `@Property(tries = 100)` on each test
  - All property tests that require a running database MUST extend `AbstractIntegrationTest` — mark the whole class optional since Docker is required
  - [x] 10.1 Property 1: Round-trip de creación de imágenes
    - Generate valid URL lists; POST then GET; assert returned URLs match exactly
    - `// Feature: product-images, Property 1: Round-trip de creación de imágenes`
    - **Validates: Requirements 3.1, 4.1**
  - [x] 10.2 Property 2: Imágenes ordenadas por displayOrder ascendente
    - Generate images with random `displayOrder` values; GET; assert list is non-decreasing by `displayOrder`
    - `// Feature: product-images, Property 2: Imágenes ordenadas por displayOrder ascendente`
    - **Validates: Requirements 4.1, 7.1, 7.2**
  - [x] 10.3 Property 3: Producto inexistente devuelve 404
    - Generate random non-existent `productId`; call POST/GET/PUT/DELETE; assert all return 404
    - `// Feature: product-images, Property 3: Producto inexistente devuelve 404`
    - **Validates: Requirements 3.2, 4.3, 5.2, 6.2**
  - [x] 10.4 Property 4: Validación de pertenencia de imagen al producto
    - Create image under product A; attempt PUT/DELETE via product B's path; assert 404
    - `// Feature: product-images, Property 4: Validación de pertenencia de imagen al producto`
    - **Validates: Requirements 8.2, 8.3, 5.3, 6.3**
  - [x] 10.5 Property 5: URLs inválidas son rechazadas con 400
    - Generate URLs with invalid schemes (ftp://, no scheme, blank); POST; assert 400 with no persistence
    - `// Feature: product-images, Property 5: URLs inválidas o vacías son rechazadas con 400`
    - **Validates: Requirements 9.1, 9.2, 9.3, 3.3, 1.2**
  - [x] 10.6 Property 6: displayOrder inválido es rechazado con 400
    - Generate negative or null `displayOrder`; PUT; assert 400 with no modification
    - `// Feature: product-images, Property 6: displayOrder inválido es rechazado con 400`
    - **Validates: Requirements 5.4, 1.3**
  - [x] 10.7 Property 7: Round-trip de actualización de displayOrder
    - Generate image and valid non-negative `displayOrder`; PUT then GET; assert updated value matches
    - `// Feature: product-images, Property 7: Round-trip de actualización de displayOrder`
    - **Validates: Requirements 5.1**
  - [x] 10.8 Property 8: Eliminación de imagen
    - Generate product with images; DELETE one image; GET; assert deleted image absent from list
    - `// Feature: product-images, Property 8: Eliminación de imagen`
    - **Validates: Requirements 6.1**
  - [x] 10.9 Property 9: Eliminación en cascada al borrar producto
    - Generate product with images; DELETE product; query `product_images` directly; assert 0 rows
    - `// Feature: product-images, Property 9: Eliminación en cascada al borrar producto`
    - **Validates: Requirements 8.1**
  - [x] 10.10 Property 10: ProductResponse incluye imágenes embebidas
    - Generate products with and without images; GET /api/products; assert each `ProductResponse` has `images` field ordered by `displayOrder`
    - `// Feature: product-images, Property 10: ProductResponse incluye imágenes embebidas`
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4**

- [x] 11. Final checkpoint — Ensure all tests pass
  - Run `./mvnw test -Dtest='!*IT,!*IntegrationTest'` and verify everything is green. Ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Property-based tests (task 10) require Docker — skip entirely if Docker is unavailable
- `@WebMvcTest` tests (task 7.1) use a mocked `JwtDecoder` and do not require Docker
- Unit tests (tasks 5.3, 9.1) use Mockito only — no Docker needed
- Each task references specific requirements for traceability
- Checkpoints use `./mvnw test -Dtest='!*IT,!*IntegrationTest'` to avoid Docker-dependent tests
