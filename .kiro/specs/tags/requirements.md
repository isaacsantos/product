# Requirements Document

## Introduction

This document defines the requirements for the Tags feature in the `products` Spring Boot application. The feature introduces a `Tag` entity that can be associated with `Product` entities in a many-to-many relationship, allowing products to be classified with labels such as "Para mamá" or "Para la novia". It exposes a RESTful CRUD API under `/api/tags` and an association endpoint on `/api/products/{id}/tags`.

## Glossary

- **Tag**: A label entity with a unique name used to classify products
- **TagRequest**: Inbound DTO carrying the `name` field for create/update operations
- **TagResponse**: Outbound DTO returned to the client, including `id` and `name`
- **product_tag**: The join table that stores the many-to-many relationship between products and tags
- **TagController**: The `@RestController` handling HTTP requests under `/api/tags`
- **TagService / TagServiceImpl**: The service layer encapsulating tag business logic
- **TagRepository**: Spring Data JPA repository for `Tag` entities
- **TagNotFoundException**: Exception thrown when a tag id does not exist
- **setTags**: The operation that replaces the full set of tags associated with a product

---

## Requirements

### Requirement 1: Create a Tag

**User Story:** As an API client, I want to create a new tag, so that I can define labels to classify products.

#### Acceptance Criteria

1. WHEN a `POST /api/tags` request is received with a valid `TagRequest` body, THE Controller SHALL persist the tag and return a `201 Created` response containing a `TagResponse` with the generated `id` and submitted `name`.
2. WHEN a `POST /api/tags` request is received with a blank `name`, THE GlobalExceptionHandler SHALL return a `400 Bad Request` response with field-level validation error messages.
3. WHEN a `POST /api/tags` request is received with a `name` exceeding 100 characters, THE GlobalExceptionHandler SHALL return a `400 Bad Request` response.

---

### Requirement 2: Retrieve All Tags

**User Story:** As an API client, I want to retrieve all tags, so that I can display available labels.

#### Acceptance Criteria

1. WHEN a `GET /api/tags` request is received, THE Controller SHALL return a `200 OK` response containing a JSON array of all `TagResponse` objects.
2. WHILE no tags exist, THE Controller SHALL return a `200 OK` response with an empty JSON array.

---

### Requirement 3: Retrieve a Single Tag

**User Story:** As an API client, I want to retrieve a tag by its id, so that I can inspect a specific label.

#### Acceptance Criteria

1. WHEN a `GET /api/tags/{id}` request is received and a tag with that `id` exists, THE Controller SHALL return a `200 OK` response with the matching `TagResponse`.
2. WHEN a `GET /api/tags/{id}` request is received and no tag with that `id` exists, THE GlobalExceptionHandler SHALL return a `404 Not Found` response with an `ErrorResponse` identifying the missing `id`.

---

### Requirement 4: Update a Tag

**User Story:** As an API client, I want to update an existing tag, so that I can correct or rename a label.

#### Acceptance Criteria

1. WHEN a `PUT /api/tags/{id}` request is received with a valid `TagRequest` and the tag exists, THE Controller SHALL update the tag name and return a `200 OK` response with the updated `TagResponse`.
2. WHEN a `PUT /api/tags/{id}` request is received and no tag with that `id` exists, THE GlobalExceptionHandler SHALL return a `404 Not Found` response.
3. WHEN a `PUT /api/tags/{id}` request is received with a blank or oversized `name`, THE GlobalExceptionHandler SHALL return a `400 Bad Request` response.

---

### Requirement 5: Delete a Tag

**User Story:** As an API client, I want to delete a tag, so that I can remove labels that are no longer relevant.

#### Acceptance Criteria

1. WHEN a `DELETE /api/tags/{id}` request is received and the tag exists, THE Controller SHALL remove the tag and return a `204 No Content` response.
2. WHEN a `DELETE /api/tags/{id}` request is received and no tag with that `id` exists, THE GlobalExceptionHandler SHALL return a `404 Not Found` response.

---

### Requirement 6: Associate Tags with a Product

**User Story:** As an API client, I want to assign a set of tags to a product, so that the product can be classified with one or more labels.

#### Acceptance Criteria

1. WHEN a `PUT /api/products/{id}/tags` request is received with a JSON array of tag ids and the product exists, THE Controller SHALL replace the product's current tag set with the provided tags and return a `200 OK` response with the updated `ProductResponse` including the new tag list.
2. WHEN a `PUT /api/products/{id}/tags` request is received with an empty array, THE Controller SHALL remove all tags from the product and return a `200 OK` response.
3. WHEN a `PUT /api/products/{id}/tags` request is received and the product does not exist, THE GlobalExceptionHandler SHALL return a `404 Not Found` response.
4. WHEN a `PUT /api/products/{id}/tags` request is received and any tag id in the array does not exist, THE GlobalExceptionHandler SHALL return a `404 Not Found` response.

---

### Requirement 7: Tags Included in Product Responses

**User Story:** As an API client, I want product responses to include their associated tags, so that I can display classification information alongside product data.

#### Acceptance Criteria

1. THE `ProductResponse` SHALL include a `tags` field containing a list of `TagResponse` objects for all tags currently associated with the product.
2. WHEN a product has no tags, THE `tags` field SHALL be an empty array.

---

### Requirement 8: Tag Data Model

**User Story:** As a developer, I want a well-defined tag data model, so that the API consistently represents tag information.

#### Acceptance Criteria

1. THE `Tag` entity SHALL contain: `id` (auto-generated Long primary key) and `name` (non-null, unique, max 100 characters).
2. THE `TagRequest` SHALL enforce that `name` is non-blank and at most 100 characters via Bean Validation.
3. THE `TagResponse` SHALL include `id` and `name`.

---

### Requirement 9: Database Schema

**User Story:** As a developer, I want the tag schema managed by Liquibase, so that changes are versioned and reproducible.

#### Acceptance Criteria

1. THE Liquibase changelog SHALL define a `tags` table with columns: `id` (BIGSERIAL primary key), `name` (VARCHAR(100) NOT NULL UNIQUE).
2. THE Liquibase changelog SHALL define a `product_tag` join table with columns: `product_id` (BIGINT FK → products.id) and `tag_id` (BIGINT FK → tags.id), with a composite primary key on both columns.
3. WHEN the application starts, Liquibase SHALL apply both changesets before the application accepts requests.
