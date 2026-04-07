# Requirements Document

## Introduction

Este documento define los requisitos para agregar soporte de imágenes a los productos del servicio `products`. La funcionalidad permite asociar múltiples URLs de imágenes a un producto mediante una relación 1:N persistida en una tabla `product_images`. El servicio almacena únicamente las URLs de las imágenes; los archivos binarios residen en un proveedor externo (p. ej. Cloudinary). Los endpoints de lectura son públicos; los de escritura requieren un JWT válido, en línea con la seguridad ya establecida para los endpoints de productos.

## Glossary

- **Image_Controller**: El `@RestController` que gestiona las peticiones HTTP bajo `/api/products/{id}/images`.
- **Image_Service**: El componente de servicio que encapsula la lógica de negocio y el mapeo de DTOs para imágenes.
- **Image_Repository**: El `JpaRepository<ProductImage, Long>` que gestiona la persistencia de imágenes.
- **ProductImage**: La entidad JPA que representa una imagen asociada a un producto, con campos `id`, `productId`, `url` y `displayOrder`.
- **ImageRequest**: El DTO de entrada que transporta una o más URLs de imágenes desde el cliente.
- **ImageResponse**: El DTO de salida devuelto al cliente con los campos `id`, `productId`, `url` y `displayOrder`.
- **DisplayOrderRequest**: El DTO de entrada para actualizar únicamente el campo `displayOrder` de una imagen.
- **ProductResponse**: El DTO de salida de producto, extendido para incluir la lista de imágenes asociadas.
- **Security_Filter_Chain**: El bean de Spring Security que aplica las reglas de autorización JWT ya definidas en el spec `api-security`.
- **Protected_Image_Endpoint**: Cualquier endpoint de escritura sobre imágenes (`POST`, `PUT`, `DELETE /api/products/{id}/images`).
- **Public_Image_Endpoint**: Cualquier endpoint de lectura sobre imágenes (`GET /api/products/{id}/images`).
- **Liquibase**: La herramienta de migración de base de datos que gestiona el esquema de la tabla `product_images`.

---

## Requirements

### Requirement 1: Modelo de datos de imágenes

**User Story:** As a developer, I want a well-defined image data model, so that the API consistently represents product image information.

#### Acceptance Criteria

1. THE ProductImage SHALL contener los campos: `id` (Long, clave primaria auto-generada), `productId` (Long, clave foránea no nula que referencia `products.id`), `url` (String, no nulo, no vacío), y `displayOrder` (Integer, no nulo, mínimo 0).
2. THE ImageRequest SHALL aceptar una lista de una o más URLs (`List<String>`) con cada URL no vacía, validada mediante Bean Validation.
3. THE DisplayOrderRequest SHALL aceptar un único campo `displayOrder` (Integer, no nulo, mínimo 0), validado mediante Bean Validation.
4. THE ImageResponse SHALL incluir los cuatro campos: `id`, `productId`, `url` y `displayOrder`.

---

### Requirement 2: Esquema de base de datos para imágenes

**User Story:** As a developer, I want the image table schema managed by Liquibase, so that schema changes are versioned and reproducible.

#### Acceptance Criteria

1. WHEN la aplicación arranca, THE Liquibase SHALL aplicar el changelog que crea la tabla `product_images` si aún no existe.
2. THE Liquibase changelog SHALL definir la tabla `product_images` con las columnas: `id` (BIGSERIAL, clave primaria), `product_id` (BIGINT NOT NULL, clave foránea a `products.id` con `ON DELETE CASCADE`), `url` (TEXT NOT NULL), y `display_order` (INTEGER NOT NULL).
3. THE Liquibase changelog SHALL crear un índice sobre `product_images(product_id)` para optimizar las consultas de imágenes por producto.

---

### Requirement 3: Añadir imágenes a un producto

**User Story:** As an API client, I want to add one or more image URLs to a product, so that I can associate visual content with a product.

#### Acceptance Criteria

