# Design Document: product-active-status

## Overview

Se agrega un campo booleano `active` a la entidad `Product` para controlar la visibilidad de productos en el catálogo. Los endpoints públicos (`/api/products`) retornan un `PublicProductResponse` que omite el campo, mientras que los endpoints de administración (`/admin/api/products`) retornan un `AdminProductResponse` que lo incluye. La migración de Liquibase garantiza que todos los productos existentes queden con `active = true`.

El cambio es aditivo y no rompe compatibilidad: los clientes existentes del endpoint público no ven ningún campo nuevo, y los clientes admin obtienen visibilidad completa del estado de activación.

## Architecture

El flujo sigue la arquitectura en capas existente sin introducir nuevas capas:

```
HTTP Request
    │
    ▼
Controller (ProductController / PublicProductController)
    │  usa AdminProductResponse / PublicProductResponse
    ▼
ProductService (interface)
    │  createAdmin() / findAllAdmin() / findByIdAdmin() / updateAdmin()
    │  findAll() / findById()  ← retornan PublicProductResponse
    ▼
ProductServiceImpl
    │  toAdminResponse(Product) / toPublicResponse(Product)
    ▼
ProductRepository (JPA)
    │
    ▼
products table (PostgreSQL)
    └── columna `active BOOLEAN NOT NULL DEFAULT true`
```

`BulkImportServiceImpl` sigue usando `ProductService` internamente; el único cambio es que el resultado de cada fila exitosa pasa a ser `AdminProductResponse`.

## Components and Interfaces

### Product (entidad JPA)
Agrega el campo:
```java
@Column(nullable = false)
@Builder.Default
private boolean active = true;
```

### ProductRequest (DTO de entrada)
Agrega campo opcional:
```java
@Builder.Default
private boolean active = true;
```

### AdminProductResponse (nuevo DTO)
Incluye todos los campos de `ProductResponse` más `active`:
```java
Long id, String name, String description, BigDecimal price,
List<ImageResponse> images, List<TagResponse> tags, boolean active
```

### PublicProductResponse (nuevo DTO — reemplaza el uso de `ProductResponse` en endpoints públicos)
Idéntico a `ProductResponse` actual (sin `active`):
```java
Long id, String name, String description, BigDecimal price,
List<ImageResponse> images, List<TagResponse> tags
```

> `ProductResponse` existente puede mantenerse o renombrarse a `PublicProductResponse`. La opción más limpia es crear `PublicProductResponse` como clase nueva y dejar `ProductResponse` para compatibilidad interna hasta que se migre completamente.

### ProductService (interfaz)
Nuevos métodos admin; los métodos públicos cambian su tipo de retorno:

```java
// Admin
AdminProductResponse createAdmin(ProductRequest request);
PageResponse<AdminProductResponse> findAllAdmin(int page, int size, Set<Long> tagIds);
AdminProductResponse findByIdAdmin(Long id);
AdminProductResponse updateAdmin(Long id, ProductRequest request);
AdminProductResponse setTagsAdmin(Long productId, Set<Long> tagIds);

// Public (retorno cambia a PublicProductResponse)
PublicProductResponse findById(Long id);
PageResponse<PublicProductResponse> findAll(int page, int size, Set<Long> tagIds);
```

> Los métodos `delete` y `setTags` no retornan un DTO de producto visible, por lo que `delete` no cambia. `setTags` se duplica en `setTagsAdmin`.

### ProductServiceImpl
Agrega dos métodos de mapeo privados:
- `toAdminResponse(Product)` → `AdminProductResponse` (incluye `active`)
- `toPublicResponse(Product)` → `PublicProductResponse` (excluye `active`)

### ProductController (admin)
Actualiza todos los métodos para usar `AdminProductResponse` y los métodos `*Admin` del servicio.

### PublicProductController
Actualiza todos los métodos para usar `PublicProductResponse` y los métodos públicos del servicio.

