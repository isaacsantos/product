# Implementation Plan: Product CRUD

## Overview

Implement full CRUD operations for the `Product` entity in the `products` Spring Boot application. Tasks follow the layered architecture: dependencies → schema → data model → repository → service → controller → error handling → wiring → tests.

## Tasks

- [x] 1. Add required Maven dependencies to pom.xml
  - Add `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `org.postgresql:postgresql` (runtime scope), and `org.liquibase:liquibase-core` to `products/pom.xml`
  - Add `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` in test scope for integration tests
  - _Requirements: 7.1, 9.3_

- [x] 2. Configure datasource and Liquibase in application.yaml
  - Add `spring.datasource` properties (url, username, password, driver-class-name) pointing to a local PostgreSQL instance
  - Add `spring.jpa.hibernate.ddl-auto: validate` and `spring.liquibase.change-log: classpath:db/changelog/db.changelog-master.yaml`
  - _Requirements: 7.1_

- [x] 3. Create Liquibase changelog and products table migration
  - Create `src/main/resources/db/changelog/db.changelog-master.yaml` as the master changelog
  - Create `src/main/resources/db/changelog/001-create-products-table.yaml` defining the `products` table with columns: `id` (BIGSERIAL PK), `name` (VARCHAR NOT NULL), `description` (TEXT nullable), `price` (NUMERIC NOT NULL)
  - _Requirements: 7.1, 7.2_

- [x] 4. Implement the Product JPA entity and DTOs
  - [x] 4.1 Create `model/Product.java` — `@Entity @Table(name="products")` with `@Id @GeneratedValue(IDENTITY)` on `id`, Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
    - _Requirements: 6.1_
  - [x] 4.2 Create `model/ProductRequest.java` — inbound DTO with `@NotBlank` on `name`, `@NotNull @DecimalMin("0.01")` on `price`, Lombok annotations
    - _Requirements: 6.2_
  - [x] 4.3 Create `model/ProductResponse.java` — outbound DTO with `id`, `name`, `description`, `price`, Lombok annotations
    - _Requirements: 6.3_
  - [x] 4.4 Create `exception/ProductNotFoundException.java` — extends `RuntimeException`, accepts `Long id`, message: `"Product not found: " + id`
    - _Requirements: 8.1_

- [x] 5. Implement ProductRepository
  - Create `repository/ProductRepository.java` extending `JpaRepository<Product, Long>`
  - _Requirements: 9.3_

- [x] 6. Implement ProductService and ProductServiceImpl
  - [x] 6.1 Create `service/ProductService.java` interface with methods: `create`, `findAll`, `findById`, `update`, `delete`
    - _Requirements: 9.2_
  - [x] 6.2 Create `service/ProductServiceImpl.java` implementing `ProductService`, annotated `@Service`
    - Implement `create`: map `ProductRequest` → `Product`, call `repository.save()`, return `ProductResponse`
    - Implement `findAll`: call `repository.findAll()`, map each to `ProductResponse`
    - Implement `findById`: call `repository.findById()`, throw `ProductNotFoundException` if empty, return `ProductResponse`
    - Implement `update`: find by id (throw if absent), apply all fields from request, save, return `ProductResponse`
    - Implement `delete`: check `existsById` (throw if false), call `deleteById`
    - Include private `toResponse(Product)` mapping helper
    - _Requirements: 1.1, 1.2, 2.1, 2.2, 3.1, 3.2, 4.1, 4.2, 5.1, 5.2, 9.2_
  - [x] 6.3 Write unit tests for ProductServiceImpl
    - Mock `ProductRepository` with Mockito
    - Test happy path for each of the five operations
    - Test `ProductNotFoundException` thrown by `findById`, `update`, and `delete` when entity absent
    - Test DTO mapping: all fields transferred correctly in `create` and `update`
    - _Requirements: 1.1, 1.2, 3.1, 3.2, 4.1, 4.2, 5.1, 5.2_

- [x] 7. Implement GlobalExceptionHandler
  - Create `exception/GlobalExceptionHandler.java` annotated `@RestControllerAdvice`
  - Create `model/ErrorResponse.java` with fields `status` (int), `message` (String), `timestamp` (Instant), Lombok `@Data @Builder @AllArgsConstructor`
  - Handle `ProductNotFoundException` → 404 with `ErrorResponse`
  - Handle `MethodArgumentNotValidException` → 400 with field-level error messages
  - Handle generic `Exception` → 500 with generic `ErrorResponse`
  - _Requirements: 8.1, 8.2, 8.3_

- [x] 8. Implement ProductController
  - Create `controller/ProductController.java` annotated `@RestController @RequestMapping("/api/products")`
  - `POST /` → `create(@Valid @RequestBody ProductRequest)` → 201 Created with `ProductResponse`
  - `GET /` → `findAll()` → 200 OK with `List<ProductResponse>`
  - `GET /{id}` → `findById(@PathVariable Long id)` → 200 OK with `ProductResponse`
  - `PUT /{id}` → `update(@PathVariable Long id, @Valid @RequestBody ProductRequest)` → 200 OK with `ProductResponse`
  - `DELETE /{id}` → `delete(@PathVariable Long id)` → 204 No Content
  - Inject `ProductService` via constructor
  - _Requirements: 1.1, 1.3, 1.4, 1.5, 2.1, 2.2, 3.1, 3.2, 4.1, 4.2, 4.3, 4.4, 5.1, 5.2, 9.1_

- [x] 9. Checkpoint — Ensure all unit tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Write integration tests with Testcontainers
  - [x] 10.1 Create base `AbstractIntegrationTest.java` with `@SpringBootTest`, `@AutoConfigureMockMvc`, and a shared `PostgreSQLContainer` started once via `@BeforeAll`
    - Configure `spring.datasource.*` dynamic properties from the container
    - _Requirements: 7.1_
  - [x] 10.2 Write property test for create-then-retrieve round-trip (Property 1)
    - Use `@ParameterizedTest @MethodSource` with varied valid `ProductRequest` inputs (different names, descriptions, prices)
    - POST each request, then GET by returned id, assert all fields match
    - **Property 1: Create then retrieve round-trip**
    - **Validates: Requirements 1.1, 1.2, 3.1**
  - [x] 10.3 Write property test for update-then-retrieve round-trip (Property 2)
    - Create a product, then PUT with varied update payloads, then GET and assert fields match update values
    - **Property 2: Update then retrieve round-trip**
    - **Validates: Requirements 4.1**
  - [x] 10.4 Write property test for delete-then-retrieve returns 404 (Property 3)
    - Create a product, DELETE it, then GET and assert 404
    - **Property 3: Delete then retrieve returns 404**
    - **Validates: Requirements 5.1, 3.2**
  - [x] 10.5 Write property test for invalid requests rejected with 400 (Property 4)
    - Use `@ParameterizedTest` with blank names and null/zero/negative prices for both POST and PUT
    - Assert 400 response and that product count is unchanged
    - **Property 4: Invalid requests are rejected with 400**
    - **Validates: Requirements 1.3, 1.4, 4.3, 4.4**
  - [x] 10.6 Write property test for non-existent id returns 404 (Property 5)
    - Use a large random id unlikely to exist; assert GET, PUT, and DELETE all return 404
    - **Property 5: Non-existent id returns 404**
    - **Validates: Requirements 3.2, 4.2, 5.2**
  - [x] 10.7 Write property test for get-all returns complete list (Property 6)
    - Insert N products (varied N including 0), call GET /api/products, assert array length equals N
    - **Property 6: Get all returns complete list**
    - **Validates: Requirements 2.1, 2.2**

- [x] 11. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at logical boundaries
- Property tests use `@ParameterizedTest` / `@MethodSource` (JUnit 5) as specified in the design
- Integration tests require a running Docker daemon for Testcontainers
