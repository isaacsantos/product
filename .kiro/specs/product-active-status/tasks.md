# Implementation Plan: product-active-status

## Overview

Agregar el campo booleano `active` a la entidad `Product`, separar los DTOs de respuesta en `AdminProductResponse` y `PublicProductResponse`, actualizar la capa de servicio y controladores, y añadir la migración Liquibase correspondiente.

## Tasks

- [x] 1. Migración Liquibase: agregar columna `active`
  - Crear `src/main/resources/db/changelog/008-add-product-active-column.yaml` con un `addColumn` de tipo `BOOLEAN NOT NULL DEFAULT true` sobre la tabla `products`.
  - Agregar un `update` que establezca `active = true` para todas las filas existentes.
  - Registrar el nuevo changeset en `db.changelog-master.yaml`.
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 2. Entidad y DTOs
  - [x] 2.1 Agregar campo `active` a `Product.java`
    - Añadir `@Column(nullable = false) @Builder.Default private boolean active = true;`
    - _Requirements: 1.1, 1.2_

  - [x] 2.2 Agregar campo `active` a `ProductRequest.java`
    - Añadir `@Builder.Default private boolean active = true;`
    - _Requirements: 4.3, 4.4, 4.5_

  - [x] 2.3 Crear `AdminProductResponse.java`
    - Incluir todos los campos de `ProductResponse` más `boolean active`.
    - _Requirements: 4.1, 6.1_

  - [x] 2.4 Crear `PublicProductResponse.java`
    - Idéntico a `ProductResponse` actual pero sin el campo `active`.
    - _Requirements: 5.1, 6.2_

- [x] 3. Capa de servicio
  - [x] 3.1 Actualizar interfaz `ProductService`
    - Agregar métodos admin: `createAdmin`, `findAllAdmin`, `findByIdAdmin`, `updateAdmin`, `setTagsAdmin`.
    - Cambiar tipo de retorno de `findAll` y `findById` a `PublicProductResponse`.
    - _Requirements: 6.3, 6.4_

  - [x] 3.2 Implementar métodos en `ProductServiceImpl`
    - Agregar `toAdminResponse(Product)` y `toPublicResponse(Product)`.
    - Implementar los métodos `*Admin` delegando en el repositorio y usando `toAdminResponse`.
    - Actualizar `findAll` y `findById` para usar `toPublicResponse`.
    - Asegurar que `create` / `createAdmin` respetan el valor de `active` del request (default `true`).
    - _Requirements: 1.2, 3.1, 4.3, 4.4, 4.5, 6.3, 6.4_

  - [x] 3.3 Property test: default `active` en creación (Property 1)
    - **Property 1: Default active en creación**
    - **Validates: Requirements 1.2, 3.1, 4.5**

  - [x] 3.4 Property test: valor explícito de `active` se preserva (Property 2)
    - **Property 2: El valor explícito de `active` se preserva**
    - **Validates: Requirements 4.3, 4.4**

  - [x] 3.5 Property test: round-trip de `active` en `AdminProductResponse` (Property 3)
    - **Property 3: Round-trip de `active` en AdminProductResponse**
    - **Validates: Requirements 4.1, 6.1, 6.5**

  - [x] 3.6 Property test: `PublicProductResponse` nunca expone `active` (Property 4)
    - **Property 4: PublicProductResponse nunca expone `active`**
    - **Validates: Requirements 5.1, 6.2**

- [x] 4. Checkpoint — Asegurar que todos los tests pasan
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Controladores
  - [x] 5.1 Actualizar `ProductController` (admin)
    - Cambiar todos los métodos para usar `AdminProductResponse` y los métodos `*Admin` del servicio.
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [x] 5.2 Actualizar `PublicProductController`
    - Cambiar todos los métodos para usar `PublicProductResponse` y los métodos públicos del servicio.
    - _Requirements: 5.1, 5.2_

  - [x] 5.3 Unit tests para `ProductController` y `PublicProductController`
    - Verificar que el endpoint admin retorna `active` en el JSON.
    - Verificar que el endpoint público no retorna `active` en el JSON.
    - _Requirements: 4.1, 5.1_

- [x] 6. `BulkImportServiceImpl`
  - [x] 6.1 Actualizar `BulkImportServiceImpl`
    - Cambiar la llamada a `productService.create` por `productService.createAdmin`.
    - Usar `AdminProductResponse` en lugar de `ProductResponse` para el resultado de cada fila.
    - _Requirements: 3.2, 7.1, 7.2_

  - [x] 6.2 Property test: BulkImport crea productos con `active = true` (Property 5)
    - **Property 5: BulkImport crea productos con `active = true`**
    - **Validates: Requirements 3.2, 7.1**

  - [x] 6.3 Property test: import result refleja el estado `active` (Property 6)
    - **Property 6: Import result refleja el estado `active` del producto**
    - **Validates: Requirements 7.2**

- [x] 7. Checkpoint final — Asegurar que todos los tests pasan
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido.
- Los property tests usan **jqwik** (ya presente en el proyecto).
- La migración Liquibase solo aplica en el perfil `postgres`; el perfil `h2` usa `ddl-auto: create-drop`.
- `ProductResponse` existente puede mantenerse temporalmente para compatibilidad interna hasta completar la migración.
