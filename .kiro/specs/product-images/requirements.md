# Requirements Document

## Introduction

Esta feature extiende el endpoint `/admin/api/products/{productId}/images` existente para integrar la subida de imágenes a Cloudinary. Actualmente el endpoint acepta URLs ya alojadas; con esta mejora, el cliente podrá enviar archivos de imagen directamente (multipart/form-data) y el sistema los subirá a Cloudinary, almacenando la URL pública resultante en la base de datos. El endpoint sigue protegido por Firebase Auth con rol ADMIN o MANAGER.

## Glossary

- **CloudinaryService**: Componente de la aplicación responsable de comunicarse con la API de Cloudinary para subir y eliminar imágenes.
- **Cloudinary**: Servicio externo de gestión de imágenes en la nube (https://cloudinary.com). Plan gratuito disponible.
- **ProductImageController**: Controlador REST existente en `/admin/api/products/{productId}/images`.
- **ProductImageService**: Interfaz de servicio existente que gestiona la lógica de imágenes de producto.
- **ProductImage**: Entidad JPA que representa una imagen asociada a un producto, con campos `id`, `productId`, `url` y `displayOrder`.
- **Admin**: Usuario autenticado con Firebase Auth que posee el rol `ADMIN` o `MANAGER`.
- **cloudinary_public_id**: Identificador único asignado por Cloudinary a cada imagen subida, necesario para eliminarla posteriormente.
- **MultipartFile**: Archivo binario enviado en una petición HTTP `multipart/form-data`.

---

## Requirements

### Requirement 1: Subida de imágenes a Cloudinary

**User Story:** As an Admin, I want to upload image files directly to the product management API, so that the images are stored in Cloudinary and associated with a product without having to manage external hosting manually.

#### Acceptance Criteria

1. WHEN an Admin sends a `POST /admin/api/products/{productId}/images/upload` request with one or more `MultipartFile` fields, THE `ProductImageController` SHALL accept the request as `multipart/form-data`.
2. WHEN the upload request is received, THE `CloudinaryService` SHALL upload each file to Cloudinary using the Cloudinary Java SDK and obtain a public HTTPS URL for each image.
3. WHEN Cloudinary returns a successful upload response, THE `ProductImageService` SHALL persist a `ProductImage` record with the Cloudinary public URL and an auto-assigned `displayOrder` equal to the current image count for that product.
4. WHEN all images are uploaded and persisted successfully, THE `ProductImageController` SHALL return HTTP 201 with a list of `ImageResponse` objects containing `id`, `productId`, `url`, and `displayOrder`.
5. IF the `productId` does not exist in the database, THEN THE `ProductImageService` SHALL throw a `ProductNotFoundException` and THE `ProductImageController` SHALL return HTTP 404.
6. IF the upload request contains no files or all files are empty, THEN THE `ProductImageController` SHALL return HTTP 400 with an `ErrorResponse` describing the validation failure.
7. IF a file's content type is not one of `image/jpeg`, `image/png`, `image/webp`, or `image/gif`, THEN THE `CloudinaryService` SHALL reject the file and THE `ProductImageController` SHALL return HTTP 400 with an `ErrorResponse`.
8. IF Cloudinary returns an error during upload, THEN THE `CloudinaryService` SHALL throw a runtime exception and THE `ProductImageController` SHALL return HTTP 502 with an `ErrorResponse` containing the message `"Cloudinary upload failed"`.
9. WHEN an Admin sends the upload request without a valid Firebase Auth JWT, THE `ProductImageController` SHALL return HTTP 401.
10. WHEN an authenticated user without the `ADMIN` or `MANAGER` role sends the upload request, THE `ProductImageController` SHALL return HTTP 403.

---

### Requirement 2: Eliminación de imágenes con limpieza en Cloudinary

**User Story:** As an Admin, I want deleting a product image to also remove it from Cloudinary, so that storage is not wasted with orphaned assets.

#### Acceptance Criteria

1. WHEN an Admin sends a `DELETE /admin/api/products/{productId}/images/{imageId}` request, THE `ProductImageService` SHALL retrieve the `cloudinary_public_id` associated with the image before deleting the database record.
2. WHEN the database record is deleted, THE `CloudinaryService` SHALL call the Cloudinary API to delete the asset identified by `cloudinary_public_id`.
3. IF Cloudinary returns an error during deletion, THEN THE `CloudinaryService` SHALL log the error and THE `ProductImageService` SHALL still complete the database deletion, returning HTTP 204 to the caller.
4. IF the `imageId` does not exist or does not belong to `productId`, THEN THE `ProductImageService` SHALL throw a `ProductImageNotFoundException` and THE `ProductImageController` SHALL return HTTP 404.

---

### Requirement 3: Configuración de credenciales de Cloudinary

**User Story:** As a developer, I want Cloudinary credentials to be externalized in application configuration, so that secrets are not hardcoded and can be changed per environment.

#### Acceptance Criteria

1. THE `CloudinaryService` SHALL read `cloudinary.cloud-name`, `cloudinary.api-key`, and `cloudinary.api-secret` from the application configuration at startup.
2. IF any of the three Cloudinary configuration properties is absent or blank at startup, THEN THE application SHALL fail to start with a descriptive error message indicating which property is missing.
3. WHERE the `h2` Spring profile is active, THE application SHALL allow Cloudinary credentials to be set to placeholder values so that the application starts without a live Cloudinary account.

---

### Requirement 4: Almacenamiento del identificador público de Cloudinary

**User Story:** As a developer, I want the Cloudinary public ID to be stored alongside the image URL, so that images can be deleted from Cloudinary by reference without parsing the URL.

#### Acceptance Criteria

1. THE `ProductImage` entity SHALL include a `cloudinaryPublicId` field of type `String`, nullable for backward compatibility with images created before this feature.
2. WHEN a new image is uploaded via `CloudinaryService`, THE `ProductImageService` SHALL persist the `cloudinaryPublicId` returned by Cloudinary in the corresponding `ProductImage` record.
3. THE `ImageResponse` DTO SHALL NOT expose `cloudinaryPublicId` to API consumers.