### BulkImportServiceImpl
- `ProductRequest` se construye con `active = true` explícito (ya es el default, pero se hace explícito).
- El resultado de cada fila exitosa usa `AdminProductResponse` en lugar de `ProductResponse`.

### ProductImageController
No retorna `ProductResponse` — retorna `ImageResponse`. No requiere cambios funcionales. El requisito 4.2 se satisface porque el contexto del producto (incluyendo `active`) es accesible vía `GET /admin/api/products/{id}`.

### Liquibase Migration (008-add-product-active-column.yaml)
```yaml
changeSet id: 008-add-product-active-column
changes:
  - addColumn: active BOOLEAN NOT NULL DEFAULT true
  - update: SET active = true WHERE active IS NULL  (precaución para DBs sin default aplicado)
```

## Data Models

### Tabla `products` (tras migración)

| Columna      | Tipo           | Restricciones              |
|--------------|----------------|----------------------------|
| id           | BIGSERIAL      | PK, NOT NULL               |
| name         | VARCHAR(255)   | NOT NULL                   |
| description  | TEXT           | nullable                   |
| price        | NUMERIC(19,2)  | nullable                   |
| active       | BOOLEAN        | NOT NULL, DEFAULT true     |

### AdminProductResponse

```json
{
  "id": 1,
  "name": "Producto A",
  "description": "...",
  "price": 9.99,
  "images": [],
  "tags": [],
  "active": true
}
```

### PublicProductResponse

```json
{
  "id": 1,
  "name": "Producto A",
  "description": "...",
  "price": 9.99,
  "images": [],
  "tags": []
}
```

### ProductRequest (actualizado)

```json
{
  "name": "Producto A",
  "description": "...",
  "price": 9.99,
  "active": true
}
```
El campo `active` es opcional; si se omite, el valor por defecto es `true`.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Default active en creación

*For any* `ProductRequest` que no especifique el campo `active` (o lo omita), el `Product` creado por `ProductService` SHALL tener `active = true`.

**Validates: Requirements 1.2, 3.1, 4.5**

---

### Property 2: El valor explícito de `active` se preserva

*For any* `ProductRequest` con un valor explícito de `active` (true o false), el producto creado o actualizado por `ProductService` SHALL tener exactamente ese valor en el campo `active`.

**Validates: Requirements 4.3, 4.4**

---

### Property 3: Round-trip de `active` en AdminProductResponse

*For any* `Product` con cualquier valor de `active`, mapearlo a `AdminProductResponse` y leer el campo `active` del resultado SHALL retornar el mismo valor que tenía el `Product` original.

**Validates: Requirements 4.1, 6.1, 6.5**

---

### Property 4: PublicProductResponse nunca expone `active`

*For any* `Product`, mapearlo a `PublicProductResponse` y serializar el resultado a JSON SHALL producir un objeto que no contiene la clave `active`.

**Validates: Requirements 5.1, 6.2**

---

### Property 5: BulkImport crea productos con `active = true`

*For any* fila CSV válida procesada por `BulkImportService`, el producto creado SHALL tener `active = true`.

**Validates: Requirements 3.2, 7.1**

---

### Property 6: Import result refleja el estado `active` del producto

*For any* importación exitosa de una fila CSV, el `AdminProductResponse` retornado en el resultado SHALL tener `active = true` (ya que BulkImport siempre crea con `active = true`).

**Validates: Requirements 7.2**

> Nota: Property 6 es consecuencia directa de Property 5 + Property 3. Se mantiene como propiedad separada para trazabilidad explícita con el requisito 7.2.

## Error Handling

| Escenario | Comportamiento |
|-----------|---------------|
| `ProductRequest` con `active` ausente | Se usa `true` como valor por defecto (lógica en `ProductRequest` con `@Builder.Default`) |
| Migración Liquibase falla al arrancar | Spring Boot falla en startup — comportamiento estándar de Liquibase |
| `findByIdAdmin` con id inexistente | Lanza `ProductNotFoundException` → 404 (igual que el comportamiento actual) |
| `updateAdmin` con id inexistente | Lanza `ProductNotFoundException` → 404 |

