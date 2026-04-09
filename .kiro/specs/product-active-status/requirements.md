# Requirements Document

## Introduction

Se agrega un campo booleano `active` a la entidad `Product` para indicar si un producto está activo o no.
Los productos activos son visibles en los endpoints públicos (`/api`), mientras que los endpoints de administración (`/admin`) exponen el campo `active` y permiten gestionarlo.
La migración de base de datos debe establecer `active = true` para todos los productos existentes, y los nuevos productos también deben crearse con `active = true` por defecto.

## Glossary

- **Product**: Entidad JPA que representa un producto en el catálogo.
- **ProductRequest**: DTO de entrada para crear o actualizar un producto (endpoints `/admin`).
- **AdminProductResponse**: DTO de salida para endpoints de administración (`/admin/api/products`). Incluye el campo `active`.
- **PublicProductResponse**: DTO de salida para endpoints públicos (`/api/products`). No incluye el campo `active`.
- **ProductService**: Servicio que encapsula la lógica de negocio de productos.
- **BulkImportService**: Servicio que procesa la importación masiva de productos desde CSV.
- **Liquibase**: Herramienta de migración de esquema de base de datos usada en el perfil `postgres`.
- **Migration**: Changeset de Liquibase que altera el esquema o los datos de la base de datos.

## Requirements

### Requirement 1: Campo `active` en la entidad Product

**User Story:** Como desarrollador, quiero que la entidad `Product` tenga un campo `active`, para poder controlar la visibilidad de los productos en el catálogo.

#### Acceptance Criteria

1. THE `Product` entity SHALL have a boolean field `active`.
2. THE `Product` entity SHALL default the `active` field to `true` when no value is provided.

---

### Requirement 2: Migración de base de datos

**User Story:** Como administrador de base de datos, quiero que todos los productos existentes tengan `active = true` tras la migración, para que no se pierda visibilidad de ningún producto al desplegar el cambio.

#### Acceptance Criteria

1. THE Migration SHALL add a column `active` of type `BOOLEAN NOT NULL` to the `products` table.
2. THE Migration SHALL set `active = true` for all existing rows in the `products` table.
3. THE Migration SHALL set the default value of the `active` column to `true` for all future inserts.
4. WHEN the application starts with the `postgres` profile, THE Liquibase SHALL apply the migration exactly once.

---

### Requirement 3: Valor por defecto en nuevos productos

**User Story:** Como administrador, quiero que los nuevos productos se creen con `active = true` por defecto, para que estén disponibles en el catálogo inmediatamente tras su creación.

#### Acceptance Criteria

1. WHEN a new product is created via `ProductService`, THE `ProductService` SHALL set `active = true` if no value is provided in the `ProductRequest`.
2. WHEN a product is imported via `BulkImportService`, THE `BulkImportService` SHALL create each product with `active = true`.

---

### Requirement 4: Exposición del campo `active` en endpoints de administración

**User Story:** Como administrador, quiero ver y gestionar el campo `active` en los endpoints `/admin`, para poder activar o desactivar productos desde el panel de administración.

#### Acceptance Criteria

1. WHEN a product is returned by any endpoint under `/admin/api/products`, THE `AdminProductResponse` SHALL include the `active` field.
2. WHEN a product is returned by any endpoint under `/admin/api/products/{id}/images`, THE response SHALL include the `active` field in the product context where applicable.
3. WHEN a `ProductRequest` is submitted to `POST /admin/api/products`, THE `ProductService` SHALL accept an optional `active` field and use it to set the product's active status.
4. WHEN a `ProductRequest` is submitted to `PUT /admin/api/products/{id}`, THE `ProductService` SHALL update the product's `active` field with the value provided in the request.
5. IF the `active` field is absent from a `ProductRequest`, THEN THE `ProductService` SHALL default the value to `true`.

---

### Requirement 5: Ocultación del campo `active` en endpoints públicos

**User Story:** Como consumidor de la API pública, quiero que las respuestas de `/api/products` no expongan el campo `active`, para que los detalles internos de gestión no sean visibles públicamente.

#### Acceptance Criteria

1. WHEN a product is returned by any endpoint under `/api/products`, THE `PublicProductResponse` SHALL NOT include the `active` field.
2. THE `PublicProductController` SHALL use `PublicProductResponse` as its response type for all product endpoints.

---

### Requirement 6: Separación de DTOs de respuesta

**User Story:** Como desarrollador, quiero DTOs de respuesta distintos para los endpoints públicos y de administración, para garantizar que el campo `active` solo se exponga donde corresponde.

#### Acceptance Criteria

1. THE system SHALL have a dedicated `AdminProductResponse` DTO that includes the `active` field alongside all existing product fields (`id`, `name`, `description`, `price`, `images`, `tags`).
2. THE system SHALL have a dedicated `PublicProductResponse` DTO (or the existing `ProductResponse` adapted) that excludes the `active` field.
3. THE `ProductService` SHALL provide a method that returns `AdminProductResponse` for use by admin controllers.
4. THE `ProductService` SHALL provide a method that returns `PublicProductResponse` for use by public controllers.
5. FOR ALL products, serializing to `AdminProductResponse` and then reading the `active` field SHALL return the same value stored in the `Product` entity (round-trip property).

---

### Requirement 7: Importación masiva con campo `active`

**User Story:** Como administrador, quiero que los productos importados masivamente desde CSV sean creados con `active = true`, para que estén disponibles en el catálogo sin intervención manual.

#### Acceptance Criteria

1. WHEN a CSV row is processed by `BulkImportService`, THE `BulkImportService` SHALL create the product with `active = true`.
2. WHEN the import result is returned by `POST /admin/api/products/import`, THE response SHALL reflect the `active` status of each successfully created product via the `AdminProductResponse`.
