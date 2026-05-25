# Design Document: Product Condition Fields

## Overview

This design adds two new fields — `conditionType` (enum: NEW/USED) and `conditionRating` (integer 1–10) — to the Product entity and exposes them through all existing API layers. The implementation follows the existing layered architecture (controller → service → repository → entity) and uses the same patterns already established in the codebase: Lombok builders with defaults, Jakarta Bean Validation annotations, and Liquibase migrations.

## Architecture

No new architectural components are introduced. The change is additive across existing layers:

```
ProductRequest (+ conditionType, conditionRating with validation)
        │
        ▼
ProductController / PublicProductController (unchanged routing)
        │
        ▼
ProductServiceImpl (maps new fields in create/update/toResponse)
        │
        ▼
Product entity (+ conditionType, conditionRating with @Builder.Default)
        │
        ▼
PostgreSQL (Liquibase migration adds columns)
```

## Components and Interfaces

### 1. ConditionType Enum

A new Java enum in the `model` package.

```java
package com.example.products.model;

public enum ConditionType {
    NEW,
    USED
}
```

### 2. Product Entity Changes

Add two fields to `Product.java`:

```java
@Enumerated(EnumType.STRING)
@Column(name = "condition_type")
@Builder.Default
private ConditionType conditionType = ConditionType.USED;

@Column(name = "condition_rating")
@Builder.Default
private Integer conditionRating = 10;
```

The `@Enumerated(EnumType.STRING)` annotation stores the enum as its name string (`"NEW"` or `"USED"`) rather than an ordinal, making the column human-readable and safe against enum reordering.

### 3. ProductRequest DTO Changes

Add optional validated fields:

```java
private ConditionType conditionType;

@Min(1)
@Max(10)
private Integer conditionRating;
```

Both fields are nullable. When `null`, the service layer relies on the entity's `@Builder.Default` to supply defaults. The `@Min`/`@Max` annotations only fire when the value is non-null (Jakarta Validation skips null by default for these constraints).

### 4. Response DTO Changes

Add to both `AdminProductResponse` and `PublicProductResponse`:

```java
private ConditionType conditionType;
private Integer conditionRating;
```

No validation needed on response DTOs — they are outbound only.

### 5. Service Layer Changes

Update `ProductServiceImpl` mapping methods:

**`createAdmin` / `create`** — pass condition fields from request to builder:

```java
Product product = Product.builder()
        .name(request.getName())
        .description(request.getDescription())
        .price(request.getPrice())
        .active(request.isActive())
        .conditionType(request.getConditionType() != null
                ? request.getConditionType() : ConditionType.USED)
        .conditionRating(request.getConditionRating() != null
                ? request.getConditionRating() : 10)
        .build();
```

**`updateAdmin` / `update`** — set condition fields on existing entity:

```java
if (request.getConditionType() != null) {
    product.setConditionType(request.getConditionType());
}
if (request.getConditionRating() != null) {
    product.setConditionRating(request.getConditionRating());
}
```

**`toAdminResponse`** — include condition fields:

```java
.conditionType(product.getConditionType())
.conditionRating(product.getConditionRating())
```

**`toPublicResponse`** — include condition fields:

```java
.conditionType(product.getConditionType())
.conditionRating(product.getConditionRating())
```

### 6. Liquibase Migration

File: `src/main/resources/db/changelog/011-add-condition-fields-to-products.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 011-add-condition-fields-to-products
      author: developer
      changes:
        - addColumn:
            tableName: products
            columns:
              - column:
                  name: condition_type
                  type: VARCHAR(10)
                  constraints:
                    nullable: true
                  defaultValue: 'USED'
              - column:
                  name: condition_rating
                  type: INTEGER
                  defaultValueNumeric: 10
```

Register in `db.changelog-master.yaml`:

```yaml
- include:
    file: 011-add-condition-fields-to-products.yaml
    relativeToChangelogFile: true
```

### 7. H2 Seed Data

No changes required. H2 profile uses `ddl-auto: create-drop`, so Hibernate generates the schema from the entity definition (which includes the new fields with defaults). Existing seed data in `data.sql` can omit the new columns and the DB defaults will apply.

### API Interfaces

#### Request Body (ProductRequest)

```json
{
  "name": "Vintage Guitar",
  "description": "1965 Fender Stratocaster",
  "price": 2500.00,
  "active": true,
  "conditionType": "USED",
  "conditionRating": 7
}
```

