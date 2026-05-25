# Implementation Plan: Product Condition Fields

## Overview

Add `conditionType` (enum: NEW/USED) and `conditionRating` (integer 1–10) fields to the Product entity, expose them through admin and public API responses, accept them in create/update requests with validation, and add a Liquibase migration for the new database columns. Implementation follows the existing layered architecture and patterns.

## Tasks

- [x] 1. Create ConditionType enum and update Product entity
  - [x] 1.1 Create `ConditionType` enum in the model package
    - Create `src/main/java/com/example/products/model/ConditionType.java` with values `NEW` and `USED`
    - _Requirements: 1.1_

  - [x] 1.2 Add condition fields to the `Product` entity
    - Add `conditionType` field with `@Enumerated(EnumType.STRING)`, `@Column(name = "condition_type")`, and `@Builder.Default` defaulting to `ConditionType.USED`
    - Add `conditionRating` field of type `Integer` with `@Column(name = "condition_rating")` and `@Builder.Default` defaulting to `10`
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2_

- [x] 2. Update DTOs (request and response)
  - [x] 2.1 Add condition fields to `ProductRequest`
    - Add optional `conditionType` field of type `ConditionType` (nullable, no `@NotNull`)
    - Add optional `conditionRating` field of type `Integer` with `@Min(1)` and `@Max(10)` annotations
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [x] 2.2 Add condition fields to `AdminProductResponse`
    - Add `conditionType` field of type `ConditionType`
    - Add `conditionRating` field of type `Integer`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

  - [x] 2.3 Add condition fields to `PublicProductResponse`
    - Add `conditionType` field of type `ConditionType`
    - Add `conditionRating` field of type `Integer`
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 3. Update service layer
  - [x] 3.1 Update `ProductServiceImpl` create methods to map condition fields
    - In `createAdmin` and `create`, pass `conditionType` and `conditionRating` from request to the Product builder, falling back to defaults (`ConditionType.USED` and `10`) when null
    - _Requirements: 7.1, 7.2, 7.5, 7.6, 1.4, 2.3_

  - [x] 3.2 Update `ProductServiceImpl` update methods to map condition fields
    - In `updateAdmin` and `update`, set `conditionType` and `conditionRating` on the existing entity when the request values are non-null
    - _Requirements: 7.3, 7.4_

  - [x] 3.3 Update `ProductServiceImpl` response mapping methods
    - In `toAdminResponse`, include `.conditionType(product.getConditionType())` and `.conditionRating(product.getConditionRating())`
    - In `toPublicResponse`, include `.conditionType(product.getConditionType())` and `.conditionRating(product.getConditionRating())`
    - In `toResponse` (legacy), include the same condition field mappings
    - _Requirements: 4.1–4.8, 5.1–5.4_

- [x] 4. Checkpoint - Verify compilation
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Create Liquibase migration
  - [x] 5.1 Create migration file `011-add-condition-fields-to-products.yaml`
    - Create `src/main/resources/db/changelog/011-add-condition-fields-to-products.yaml`
    - Add `condition_type` column: VARCHAR(10), nullable, default `'USED'`
    - Add `condition_rating` column: INTEGER, default `10`
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 5.2 Register migration in `db.changelog-master.yaml`
    - Append include entry for `011-add-condition-fields-to-products.yaml` to the master changelog
    - _Requirements: 6.4_

- [x] 6. Write unit tests for condition fields
  - [x] 6.1 Write unit tests for `ProductServiceImpl` condition field mapping
    - Test that `createAdmin` maps provided `conditionType` and `conditionRating` to entity and response
    - Test that `createAdmin` applies defaults when condition fields are null
    - Test that `updateAdmin` updates condition fields when provided
    - Test that `updateAdmin` preserves existing condition fields when request values are null
    - _Requirements: 1.3, 1.4, 2.2, 2.3, 7.1–7.6_

  - [x] 6.2 Write property test: Condition fields default application (Property 1)
    - **Property 1: Condition fields default application**
    - For any valid product creation request omitting both `conditionType` and `conditionRating`, assert the response has `conditionType == USED` and `conditionRating == 10`
    - Use jqwik `@Property` with `@ForAll` to generate random valid product names/prices
    - **Validates: Requirements 1.3, 1.4, 2.2, 2.3, 7.5, 7.6**

  - [x] 6.3 Write property test: Condition fields round-trip preservation (Property 2)
    - **Property 2: Condition fields round-trip preservation**
    - For any valid `ConditionType` and any integer in [1, 10], create a product with those values and assert the response returns the same values
    - Use jqwik `@Property` with `@ForAll` to generate random valid condition combinations
    - **Validates: Requirements 4.1–4.8, 5.1–5.4, 7.1–7.4**

  - [x] 6.4 Write property test: Condition rating out-of-range rejection (Property 3)
    - **Property 3: Condition rating out-of-range rejection**
    - For any integer outside [1, 10], assert that a product creation request with that `conditionRating` is rejected with 400
    - Use jqwik `@Property` with `@ForAll` integers filtered to `< 1 || > 10`
    - **Validates: Requirements 3.3, 3.4**

- [x] 7. Write integration tests for condition fields
  - [x] 7.1 Write integration test for condition fields end-to-end
    - Extend `AbstractIntegrationTest` to create `ProductConditionFieldsIT`
    - Test POST with condition fields → GET returns same values (admin and public endpoints)
    - Test POST without condition fields → GET returns defaults
    - Test PUT updates condition fields
    - Test validation rejection for out-of-range `conditionRating`
    - Test invalid `conditionType` string returns 400
    - Verify Liquibase migration applies cleanly (implicit via Testcontainers startup)
    - **Property 4: Invalid condition type rejection**
    - **Validates: Requirements 3.5, 6.1–6.4**

- [x] 8. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The project uses Java 17, Spring Boot 4.0.5, Maven, Lombok, jqwik for property tests, and Testcontainers for integration tests
- H2 profile uses `ddl-auto: create-drop` so no migration needed for local dev — Hibernate generates schema from entity

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "5.1"] },
    { "id": 3, "tasks": ["3.1", "3.2", "3.3", "5.2"] },
    { "id": 4, "tasks": ["6.1", "6.2", "6.3", "6.4"] },
    { "id": 5, "tasks": ["7.1"] }
  ]
}
```
