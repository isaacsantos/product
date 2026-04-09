# Implementation Plan: Product Images (Cloudinary Integration)

## Overview

Extend the existing `ProductImage` feature to support direct file uploads via Cloudinary. The existing JSON endpoint (`POST /admin/api/products/{productId}/images`) and all other endpoints remain unchanged. New work focuses on the Cloudinary integration layer, a new `POST /upload` endpoint, and updating `deleteImage` to clean up Cloudinary assets.

## Tasks

- [x] 1. Agregar dependencia Maven de Cloudinary y configuración
  - [x] 1.1 Agregar `cloudinary-http5:2.2.0` en `pom.xml`
    - Añadir bajo `<dependencies>` sin `<scope>` (runtime)
    - _Requirements: 3.1_
  - [x] 1.2 Añadir bloque `cloudinary:` en `src/main/resources/application.yaml`
    - Campos: `cloud-name: ${CLOUDINARY_CLOUD_NAME}`, `api-key: ${CLOUDINARY_API_KEY}`, `api-secret: ${CLOUDINARY_API_SECRET}`
    - _Requirements: 3.1_
  - [x] 1.3 Añadir valores placeholder en `src/main/resources/application-h2.yaml`
    - `cloud-name: placeholder`, `api-key: placeholder`, `api-secret: placeholder`
    - _Requirements: 3.3_

- [x] 2. Crear `CloudinaryProperties` y nuevas excepciones
  - [x] 2.1 Crear `CloudinaryProperties` en `config/`
    - `@ConfigurationProperties(prefix = "cloudinary")` + `@Validated` + Lombok `@Data`
    - Campos: `@NotBlank String cloudName`, `@NotBlank String apiKey`, `@NotBlank String apiSecret`
    - Registrar con `@EnableConfigurationProperties` en `ProductsApplication` o via `@ConfigurationPropertiesScan`
    - _Requirements: 3.1, 3.2_
  - [x] 2.2 Crear `InvalidImageTypeException` en `exception/`
    - `super("Unsupported image type: " + contentType)`
    - _Requirements: 1.7_
  - [x] 2.3 Crear `CloudinaryUploadException` en `exception/`
    - Constructor `(String message, Throwable cause)`
    - _Requirements: 1.8_
  - [x] 2.4 Crear `CloudinaryDeleteException` en `exception/`
    - Constructor `(String message, Throwable cause)` — solo se usa para logging, no se mapea a HTTP
    - _Requirements: 2.3_

- [x] 3. Crear `CloudinaryService` y `CloudinaryServiceImpl`
  - [x] 3.1 Crear interfaz `CloudinaryService` en `service/`
    - Métodos: `CloudinaryUploadResult upload(MultipartFile file)` y `void delete(String publicId)`
    - _Requirements: 1.2, 2.2_
  - [x] 3.2 Crear record `CloudinaryUploadResult` en `model/`
    - `record CloudinaryUploadResult(String url, String publicId) {}`
    - DTO interno — no expuesto en la API
    - _Requirements: 1.2, 4.2_
  - [x] 3.3 Crear `CloudinaryServiceImpl` en `service/`
    - `@Service`, inyectar `CloudinaryProperties` en constructor, inicializar `Cloudinary` con `ObjectUtils.asMap`
    - `ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif")`
    - `upload`: validar `contentType` → lanzar `InvalidImageTypeException` si no permitido; llamar `cloudinary.uploader().upload(file.getBytes(), ...)` → devolver `CloudinaryUploadResult`; capturar `IOException` → lanzar `CloudinaryUploadException`
    - `delete`: llamar `cloudinary.uploader().destroy(publicId, ...)` ; capturar `IOException` → lanzar `CloudinaryDeleteException`
    - _Requirements: 1.2, 1.7, 1.8, 2.2, 3.1_
  - [x] 3.4 Escribir unit tests para `CloudinaryServiceImpl` (`CloudinaryServiceImplTest`)
    - Usar `@ExtendWith(MockitoExtension.class)`, mockear el `Cloudinary` SDK internamente (spy o constructor injection)
    - Upload exitoso → devuelve `CloudinaryUploadResult` con `url` y `publicId` correctos
    - Upload con content-type inválido → lanza `InvalidImageTypeException`
    - Upload con error de SDK (`IOException`) → lanza `CloudinaryUploadException`
    - Delete exitoso → no lanza excepción
    - Delete con error de SDK → lanza `CloudinaryDeleteException`
    - _Requirements: 1.2, 1.7, 1.8, 2.2_

