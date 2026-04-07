# Requirements Document

## Introduction

This feature adds a bulk product import endpoint to the products service. It accepts a CSV file upload containing product data (name, description, price, and image URL), and for each valid row creates a product via the existing `ProductService` and then associates an image via the existing `ProductImageService`. The endpoint is protected by JWT Bearer authentication and restricted to users holding the `ADMIN` or `MANAGER` role.

## Glossary

- **BulkImportController**: The REST controller that exposes the CSV upload endpoint.
- **BulkImportService**: The service that orchestrates parsing the CSV and delegating to `ProductService` and `ProductImageService`.
- **CSV_Parser**: The component responsible for reading and validating the structure of the uploaded CSV file.
- **ProductService**: The existing service with a `create(ProductRequest)` method that persists a product and returns a `ProductResponse` containing the generated `id`.
- **ProductImageService**: The existing service with an `addImages(Long productId, ImageRequest)` method that associates image URLs with a product.
- **ImportResult**: The response body returned after a bulk import, containing per-row success or failure details.
- **ImportRowResult**: A single entry in `ImportResult` describing the outcome (success or failure) for one CSV row.
- **JWT**: JSON Web Token used for stateless authentication; carries role claims.
- **Role**: An authorization claim embedded in the JWT; valid values for this feature are `ADMIN` and `MANAGER`.

---

## Requirements

### Requirement 1: Authenticated and Authorized Access

**User Story:** As a system administrator, I want the bulk import endpoint to be protected by JWT authentication and restricted to privileged roles, so that only authorized users can create products in bulk.

#### Acceptance Criteria

1. WHEN a request is made to `POST /api/products/import` without a `Authorization: Bearer <token>` header, THE BulkImportController SHALL return HTTP 401 Unauthorized.
2. WHEN a request is made to `POST /api/products/import` with a valid JWT that does not contain the `ADMIN` or `MANAGER` role, THE BulkImportController SHALL return HTTP 403 Forbidden.
3. WHEN a request is made to `POST /api/products/import` with a valid JWT containing the `ADMIN` role, THE BulkImportController SHALL process the request.
4. WHEN a request is made to `POST /api/products/import` with a valid JWT containing the `MANAGER` role, THE BulkImportController SHALL process the request.

---

### Requirement 2: CSV File Upload

**User Story:** As a manager, I want to upload a CSV file to the import endpoint, so that I can create multiple products and their images in a single request.

#### Acceptance Criteria

1. THE BulkImportController SHALL accept `multipart/form-data` requests with a file field named `file` at `POST /api/products/import`.
2. WHEN the uploaded file is not provided or the `file` field is absent, THE BulkImportController SHALL return HTTP 400 Bad Request with a descriptive error message.
3. WHEN the uploaded file has a content type other than `text/csv` or `application/octet-stream`, THE BulkImportController SHALL return HTTP 400 Bad Request with a descriptive error message.
4. WHEN the uploaded file is empty (zero bytes or contains only blank lines), THE BulkImportController SHALL return HTTP 400 Bad Request with a descriptive error message.

---

### Requirement 3: CSV Parsing

**User Story:** As a manager, I want the system to parse each row of the CSV file, so that product data is correctly extracted for creation.

#### Acceptance Criteria

1. THE CSV_Parser SHALL expect exactly four columns per row in the order: `Name`, `Description`, `Price`, `URL`.
2. THE CSV_Parser SHALL treat the first row as data (no header row is expected), consistent with the existing `import.csv` format.
3. WHEN a CSV row has fewer or more than four columns, THE CSV_Parser SHALL mark that row as failed with a descriptive parse error and continue processing remaining rows.
4. WHEN a CSV row has a blank `Name` field, THE CSV_Parser SHALL mark that row as failed with a descriptive validation error and continue processing remaining rows.
5. WHEN a CSV row has a `Price` field that is not a valid decimal number or is less than `0.01`, THE CSV_Parser SHALL mark that row as failed with a descriptive validation error and continue processing remaining rows.
6. WHEN a CSV row has a `URL` field that does not match the pattern `https?://.*`, THE CSV_Parser SHALL mark that row as failed with a descriptive validation error and continue processing remaining rows.
7. THE CSV_Parser SHALL treat a blank `Description` field as a valid empty description.

---

### Requirement 4: Per-Row Product and Image Creation

**User Story:** As a manager, I want each valid CSV row to create a product and its associated image, so that the bulk import populates both the product catalog and image records atomically per row.

#### Acceptance Criteria

1. WHEN a CSV row passes validation, THE BulkImportService SHALL call `ProductService.create(ProductRequest)` with the `Name`, `Description`, and `Price` from that row.
2. WHEN `ProductService.create` succeeds, THE BulkImportService SHALL call `ProductImageService.addImages(productId, ImageRequest)` using the generated product `id` and the `URL` from that row.
3. WHEN both `ProductService.create` and `ProductImageService.addImages` succeed for a row, THE BulkImportService SHALL record that row as successfully imported.
4. WHEN `ProductService.create` throws an exception for a row, THE BulkImportService SHALL record that row as failed with the exception message and continue processing remaining rows without rolling back previously successful rows.
5. WHEN `ProductImageService.addImages` throws an exception for a row after the product was already created, THE BulkImportService SHALL record that row as failed with the exception message and continue processing remaining rows.

---

### Requirement 5: Import Result Response

**User Story:** As a manager, I want to receive a detailed report after the import, so that I know which rows succeeded and which failed and why.

#### Acceptance Criteria

1. WHEN the import completes (regardless of partial failures), THE BulkImportController SHALL return HTTP 200 OK with an `ImportResult` body.
2. THE ImportResult SHALL contain a list of `ImportRowResult` entries, one per non-blank CSV row processed.
3. THE ImportRowResult for a successful row SHALL include the row number (1-based), a `SUCCESS` status, and the created product `id`.
4. THE ImportRowResult for a failed row SHALL include the row number (1-based), a `FAILED` status, and a descriptive error message.
5. THE ImportResult SHALL include a summary with the total count of rows processed, the count of successful rows, and the count of failed rows.

---

### Requirement 6: CSV Round-Trip Integrity

**User Story:** As a developer, I want the CSV parsing to be verifiable end-to-end, so that data read from a CSV row is faithfully passed to the service layer without loss or corruption.

#### Acceptance Criteria

1. FOR ALL valid CSV rows, THE BulkImportService SHALL pass the `Name`, `Description`, `Price`, and `URL` values to `ProductService` and `ProductImageService` exactly as parsed from the CSV without modification.
2. WHEN a CSV file is constructed from a set of valid product records and uploaded, THE BulkImportController SHALL produce an `ImportResult` where the count of successful rows equals the number of valid rows in the file (round-trip property).
