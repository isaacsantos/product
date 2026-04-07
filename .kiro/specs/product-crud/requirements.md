# Requirements Document

## Introduction

This document defines the requirements for the Product CRUD feature in the `products` Spring Boot application. The feature exposes a RESTful API under `/api/products` that allows clients to create, read, update, and delete `Product` resources. Products are persisted in a PostgreSQL database managed via Liquibase migrations. All input is validated using Bean Validation, and errors are handled centrally with consistent HTTP status codes and response bodies.

## Glossary

- **API**: The HTTP REST interface exposed by the application at `/api/products`
- **Controller**: The `ProductController` Spring `@RestController` that handles HTTP requests
- **Service**: The `ProductService` / `ProductServiceImpl` that encapsulates business logic and DTO mapping
- **Repository**: The `ProductRepository` Spring Data JPA interface that manages persistence
- **Product**: The core domain entity with fields `id`, `name`, `description`, and `price`
- **ProductRequest**: The inbound DTO carrying `name`, `description`, and `price` from the client
- **ProductResponse**: The outbound DTO returned to the client, including the generated `id`
- **ErrorResponse**: The standard error body containing `status`, `message`, and `timestamp`
- **GlobalExceptionHandler**: The `@RestControllerAdvice` component that maps exceptions to HTTP error responses
- **Liquibase**: The database migration tool that manages the `products` table schema

---

## Requirements

### Requirement 1: Create a Product

**User Story:** As an API client, I want to create a new product, so that I can add items to the product catalogue.

#### Acceptance Criteria

1. WHEN a `POST /api/products` request is received with a valid `ProductRequest` body, THE Controller SHALL persist the product and return a `201 Created` response containing a `ProductResponse` with all submitted fields and a generated `id`.
2. WHEN a product is created, THE Service SHALL assign a unique `id` to the product that is not shared by any other product.
3. WHEN a `POST /api/products` request is received with a blank `name`, THE GlobalExceptionHandler SHALL return a `400 Bad Request` response with field-level validation error messages.
4. WHEN a `POST /api/products` request is received with a `null` or non-positive `price`, THE GlobalExceptionHandler SHALL return a `400 Bad Request` response with field-level validation error messages.
5. WHEN a `POST /api/products` request is received without a `description`, THE Controller SHALL accept the request and treat `description` as `null`.

---

### Requirement 2: Retrieve All Products

**User Story:** As an API client, I want to retrieve all products, so that I can display the full product catalogue.

#### Acceptance Criteria

1. WHEN a `GET /api/products` request is received, THE Controller SHALL return a `200 OK` response containing a JSON array of `ProductResponse` objects for every product in the database.
2. WHILE no products exist in the database, THE Controller SHALL return a `200 OK` response with an empty JSON array.

---

### Requirement 3: Retrieve a Single Product

**User Story:** As an API client, I want to retrieve a product by its id, so that I can display or inspect a specific item.

#### Acceptance Criteria

1. WHEN a `GET /api/products/{id}` request is received and a product with that `id` exists, THE Controller SHALL return a `200 OK` response containing the matching `ProductResponse`.
2. WHEN a `GET /api/products/{id}` request is received and no product with that `id` exists, THE GlobalExceptionHandler SHALL return a `404 Not Found` response containing an `ErrorResponse` with `status` 404 and a message identifying the missing `id`.

---

### Requirement 4: Update a Product

**User Story:** As an API client, I want to update an existing product, so that I can correct or change product information.

#### Acceptance Criteria

1. WHEN a `PUT /api/products/{id}` request is received with a valid `ProductRequest` body and a product with that `id` exists, THE Controller SHALL replace all fields of the product with the supplied values and return a `200 OK` response containing the updated `ProductResponse`.
2. WHEN a `PUT /api/products/{id}` request is received and no product with that `id` exists, THE GlobalExceptionHandler SHALL return a `404 Not Found` response containing an `ErrorResponse` with `status` 404 and a message identifying the missing `id`.
3. WHEN a `PUT /api/products/{id}` request is received with a blank `name`, THE GlobalExceptionHandler SHALL return a `400 Bad Request` response with field-level validation error messages.
4. WHEN a `PUT /api/products/{id}` request is received with a `null` or non-positive `price`, THE GlobalExceptionHandler SHALL return a `400 Bad Request` response with field-level validation error messages.

---

### Requirement 5: Delete a Product

**User Story:** As an API client, I want to delete a product by its id, so that I can remove items that are no longer available.

#### Acceptance Criteria

1. WHEN a `DELETE /api/products/{id}` request is received and a product with that `id` exists, THE Controller SHALL remove the product from the database and return a `204 No Content` response with no body.
2. WHEN a `DELETE /api/products/{id}` request is received and no product with that `id` exists, THE GlobalExceptionHandler SHALL return a `404 Not Found` response containing an `ErrorResponse` with `status` 404 and a message identifying the missing `id`.

---

### Requirement 6: Product Data Model

**User Story:** As a developer, I want a well-defined product data model, so that the API consistently represents product information.

#### Acceptance Criteria

1. THE Product SHALL contain the fields: `id` (auto-generated Long primary key), `name` (non-null, non-blank String), `description` (nullable String), and `price` (non-null BigDecimal greater than or equal to `0.01`).
2. THE ProductRequest SHALL enforce that `name` is non-blank and `price` is non-null and at least `0.01` via Bean Validation annotations.
3. THE ProductResponse SHALL include all four fields: `id`, `name`, `description`, and `price`.

---

### Requirement 7: Database Schema Management

**User Story:** As a developer, I want the database schema to be managed by Liquibase, so that schema changes are versioned and reproducible.

#### Acceptance Criteria

1. WHEN the application starts, THE Liquibase SHALL apply any pending changelog entries to create or migrate the `products` table.
2. THE Liquibase changelog SHALL define a `products` table with columns: `id` (BIGSERIAL primary key), `name` (VARCHAR NOT NULL), `description` (TEXT, nullable), and `price` (NUMERIC NOT NULL).

---

### Requirement 8: Centralised Error Handling

**User Story:** As an API client, I want consistent error responses, so that I can reliably handle failures in my application.

#### Acceptance Criteria

1. WHEN a `ProductNotFoundException` is raised, THE GlobalExceptionHandler SHALL return a `404 Not Found` response with an `ErrorResponse` body containing `status`, `message`, and `timestamp`.
2. WHEN a `MethodArgumentNotValidException` is raised, THE GlobalExceptionHandler SHALL return a `400 Bad Request` response with field-level validation error details.
3. IF an unexpected runtime exception occurs, THEN THE GlobalExceptionHandler SHALL return a `500 Internal Server Error` response with a generic `ErrorResponse` body.

---

### Requirement 9: Layered Architecture

**User Story:** As a developer, I want a clean separation between the HTTP, business logic, and persistence layers, so that the codebase is maintainable and testable.

#### Acceptance Criteria

1. THE Controller SHALL delegate all business logic to the Service and perform no direct repository access.
2. THE Service SHALL encapsulate all DTO-to-entity mapping and business rule enforcement.
3. THE Repository SHALL extend `JpaRepository<Product, Long>` and provide all persistence operations through Spring Data JPA.