- [x] 4. Añadir `cloudinaryPublicId` a `ProductImage` y migración Liquibase
  - [x] 4.1 Añadir campo `cloudinaryPublicId` a la entidad `ProductImage`
    - `@Column(name = "cloudinary_public_id")` nullable — compatibilidad con registros anteriores
    - _Requirements: 4.1_
  - [x] 4.2 Crear changelog `009-add-cloudinary-public-id-to-product-images.yaml` en `src/main/resources/db/changelog/`
    - `addColumn` en `product_images`: columna `cloudinary_public_id` tipo `TEXT`, `nullable: true`
    - _Requirements: 4.1_
  - [x] 4.3 Registrar el nuevo changelog en `db.changelog-master.yaml`
    - Añadir entrada al final: `- include: file: 009-add-cloudinary-public-id-to-product-images.yaml`
    - _Requirements: 4.1_

- [x] 5. Actualizar `ProductImageService` y `ProductImageServiceImpl`
  - [x] 5.1 Añadir método `uploadImages` a la interfaz `ProductImageService`
    - `List<ImageResponse> uploadImages(Long productId, List<MultipartFile> files)`
    - _Requirements: 1.1, 1.3, 1.4_
  - [x] 5.2 Implementar `uploadImages` en `ProductImageServiceImpl`
    - Inyectar `CloudinaryService` en el constructor
    - Verificar producto existe (`ProductNotFoundException` si no)
    - Obtener conteo actual de imágenes para `displayOrder` inicial
    - Por cada archivo: llamar `cloudinaryService.upload(file)` → construir `ProductImage` con `url`, `cloudinaryPublicId` y `displayOrder`
    - `imageRepository.saveAll(...)` → devolver `List<ImageResponse>`
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 4.2_
  - [x] 5.3 Actualizar `deleteImage` en `ProductImageServiceImpl` para invocar Cloudinary
    - Guardar `cloudinaryPublicId` antes de `deleteById`
    - Tras `deleteById`: si `cloudinaryPublicId != null`, llamar `cloudinaryService.delete(publicId)` en try/catch; en catch loguear con `log.error` y continuar
    - _Requirements: 2.1, 2.2, 2.3_
  - [x] 5.4 Actualizar unit tests de `ProductImageServiceImpl` (`ProductImageServiceImplTest`)
    - Mockear `CloudinaryService` además de los repositorios existentes
    - `uploadImages` happy path → persiste `cloudinaryPublicId` y devuelve `ImageResponse` correctos
    - `uploadImages` con producto inexistente → lanza `ProductNotFoundException`
    - `deleteImage` con `cloudinaryPublicId` presente → verifica que `cloudinaryService.delete` fue invocado con ese publicId
    - `deleteImage` con `cloudinaryPublicId` nulo → verifica que `cloudinaryService.delete` NO fue invocado
    - `deleteImage` cuando Cloudinary falla → completa la eliminación de BD igualmente (no relanza excepción)
    - _Requirements: 1.3, 1.5, 2.1, 2.2, 2.3, 4.2_

- [x] 6. Añadir handlers en `GlobalExceptionHandler` y actualizar `ProductImageController`
  - [x] 6.1 Añadir handler `InvalidImageTypeException` en `GlobalExceptionHandler`
    - Devolver `400 Bad Request` con `ErrorResponse`
    - _Requirements: 1.7_
  - [x] 6.2 Añadir handler `CloudinaryUploadException` en `GlobalExceptionHandler`
    - Devolver `502 Bad Gateway` con mensaje `"Cloudinary upload failed"`
    - _Requirements: 1.8_
  - [x] 6.3 Añadir endpoint `POST /upload` en `ProductImageController`
    - `@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`
    - `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`
    - Parámetro: `@RequestParam("files") List<MultipartFile> files`
    - Delegar a `productImageService.uploadImages(productId, files)` → `201 Created`
    - _Requirements: 1.1, 1.4, 1.9, 1.10_
  - [x] 6.4 Actualizar `@WebMvcTest` tests de `ProductImageController` (`ProductImageControllerTest`)
    - Mockear `CloudinaryService` si es necesario (o solo `ProductImageService`)
    - `POST /upload` sin JWT → 401
    - `POST /upload` con rol insuficiente → 403
    - `POST /upload` con content-type inválido (mock lanza `InvalidImageTypeException`) → 400
    - `POST /upload` sin archivos → 400
    - `POST /upload` con error de Cloudinary (mock lanza `CloudinaryUploadException`) → 502
    - `POST /upload` exitoso → 201 con lista de `ImageResponse`
    - _Requirements: 1.1, 1.4, 1.6, 1.7, 1.8, 1.9, 1.10_

- [x] 7. Checkpoint — Verificar que todos los tests pasan
  - Ejecutar `./mvnw test -Dtest='!*IT,!*IntegrationTest'` y confirmar que todo está en verde. Preguntar al usuario si surgen dudas.

