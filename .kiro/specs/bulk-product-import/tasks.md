# Implementation Plan: Bulk Product Import

## Overview

Add a `POST /api/products/import` endpoint that accepts a `multipart/form-data` CSV upload and creates products with associated images in bulk. A new `BulkImportController` delegates to a new `BulkImportService`, which parses the CSV inline and orchestrates calls to the existing `ProductService` and `ProductImageService`. Row-level failures are isolated — one bad row never aborts the rest.

## Tasks

- [x] 1. Create response DTOs and BulkImportService interface
  - Create `RowStatus` enum in `model/` with values `SUCCESS` and `FAILED`
  - Create `ImportRowResult` in `model/` with fields: `int rowNumber`, `RowStatus status`, `Long productId`, `String errorMessage`; use Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
  - Create `ImportResult` in `model/` with fields: `int totalRows`, `int successCount`, `int failedCount`, `List<ImportRowResult> rows`; use Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
  - Create `BulkImportService` interface in `service/` with method `ImportResult importProducts(MultipartFile file) throws IOException`
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 2. Implement BulkImportServiceImpl
  - [x] 2.1 Create `BulkImportServiceImpl` in `service/` annotated with `@Service`
    - Inject `ProductService` and `ProductImageService`
    - Read the `MultipartFile` via `new BufferedReader(new InputStreamReader(file.getInputStream()))`
    - For each non-blank line call `String.split(",", -1)` to get exactly 4 columns; record `FAILED` if column count != 4
    - Validate: name non-blank, price parseable as `BigDecimal` and >= 0.01, URL matches `https?://.*`; record `FAILED` with descriptive message on any violation
    - Treat blank description as valid empty string
    - For valid rows: call `ProductService.create(ProductRequest)`, then `ProductImageService.addImages(productId, new ImageRequest(List.of(url)))`; catch any exception per call and record `FAILED`
    - Build `ImportResult` with `totalRows`, `successCount`, `failedCount`, and the `rows` list; row numbers are 1-based
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 4.1, 4.2, 4.3, 4.4, 4.5, 5.2, 5.3, 5.4, 5.5, 6.1_

  - [ ]* 2.2 Write unit tests for `BulkImportServiceImpl` (`BulkImportServiceImplTest`)
    - Use `@ExtendWith(MockitoExtension.class)`, mock `ProductService` and `ProductImageService`
    - Happy path: single valid row → `create` and `addImages` called with correct args, result has 1 SUCCESS with non-null `productId`
    - Blank description row → treated as valid, description passed as empty string
    - Row with wrong column count (3 cols, 5 cols) → FAILED, processing continues
    - Row with blank name → FAILED, processing continues
    - Row with invalid price (non-numeric, `"0.00"`, `"-1"`) → FAILED, processing continues
    - Row with invalid URL (no scheme, `ftp://`, blank) → FAILED, processing continues
    - `ProductService.create` throws → row FAILED, next row still processed
    - `ProductImageService.addImages` throws → row FAILED, next row still processed
    - Summary invariant: `totalRows == successCount + failedCount`
    - Row numbers are 1-based and match CSV line position
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 4.1, 4.2, 4.3, 4.4, 4.5, 5.2, 5.3, 5.4, 5.5_

- [x] 3. Create BulkImportController
  - Create `BulkImportController` in `controller/` annotated with `@RestController @RequestMapping("/api/products")`
  - Add `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")` on the handler method
  - Handler: `POST /import`, consumes `multipart/form-data`, `@RequestParam("file") MultipartFile file`
  - File-level validation before delegating: return `400` with `ErrorResponse` if file is null/empty, content type is not `text/csv` or `application/octet-stream`, or file has zero bytes / only blank lines
  - Delegate to `BulkImportService.importProducts(file)` and return `200 OK` with `ImportResult`
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 5.1_

  - [ ]* 3.1 Write `@WebMvcTest` tests for `BulkImportController` (`BulkImportControllerTest`)
    - Mock `BulkImportService` with `@MockitoBean`
    - No JWT → `401`
    - JWT without `ADMIN`/`MANAGER` role → `403`
    - Missing `file` part → `400`
    - Wrong content type (e.g. `image/png`) → `400`
    - Empty file (0 bytes) → `400`
    - Valid CSV file with ADMIN JWT → `200` with `ImportResult` body
    - Valid CSV file with MANAGER JWT → `200` with `ImportResult` body
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.2, 2.3, 2.4, 5.1_

- [x] 4. Checkpoint — Ensure all unit and WebMvcTest tests pass
  - Run `./mvnw test -Dtest='!*IT,!*IntegrationTest'` and verify all tests are green. Ask the user if questions arise.

