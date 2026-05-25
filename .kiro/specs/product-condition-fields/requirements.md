# Requirements Document

## Introduction

This feature adds two new fields to the Product entity: Condition Type (NEW or USED) and Condition Rating (1–10). Both fields are optional in requests, carry defaults at the entity level, and are exposed in all public and admin API responses and accepted in create/update requests.

## Glossary

- **Product_Service**: The Spring Boot application responsible for managing Product resources via RESTful APIs.
- **Product_Entity**: The JPA entity mapped to the `products` database table representing a product.
- **ConditionType_Enum**: A Java enum with values `NEW` and `USED`, persisted as a VARCHAR string in the database.
- **Condition_Rating**: An integer field on the Product entity representing product condition on a scale of 1 to 10.
- **Admin_API**: The authenticated REST API served under `/admin/api/products` returning `AdminProductResponse` DTOs.
- **Public_API**: The unauthenticated REST API served under `/api/products` returning `PublicProductResponse` DTOs.
- **ProductRequest_DTO**: The inbound validated DTO used for creating and updating products.

## Requirements

### Requirement 1: Condition Type Field on Product Entity

**User Story:** As a product manager, I want each product to have an optional condition type indicating whether it is new or used, so that buyers can filter and understand product condition at a glance.

#### Acceptance Criteria

1. THE Product_Entity SHALL include a `conditionType` field of type ConditionType_Enum.
2. THE Product_Entity SHALL store the `conditionType` field as a VARCHAR column using JPA `@Enumerated(EnumType.STRING)`.
3. THE Product_Entity SHALL default the `conditionType` field to `USED` when no value is provided, using Lombok `@Builder.Default`.
4. WHEN a product is persisted without an explicit `conditionType` value, THE Product_Service SHALL store the value `USED` in the database.

### Requirement 2: Condition Rating Field on Product Entity

**User Story:** As a product manager, I want each product to have a condition rating from 1 to 10, so that buyers can assess the quality of used products numerically.

#### Acceptance Criteria

1. THE Product_Entity SHALL include a `conditionRating` field of type Integer.
2. THE Product_Entity SHALL default the `conditionRating` field to `10` when no value is provided, using Lombok `@Builder.Default`.
3. WHEN a product is persisted without an explicit `conditionRating` value, THE Product_Service SHALL store the value `10` in the database.

### Requirement 3: Validation of Condition Fields in ProductRequest

**User Story:** As a developer, I want condition fields validated on input, so that invalid data is rejected before reaching the database.

#### Acceptance Criteria

1. THE ProductRequest_DTO SHALL accept an optional `conditionType` field of type ConditionType_Enum that is nullable.
2. THE ProductRequest_DTO SHALL accept an optional `conditionRating` field of type Integer that is nullable.
3. WHEN a `conditionRating` value is provided, THE Product_Service SHALL reject the request with a 400 status if the value is less than 1.
4. WHEN a `conditionRating` value is provided, THE Product_Service SHALL reject the request with a 400 status if the value is greater than 10.
5. WHEN an invalid `conditionType` value is provided in the JSON payload, THE Product_Service SHALL reject the request with a 400 status.

### Requirement 4: Condition Fields in Admin API Responses

**User Story:** As an admin user, I want to see condition type and condition rating in product responses, so that I can manage product condition data through the admin interface.

#### Acceptance Criteria

1. WHEN the Admin_API returns a product via GET `/admin/api/products`, THE Product_Service SHALL include the `conditionType` field in the AdminProductResponse.
2. WHEN the Admin_API returns a product via GET `/admin/api/products/{id}`, THE Product_Service SHALL include the `conditionType` field in the AdminProductResponse.
3. WHEN the Admin_API returns a product via POST `/admin/api/products`, THE Product_Service SHALL include the `conditionType` field in the AdminProductResponse.
4. WHEN the Admin_API returns a product via PUT `/admin/api/products/{id}`, THE Product_Service SHALL include the `conditionType` field in the AdminProductResponse.
5. WHEN the Admin_API returns a product via GET `/admin/api/products`, THE Product_Service SHALL include the `conditionRating` field in the AdminProductResponse.
6. WHEN the Admin_API returns a product via GET `/admin/api/products/{id}`, THE Product_Service SHALL include the `conditionRating` field in the AdminProductResponse.
7. WHEN the Admin_API returns a product via POST `/admin/api/products`, THE Product_Service SHALL include the `conditionRating` field in the AdminProductResponse.
8. WHEN the Admin_API returns a product via PUT `/admin/api/products/{id}`, THE Product_Service SHALL include the `conditionRating` field in the AdminProductResponse.

### Requirement 5: Condition Fields in Public API Responses

**User Story:** As a public user, I want to see condition type and condition rating in product listings, so that I can assess product condition before purchasing.

#### Acceptance Criteria

1. WHEN the Public_API returns a product via GET `/api/products`, THE Product_Service SHALL include the `conditionType` field in the PublicProductResponse.
2. WHEN the Public_API returns a product via GET `/api/products/{id}`, THE Product_Service SHALL include the `conditionType` field in the PublicProductResponse.
3. WHEN the Public_API returns a product via GET `/api/products`, THE Product_Service SHALL include the `conditionRating` field in the PublicProductResponse.
4. WHEN the Public_API returns a product via GET `/api/products/{id}`, THE Product_Service SHALL include the `conditionRating` field in the PublicProductResponse.

### Requirement 6: Database Migration for Condition Fields

**User Story:** As a developer, I want the new columns added via a Liquibase migration, so that the schema change is versioned and repeatable.

#### Acceptance Criteria

1. THE Product_Service SHALL add a Liquibase changeset file named `011-add-condition-fields-to-products.yaml` to the changelog directory.
2. THE Product_Service SHALL add a `condition_type` column of type VARCHAR that is nullable with a default value of `'USED'` to the `products` table.
3. THE Product_Service SHALL add a `condition_rating` column of type INTEGER with a default value of `10` to the `products` table.
4. THE Product_Service SHALL register the new changeset in the `db.changelog-master.yaml` master changelog.

### Requirement 7: Condition Fields Accepted in Create and Update Requests

**User Story:** As an admin user, I want to set condition type and condition rating when creating or updating a product, so that I can manage product condition data.

#### Acceptance Criteria

1. WHEN a POST request to `/admin/api/products` includes a `conditionType` value, THE Product_Service SHALL persist the provided ConditionType_Enum value on the Product_Entity.
2. WHEN a POST request to `/admin/api/products` includes a `conditionRating` value, THE Product_Service SHALL persist the provided integer value on the Product_Entity.
3. WHEN a PUT request to `/admin/api/products/{id}` includes a `conditionType` value, THE Product_Service SHALL update the Product_Entity with the provided ConditionType_Enum value.
4. WHEN a PUT request to `/admin/api/products/{id}` includes a `conditionRating` value, THE Product_Service SHALL update the Product_Entity with the provided integer value.
5. WHEN a POST request to `/admin/api/products` omits `conditionType`, THE Product_Service SHALL apply the default value of `USED`.
6. WHEN a POST request to `/admin/api/products` omits `conditionRating`, THE Product_Service SHALL apply the default value of `10`.