Both `conditionType` and `conditionRating` are optional. Omitting them results in defaults (`USED`, `10`).

#### Response Body (AdminProductResponse / PublicProductResponse)

```json
{
  "id": 1,
  "name": "Vintage Guitar",
  "description": "1965 Fender Stratocaster",
  "price": 2500.00,
  "conditionType": "USED",
  "conditionRating": 7,
  "images": [],
  "tags": [],
  "active": true
}
```

#### Validation Error Response (400)

When `conditionRating` is out of range or `conditionType` is an invalid string:

```json
{
  "status": 400,
  "message": "Validation failed: conditionRating must be between 1 and 10",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

## Data Models

### Database Schema Change

| Column            | Type        | Nullable | Default  |
|-------------------|-------------|----------|----------|
| `condition_type`  | VARCHAR(10) | Yes      | `'USED'` |
| `condition_rating`| INTEGER     | Yes      | `10`     |

Both columns are nullable at the DB level to allow the migration to run without locking/backfilling existing rows — the DEFAULT clause handles existing rows transparently.

### Entity Field Mapping

| Java Field       | DB Column          | Type                | JPA Annotation                     |
|------------------|--------------------|---------------------|------------------------------------|
| `conditionType`  | `condition_type`   | ConditionType enum  | `@Enumerated(EnumType.STRING)`     |
| `conditionRating`| `condition_rating` | Integer             | `@Column(name="condition_rating")` |

## Error Handling

| Scenario                                    | HTTP Status | Mechanism                              |
|---------------------------------------------|-------------|----------------------------------------|
| `conditionRating < 1` or `> 10`            | 400         | `@Min(1) @Max(10)` + `@Valid`          |
| Invalid `conditionType` string (e.g. "OLD") | 400         | Jackson deserialization failure         |
| `conditionType` omitted                     | —           | Entity default `USED` applied          |
| `conditionRating` omitted                   | —           | Entity default `10` applied            |

Jackson's default behavior for unknown enum values is to throw `InvalidFormatException`, which Spring's `GlobalExceptionHandler` already catches and maps to a 400 response via `HttpMessageNotReadableException`.

## Testing Strategy

### Unit Tests (Mockito)

- **ProductServiceImpl**: Verify that `createAdmin`/`updateAdmin` correctly maps condition fields from request to entity and from entity to response DTOs.
- **Validation**: Verify `@Min`/`@Max` constraints trigger for out-of-range `conditionRating` values using MockMvc with `@Valid`.
- **Default application**: Verify that null condition fields in the request result in entity defaults.

### Property-Based Tests (jqwik)

- **Round-trip preservation**: Generate random valid `ConditionType` and `conditionRating` values, pass through the service create/read cycle, and assert the output matches the input.
- **Default invariant**: Generate random valid product requests with null condition fields and assert defaults are always applied.
- **Validation rejection**: Generate random integers outside [1, 10] and assert they are always rejected.

### Integration Tests (Testcontainers)

- **Migration verification**: Confirm the Liquibase migration applies cleanly and the columns exist with correct defaults.
- **End-to-end API**: POST/PUT/GET through the full stack and verify condition fields persist and return correctly.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Condition fields default application

*For any* valid product creation request that omits both `conditionType` and `conditionRating`, the resulting product entity and API response SHALL have `conditionType` equal to `USED` and `conditionRating` equal to `10`.

**Validates: Requirements 1.3, 1.4, 2.2, 2.3, 7.5, 7.6**

### Property 2: Condition fields round-trip preservation

*For any* valid `ConditionType` value and *for any* valid `conditionRating` integer in [1, 10], creating or updating a product with those values and then reading the product back SHALL return the same `conditionType` and `conditionRating` values in both admin and public responses.

**Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 5.1, 5.2, 5.3, 5.4, 7.1, 7.2, 7.3, 7.4**

### Property 3: Condition rating out-of-range rejection

*For any* integer value outside the range [1, 10], submitting a product creation or update request with that value as `conditionRating` SHALL result in a 400 Bad Request response, and the product state SHALL remain unchanged.

**Validates: Requirements 3.3, 3.4**

### Property 4: Invalid condition type rejection

*For any* string that is not a valid `ConditionType` enum name (i.e., not "NEW" and not "USED"), submitting a product creation or update request with that string as `conditionType` SHALL result in a 400 Bad Request response.

**Validates: Requirements 3.5**