1. WHEN a `POST /api/products/{id}/images` request is received with a valid Bearer JWT and a valid `ImageRequest` body, THE Image_Controller SHALL persistir las imágenes y devolver una respuesta `201 Created` con la lista de `ImageResponse` creadas.
2. WHEN a `POST /api/products/{id}/images` request is received and no product with that `id` exists, THE GlobalExceptionHandler SHALL devolver una respuesta `404 Not Found` con un `ErrorResponse` identificando el `id` del producto ausente.
3. WHEN a `POST /api/products/{id}/images` request is received with an empty URL list or a blank URL, THE GlobalExceptionHandler SHALL devolver una respuesta `400 Bad Request` con mensajes de error de validación a nivel de campo.
4. WHEN a `POST /api/products/{id}/images` request is received without an `Authorization` header, THE Security_Filter_Chain SHALL devolver una respuesta `401 Unauthorized` sin invocar el Image_Controller.

---

### Requirement 4: Listar imágenes de un producto

**User Story:** As an API client, I want to retrieve all images for a product, so that I can display them in the product detail view.

#### Acceptance Criteria

1. WHEN a `GET /api/products/{id}/images` request is received and a product with that `id` exists, THE Image_Controller SHALL devolver una respuesta `200 OK` con un array JSON de `ImageResponse` ordenados por `displayOrder` ascendente.
2. WHILE a product exists but has no associated images, THE Image_Controller SHALL devolver una respuesta `200 OK` con un array JSON vacío.
3. WHEN a `GET /api/products/{id}/images` request is received and no product with that `id` exists, THE GlobalExceptionHandler SHALL devolver una respuesta `404 Not Found` con un `ErrorResponse` identificando el `id` del producto ausente.
4. WHEN a `GET /api/products/{id}/images` request is received without an `Authorization` header, THE Security_Filter_Chain SHALL permitir la petición y devolver una respuesta `200 OK`.

---

### Requirement 5: Actualizar el orden de visualización de una imagen

**User Story:** As an API client, I want to update the display order of a specific image, so that I can control the sequence in which images are shown.

#### Acceptance Criteria

1. WHEN a `PUT /api/products/{id}/images/{imageId}` request is received with a valid Bearer JWT and a valid `DisplayOrderRequest` body, and both the product and the image exist and the image belongs to the product, THE Image_Controller SHALL actualizar el campo `displayOrder` y devolver una respuesta `200 OK` con el `ImageResponse` actualizado.
2. WHEN a `PUT /api/products/{id}/images/{imageId}` request is received and no product with that `id` exists, THE GlobalExceptionHandler SHALL devolver una respuesta `404 Not Found` con un `ErrorResponse` identificando el `id` del producto ausente.
3. WHEN a `PUT /api/products/{id}/images/{imageId}` request is received and no image with that `imageId` exists for the given product, THE GlobalExceptionHandler SHALL devolver una respuesta `404 Not Found` con un `ErrorResponse` identificando el `imageId` ausente.
4. WHEN a `PUT /api/products/{id}/images/{imageId}` request is received with a `null` or negative `displayOrder`, THE GlobalExceptionHandler SHALL devolver una respuesta `400 Bad Request` con mensajes de error de validación a nivel de campo.
5. WHEN a `PUT /api/products/{id}/images/{imageId}` request is received without an `Authorization` header, THE Security_Filter_Chain SHALL devolver una respuesta `401 Unauthorized` sin invocar el Image_Controller.

---

### Requirement 6: Eliminar una imagen de un producto

**User Story:** As an API client, I want to delete a specific image from a product, so that I can remove outdated or incorrect images.

#### Acceptance Criteria