No se introducen nuevos tipos de error. El campo `active` no tiene restricciones de validación adicionales más allá de ser booleano.

## Testing Strategy

### Unit Tests (JUnit 5 + Mockito)

Cubren casos concretos y condiciones de borde:

- `ProductServiceImplTest`: verificar que `createAdmin` con `active=false` persiste `false`; que `updateAdmin` actualiza el campo `active`; que `toPublicResponse` no incluye `active`.
- `BulkImportServiceImplTest`: verificar que cada fila exitosa produce un `AdminProductResponse` con `active = true`.
- Serialización JSON: verificar que `PublicProductResponse` serializado no contiene la clave `"active"`.

### Property-Based Tests (jqwik)

El proyecto ya usa **jqwik** (evidenciado por `.jqwik-database` en la raíz). Se usará jqwik para los property tests.

Cada property test debe ejecutarse con mínimo 100 iteraciones (configuración por defecto de jqwik).

Formato de tag: `Feature: product-active-status, Property {N}: {texto}`

#### Property 1 — Default active en creación
```java
// Feature: product-active-status, Property 1: Default active en creación
@Property
void createWithoutActiveSetsActiveTrue(@ForAll @Valid ProductRequest request) {
    // request generado sin campo active (usa default true)
    AdminProductResponse response = service.createAdmin(request);
    assertThat(response.isActive()).isTrue();
}
```

#### Property 2 — Valor explícito de `active` se preserva
```java
// Feature: product-active-status, Property 2: El valor explícito de active se preserva
@Property
void explicitActiveValueIsPreserved(@ForAll boolean active, @ForAll @Valid ProductRequest base) {
    ProductRequest request = base.toBuilder().active(active).build();
    AdminProductResponse response = service.createAdmin(request);
    assertThat(response.isActive()).isEqualTo(active);
}
```

#### Property 3 — Round-trip de `active` en AdminProductResponse
```java
// Feature: product-active-status, Property 3: Round-trip de active en AdminProductResponse
@Property
void adminResponseActiveRoundTrip(@ForAll boolean active) {
    Product product = Product.builder().name("test").active(active).build();
    AdminProductResponse response = toAdminResponse(product);
    assertThat(response.isActive()).isEqualTo(active);
}
```

#### Property 4 — PublicProductResponse nunca expone `active`
```java
// Feature: product-active-status, Property 4: PublicProductResponse nunca expone active
@Property
void publicResponseNeverExposesActive(@ForAll boolean active) throws Exception {
    Product product = Product.builder().name("test").active(active).build();
    PublicProductResponse response = toPublicResponse(product);
    String json = objectMapper.writeValueAsString(response);
    assertThat(json).doesNotContain("\"active\"");
}
```

#### Property 5 — BulkImport crea productos con `active = true`
```java
// Feature: product-active-status, Property 5: BulkImport crea productos con active = true
@Property
void bulkImportCreatesProductsWithActiveTrue(@ForAll @ValidCsvRow String csvRow) {
    ImportResult result = bulkImportService.importProducts(toFile(csvRow));
    result.getRows().stream()
        .filter(r -> r.getStatus() == RowStatus.SUCCESS)
        .forEach(r -> {
            AdminProductResponse product = service.findByIdAdmin(r.getProductId());
            assertThat(product.isActive()).isTrue();
        });
}
```

### Integration Tests (Testcontainers)

- `ProductControllerIT`: verifica que `POST /admin/api/products` retorna `AdminProductResponse` con `active`; que `GET /api/products` retorna respuestas sin `active`.
- `LiquibaseMigrationIT`: verifica que la columna `active` existe y tiene `DEFAULT true` tras la migración.