- [x] 8. Escribir property-based tests con jqwik (`ProductImagePropertyTest`)
  - Usar `@Property(tries = 100)` en cada test
  - Tests de integración extienden `AbstractIntegrationTest` (Testcontainers PostgreSQL — requiere Docker)
  - Tests de validación/seguridad usan `@WebMvcTest` con `JwtDecoder` mockeado
  - [x] 8.1 Property 1: Respuesta de upload contiene todos los campos requeridos
    - Generar productos existentes y listas de archivos válidos (mock `CloudinaryService`); verificar que cada `ImageResponse` tiene `id` no nulo, `productId` correcto, `url` con esquema `https://`, `displayOrder >= 0`
    - `// Feature: product-images, Property 1: Respuesta de upload contiene todos los campos requeridos`
    - **Validates: Requirements 1.2, 1.4**
  - [x] 8.2 Property 2: displayOrder auto-asignado es consecutivo desde el conteo actual
    - Generar producto con N imágenes existentes y M archivos nuevos; verificar que los nuevos `displayOrder` son N, N+1, ..., N+M-1
    - `// Feature: product-images, Property 2: displayOrder auto-asignado es consecutivo desde el conteo actual`
    - **Validates: Requirements 1.3**
  - [x] 8.3 Property 3: Tipo de archivo inválido devuelve 400
    - Generar content-types aleatorios fuera del conjunto permitido; verificar 400 sin persistencia
    - `// Feature: product-images, Property 3: Tipo de archivo inválido devuelve 400`
    - **Validates: Requirements 1.7**
  - [x] 8.4 Property 4: cloudinaryPublicId se persiste en cada imagen subida
    - Generar uploads exitosos con `publicId` aleatorio (mock `CloudinaryService`); verificar que el `ProductImage` en BD tiene ese `cloudinaryPublicId`
    - `// Feature: product-images, Property 4: cloudinaryPublicId se persiste en cada imagen subida`
    - **Validates: Requirements 4.2**
  - [x] 8.5 Property 5: Eliminación invoca Cloudinary con el publicId correcto
    - Generar imágenes con `cloudinaryPublicId` aleatorio; DELETE y verificar que `CloudinaryService.delete` fue llamado con ese `publicId` y el registro fue eliminado de BD
    - `// Feature: product-images, Property 5: Eliminación invoca Cloudinary con el publicId correcto`
    - **Validates: Requirements 2.1, 2.2**
  - [x] 8.6 Property 6: cloudinaryPublicId no se expone en ImageResponse
    - Generar cualquier `ImageResponse` (via GET o POST); verificar que el JSON serializado no contiene la clave `cloudinaryPublicId`
    - `// Feature: product-images, Property 6: cloudinaryPublicId no se expone en ImageResponse`
    - **Validates: Requirements 4.3**
  - [x] 8.7 Property 7: Producto inexistente devuelve 404 en todos los endpoints
    - Generar `productId` aleatorios no existentes; verificar 404 en POST, POST /upload, GET, PUT, DELETE
    - `// Feature: product-images, Property 7: Producto inexistente devuelve 404 en todos los endpoints`
    - **Validates: Requirements 1.5**
  - [x] 8.8 Property 8: Imagen que no pertenece al producto devuelve 404
    - Crear imagen bajo producto A; intentar PUT/DELETE vía producto B; verificar 404
    - `// Feature: product-images, Property 8: Imagen que no pertenece al producto devuelve 404`
    - **Validates: Requirements 2.4**
  - [x] 8.9 Property 9: Round-trip de creación de imágenes (endpoint JSON existente)
    - Generar productos y listas de URLs válidas; POST JSON + GET; verificar que las URLs devueltas coinciden exactamente
    - `// Feature: product-images, Property 9: Round-trip de creación de imágenes (endpoint JSON existente)`
    - **Validates: Requirements 1.1**
  - [x] 8.10 Property 10: Imágenes ordenadas por displayOrder ascendente
    - Generar productos con imágenes de `displayOrder` aleatorio; GET; verificar orden no decreciente
    - `// Feature: product-images, Property 10: Imágenes ordenadas por displayOrder ascendente`
    - **Validates: Requirements 1.3**

- [x] 9. Checkpoint final — Verificar que todos los tests pasan
  - Ejecutar `./mvnw test -Dtest='!*IT,!*IntegrationTest'` y confirmar que todo está en verde. Preguntar al usuario si surgen dudas.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Property-based tests (task 8) require Docker for integration variants — skip if Docker is unavailable
- `@WebMvcTest` tests (task 6.4) use a mocked `JwtDecoder` and do not require Docker
- Unit tests (tasks 3.4, 5.4) use Mockito only — no Docker needed
- El endpoint JSON existente `POST /admin/api/products/{productId}/images` no se modifica
- `cloudinaryPublicId` es nullable para compatibilidad con imágenes creadas antes de este feature
- Si Cloudinary falla en delete, se loguea y la eliminación de BD se completa igualmente (HTTP 204)