1. WHEN a `DELETE /api/products/{id}/images/{imageId}` request is received with a valid Bearer JWT, and both the product and the image exist and the image belongs to the product, THE Image_Controller SHALL eliminar la imagen y devolver una respuesta `204 No Content` sin cuerpo.
2. WHEN a `DELETE /api/products/{id}/images/{imageId}` request is received and no product with that `id` exists, THE GlobalExceptionHandler SHALL devolver una respuesta `404 Not Found` con un `ErrorResponse` identificando el `id` del producto ausente.
3. WHEN a `DELETE /api/products/{id}/images/{imageId}` request is received and no image with that `imageId` exists for the given product, THE GlobalExceptionHandler SHALL devolver una respuesta `404 Not Found` con un `ErrorResponse` identificando el `imageId` ausente.
4. WHEN a `DELETE /api/products/{id}/images/{imageId}` request is received without an `Authorization` header, THE Security_Filter_Chain SHALL devolver una respuesta `401 Unauthorized` sin invocar el Image_Controller.

---

### Requirement 7: Imágenes embebidas en las respuestas de producto

**User Story:** As an API client, I want product responses to include the associated images, so that I can display product images without making a separate request.

#### Acceptance Criteria

1. WHEN a `GET /api/products` request is received, THE Controller SHALL devolver cada `ProductResponse` con un campo `images` que contenga la lista de `ImageResponse` del producto, ordenados por `displayOrder` ascendente.
2. WHEN a `GET /api/products/{id}` request is received and the product exists, THE Controller SHALL devolver el `ProductResponse` con un campo `images` que contenga la lista de `ImageResponse` del producto, ordenados por `displayOrder` ascendente.
3. WHILE a product has no associated images, THE Controller SHALL incluir un campo `images` con un array vacío en el `ProductResponse`.
4. THE ProductResponse SHALL extenderse para incluir el campo `images` de tipo `List<ImageResponse>` sin romper los contratos existentes de los endpoints de producto.

---

### Requirement 8: Integridad referencial y eliminación en cascada

**User Story:** As a developer, I want images to be automatically removed when their parent product is deleted, so that no orphaned image records remain in the database.

#### Acceptance Criteria

1. WHEN a product is deleted via `DELETE /api/products/{id}`, THE Liquibase-defined foreign key constraint SHALL eliminar en cascada todas las filas de `product_images` asociadas a ese `product_id`.
2. THE Image_Service SHALL verificar que el `imageId` pertenece al `productId` indicado en la ruta antes de ejecutar cualquier operación de actualización o eliminación.
3. IF an `imageId` does not belong to the given `productId`, THEN THE GlobalExceptionHandler SHALL devolver una respuesta `404 Not Found` con un `ErrorResponse` describiendo la discrepancia.

---

### Requirement 9: Validación de URLs de imágenes

**User Story:** As an API client, I want the service to reject malformed image URLs, so that only valid, reachable image references are stored.

#### Acceptance Criteria

1. WHEN a `POST /api/products/{id}/images` request is received with a URL that does not start with `http://` or `https://`, THE GlobalExceptionHandler SHALL devolver una respuesta `400 Bad Request` con un mensaje de error indicando que la URL no es válida.
2. THE ImageRequest SHALL validar cada URL de la lista mediante una anotación Bean Validation (`@URL` o equivalente) que compruebe el formato del esquema HTTP/HTTPS.
3. IF a URL in the list is blank or null, THEN THE GlobalExceptionHandler SHALL devolver una respuesta `400 Bad Request` con mensajes de error de validación a nivel de campo.

---

### Requirement 10: Arquitectura en capas para imágenes

**User Story:** As a developer, I want the image feature to follow the same layered architecture as the existing product feature, so that the codebase remains consistent and maintainable.

#### Acceptance Criteria

1. THE Image_Controller SHALL delegar toda la lógica de negocio al Image_Service y no acceder directamente al Image_Repository.
2. THE Image_Service SHALL encapsular todo el mapeo de DTOs a entidades y la aplicación de reglas de negocio para imágenes.
3. THE Image_Repository SHALL extender `JpaRepository<ProductImage, Long>` y exponer un método de consulta para obtener imágenes por `productId` ordenadas por `displayOrder` ascendente.
4. THE ProductImage SHALL declarar la relación `@ManyToOne` hacia `Product` y la entidad `Product` SHALL declarar la relación `@OneToMany` hacia `ProductImage` con `CascadeType.ALL` y `orphanRemoval = true`.
