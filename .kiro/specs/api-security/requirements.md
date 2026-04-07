# Requirements Document

## Introduction

Este documento define los requisitos para agregar seguridad basada en JWT al servicio `products`. La API expone endpoints de lectura públicos (`GET /api/products`, `GET /api/products/{id}`) y endpoints de escritura protegidos (`POST`, `PUT`, `DELETE`) que requieren un JWT válido firmado con RSA. El servicio actúa como Resource Server: valida los tokens con su propia clave pública RSA sin depender de un proveedor OAuth externo. Adicionalmente, se configura CORS para permitir llamadas desde páginas estáticas públicas.

## Glossary

- **Security_Filter_Chain**: El bean `SecurityFilterChain` de Spring Security que define las reglas de autorización HTTP y el filtro JWT.
- **JWT_Validator**: El componente que valida la firma, expiración y estructura de los tokens JWT usando la clave pública RSA.
- **RSA_Key_Pair**: El par de claves RSA (pública/privada) almacenado en `src/main/resources/certs/`. La clave privada firma los tokens; la clave pública los verifica.
- **Resource_Server**: El rol que cumple el servicio `products`: valida JWTs entrantes pero no los emite en flujo OAuth estándar.
- **CORS_Config**: La configuración de Cross-Origin Resource Sharing que permite peticiones desde orígenes externos definidos.
- **Protected_Endpoint**: Cualquier endpoint que requiere un JWT válido en el header `Authorization: Bearer <token>` (`POST`, `PUT`, `DELETE /api/products`).
- **Public_Endpoint**: Cualquier endpoint accesible sin autenticación (`GET /api/products`, `GET /api/products/{id}`).
- **Bearer_Token**: El JWT incluido en el header HTTP `Authorization` con el esquema `Bearer`.

---

## Requirements

### Requirement 1: Acceso público a endpoints de lectura

**User Story:** As an API client, I want to retrieve products without providing credentials, so that I can display the product catalogue from a public page.

#### Acceptance Criteria

1. WHEN a `GET /api/products` request is received without an `Authorization` header, THE Security_Filter_Chain SHALL allow the request and return a `200 OK` response.
2. WHEN a `GET /api/products/{id}` request is received without an `Authorization` header, THE Security_Filter_Chain SHALL allow the request and return a `200 OK` response.
3. WHEN a `GET /api/products` request is received with an invalid or expired JWT, THE Security_Filter_Chain SHALL allow the request and return a `200 OK` response.

---

### Requirement 2: Protección de endpoints de escritura con JWT

**User Story:** As a system administrator, I want write operations to require a valid JWT, so that only authorized clients can modify the product catalogue.

#### Acceptance Criteria

1. WHEN a `POST /api/products` request is received with a valid Bearer JWT signed with the RSA private key, THE Security_Filter_Chain SHALL allow the request to reach the Controller.
2. WHEN a `PUT /api/products/{id}` request is received with a valid Bearer JWT signed with the RSA private key, THE Security_Filter_Chain SHALL allow the request to reach the Controller.
3. WHEN a `DELETE /api/products/{id}` request is received with a valid Bearer JWT signed with the RSA private key, THE Security_Filter_Chain SHALL allow the request to reach the Controller.
4. WHEN a `POST /api/products` request is received without an `Authorization` header, THE Security_Filter_Chain SHALL return a `401 Unauthorized` response without invoking the Controller.
5. WHEN a `PUT /api/products/{id}` request is received without an `Authorization` header, THE Security_Filter_Chain SHALL return a `401 Unauthorized` response without invoking the Controller.
6. WHEN a `DELETE /api/products/{id}` request is received without an `Authorization` header, THE Security_Filter_Chain SHALL return a `401 Unauthorized` response without invoking the Controller.

---

### Requirement 3: Validación de JWT con clave pública RSA

**User Story:** As a developer, I want the service to validate JWTs using a local RSA public key, so that no external OAuth provider is needed.

#### Acceptance Criteria