- [x] 5. Write property-based tests (`BulkImportPropertyIT`)
  - Create `BulkImportPropertyIT` in `src/test/java/com/example/products/` extending `AbstractIntegrationTest`
  - Use `@Property(tries = 100)` on each test method; class is named `*IT` so it is skipped without Docker

  - [x]* 5.1 Write property test for Property 1: Unauthenticated requests are rejected
    - `// Feature: bulk-product-import, Property 1: Unauthenticated requests are rejected`
    - Generate requests without `Authorization` header; assert all return `401`
    - **Property 1: Unauthenticated requests are rejected**
    - **Validates: Requirements 1.1**

  - [x]* 5.2 Write property test for Property 2: Insufficient role is rejected
    - `// Feature: bulk-product-import, Property 2: Insufficient role is rejected`
    - Generate valid JWTs with random roles excluding `ADMIN`/`MANAGER`; assert all return `403`
    - **Property 2: Insufficient role is rejected**
    - **Validates: Requirements 1.2**

  - [x]* 5.3 Write property test for Property 3: Authorized roles allow processing
    - `// Feature: bulk-product-import, Property 3: Authorized roles allow processing`
    - Generate JWTs with `ADMIN` or `MANAGER` role and a valid CSV; assert response is not `401` or `403`
    - **Property 3: Authorized roles allow processing**
    - **Validates: Requirements 1.3, 1.4**

  - [x]* 5.4 Write property test for Property 4: Invalid content type is rejected
    - `// Feature: bulk-product-import, Property 4: Invalid content type is rejected`
    - Generate random content type strings excluding `text/csv` and `application/octet-stream`; assert `400`
    - **Property 4: Invalid content type is rejected**
    - **Validates: Requirements 2.3**

  - [x]* 5.5 Write property test for Property 5: Invalid rows are marked FAILED and processing continues
    - `// Feature: bulk-product-import, Property 5: Invalid rows are marked FAILED and processing continues`
    - Generate CSVs mixing valid rows and invalid rows (wrong column count, blank name, bad price, bad URL); assert each invalid row has `FAILED` status with non-blank error message and each valid row is processed
    - **Property 5: Invalid rows are marked FAILED and processing continues**
    - **Validates: Requirements 3.3, 3.4, 3.5, 3.6**

  - [x]* 5.6 Write property test for Property 6: Blank description is treated as valid
    - `// Feature: bulk-product-import, Property 6: Blank description is treated as valid`
    - Generate CSV rows with blank/empty description; assert row is `SUCCESS` and created product has empty or null description
    - **Property 6: Blank description is treated as valid**
    - **Validates: Requirements 3.7**

  - [x]* 5.7 Write property test for Property 7: Row-level failure isolation
    - `// Feature: bulk-product-import, Property 7: Row-level failure isolation`
    - Generate CSVs where specific rows are designed to fail (e.g. duplicate constraint or injected mock); assert failing rows are `FAILED` and all other valid rows are `SUCCESS`
    - **Property 7: Row-level failure isolation**
    - **Validates: Requirements 4.4, 4.5**

  - [x]* 5.8 Write property test for Property 8: Import always returns 200 with ImportResult
    - `// Feature: bulk-product-import, Property 8: Import always returns 200 with ImportResult`
    - Generate any valid CSV upload (with ADMIN JWT); assert response is always `200 OK` with a non-null `ImportResult` body
    - **Property 8: Import always returns 200 with ImportResult**
    - **Validates: Requirements 5.1**

  - [x]* 5.9 Write property test for Property 9: Result count invariant
    - `// Feature: bulk-product-import, Property 9: Result count invariant`
    - Generate CSVs with N non-blank rows; assert result contains exactly N `ImportRowResult` entries and `totalRows == successCount + failedCount == N`
    - **Property 9: Result count invariant**
    - **Validates: Requirements 5.2, 5.5**

  - [x]* 5.10 Write property test for Property 10: ImportRowResult structure completeness
    - `// Feature: bulk-product-import, Property 10: ImportRowResult structure completeness`
    - Generate mixed success/failure CSVs; assert `SUCCESS` rows have non-null `productId` and null `errorMessage`; assert `FAILED` rows have non-blank `errorMessage` and null `productId`
    - **Property 10: ImportRowResult structure completeness**
    - **Validates: Requirements 5.3, 5.4**

  - [x]* 5.11 Write property test for Property 11: CSV round-trip data fidelity
    - `// Feature: bulk-product-import, Property 11: CSV round-trip data fidelity`
    - Generate random valid product records (name, description, price, URL); write to CSV; POST with ADMIN JWT; assert all rows `SUCCESS`; GET each created product and assert name, description, price match; GET images and assert URL matches
    - **Property 11: CSV round-trip data fidelity**
    - **Validates: Requirements 6.1, 6.2**

- [x] 6. Final checkpoint — Ensure all tests pass
  - Run `./mvnw test -Dtest='!*IT,!*IntegrationTest'` and verify everything is green. Ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Property-based tests (task 5) require Docker — skip entirely if Docker is unavailable
- `@WebMvcTest` tests (task 3.1) use a mocked `JwtDecoder` and do not require Docker
- Unit tests (task 2.2) use Mockito only — no Docker needed
- Each task references specific requirements for traceability
- Checkpoints use `./mvnw test -Dtest='!*IT,!*IntegrationTest'` to avoid Docker-dependent tests