1. WHEN a Bearer JWT is received on a Protected_Endpoint, THE JWT_Validator SHALL verify the token signature using the RSA public key loaded from `src/main/resources/certs/public.pem`.
2. WHEN a Bearer JWT has an expired `exp` claim, THE JWT_Validator SHALL reject the token and THE Security_Filter_Chain SHALL return a `401 Unauthorized` response.
3. WHEN a Bearer JWT has a signature that does not match the RSA public key, THE JWT_Validator SHALL reject the token and THE Security_Filter_Chain SHALL return a `401 Unauthorized` response.
4. WHEN a Bearer JWT has a malformed structure (not three Base64url-encoded segments), THE JWT_Validator SHALL reject the token and THE Security_Filter_Chain SHALL return a `401 Unauthorized` response.
5. THE JWT_Validator SHALL use the RSA public key configured via `spring.security.oauth2.resourceserver.jwt.public-key-location` in `application.yaml`.

---

### Requirement 4: Almacenamiento del par de llaves RSA

**User Story:** As a developer, I want the RSA key pair stored in the project under a dedicated directory, so that the service can sign and verify JWTs without an external key store.

#### Acceptance Criteria

1. THE RSA_Key_Pair SHALL be stored as PEM files in `src/main/resources/certs/`: `private.pem` (clave privada PKCS#8) y `public.pem` (clave pública X.509).
2. THE RSA_Key_Pair SHALL use a minimum key size of 2048 bits.
3. IF the `public.pem` file is absent at application startup, THEN THE Resource_Server SHALL fail to start and log a descriptive error indicating the missing key.
4. THE `private.pem` file SHALL be listed in `.gitignore` to prevent accidental exposure in version control.

---

### Requirement 5: Configuración CORS

**User Story:** As a frontend developer, I want the API to accept cross-origin requests from a public static page, so that the product catalogue can be consumed from a browser.

#### Acceptance Criteria

1. THE CORS_Config SHALL allow HTTP methods `GET`, `POST`, `PUT`, `DELETE`, and `OPTIONS` for the path pattern `/api/**`.
2. THE CORS_Config SHALL allow the `Authorization` and `Content-Type` headers in cross-origin requests.
3. THE CORS_Config SHALL expose the `Authorization` header to cross-origin responses.
4. WHEN a preflight `OPTIONS` request is received, THE Security_Filter_Chain SHALL return a `200 OK` response without requiring authentication.
5. THE CORS_Config SHALL read the list of allowed origins from the configuration property `security.cors.allowed-origins` in `application.yaml`, so that origins can be changed without recompiling.

---

### Requirement 6: Desactivación de CSRF

**User Story:** As a developer, I want CSRF protection disabled for the stateless REST API, so that JWT-authenticated clients are not blocked by CSRF token requirements.

#### Acceptance Criteria

1. THE Security_Filter_Chain SHALL disable CSRF protection, given that the API is stateless and uses Bearer token authentication.
2. THE Security_Filter_Chain SHALL not create or use HTTP sessions for authentication state (session creation policy: `STATELESS`).

---

### Requirement 7: Dependencia spring-boot-starter-oauth2-resource-server

**User Story:** As a developer, I want the OAuth2 Resource Server starter included in the build, so that Spring Security's JWT support is available.

#### Acceptance Criteria

1. THE `pom.xml` SHALL declare `spring-boot-starter-oauth2-resource-server` as a compile-scope dependency under `org.springframework.boot`.
2. WHEN the application starts with the dependency present and a valid `public.pem`, THE Resource_Server SHALL initialize the JWT decoder bean without errors.

---

### Requirement 8: Respuestas de error de seguridad consistentes

**User Story:** As an API client, I want security errors to return standard HTTP status codes, so that I can handle authentication failures programmatically.

#### Acceptance Criteria

1. WHEN a Protected_Endpoint is accessed without a Bearer token, THE Security_Filter_Chain SHALL return `401 Unauthorized` with a `WWW-Authenticate: Bearer` header.
2. WHEN a Protected_Endpoint is accessed with an invalid or expired Bearer token, THE Security_Filter_Chain SHALL return `401 Unauthorized` with a `WWW-Authenticate: Bearer` header containing an error description.
3. IF a valid JWT is present but the request targets a resource the token is not authorized for, THEN THE Security_Filter_Chain SHALL return `403 Forbidden`.
