# Design Document: firebase-auth-integration

## Overview

Esta feature migra el mecanismo de autenticación JWT del backend desde validación con llave pública RSA local (`public.pem` + `NimbusJwtDecoder.withPublicKey`) hacia validación contra el JWKS público de Firebase Authentication. Adicionalmente, incorpora un nuevo endpoint de administración para asignar roles a usuarios de Firebase mediante el Firebase Admin SDK.

El cambio es principalmente de configuración en `SecurityConfig.java`: se reemplaza el `JwtDecoder` basado en llave estática por uno que consulta el JWKS endpoint de Firebase, añadiendo validadores de `iss` y `aud`. La extracción de roles desde el claim `roles` del JWT se mantiene sin cambios. Se agrega un nuevo componente `FirebaseConfig` para inicializar el `FirebaseApp` como bean de Spring, y una capa controller/service para el endpoint `POST /admin/api/users/{uid}/roles`.

## Architecture

```mermaid
graph TD
    Client -->|Bearer JWT| SecurityFilterChain
    SecurityFilterChain -->|validate| JwtDecoder
    JwtDecoder -->|fetch JWKS| Firebase_JWKS_Endpoint["Firebase JWKS\nhttps://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"]
    JwtDecoder -->|validate iss+aud| DelegatingOAuth2TokenValidator
    SecurityFilterChain -->|extract roles| JwtAuthenticationConverter
    JwtAuthenticationConverter -->|roles claim| SecurityContext

    AdminClient -->|POST /admin/api/users/{uid}/roles| RoleManagementController
    RoleManagementController --> RoleManagementService
    RoleManagementService -->|setCustomUserClaims| FirebaseAuth
    FirebaseAuth -->|Admin SDK| Firebase_Project["Firebase Project"]

    FirebaseConfig -->|initialize| FirebaseApp
    FirebaseApp --> FirebaseAuth
```

### Decisiones de diseño

- **NimbusJwtDecoder.withJwkSetUri**: Spring Security cachea y refresca las claves JWKS automáticamente, eliminando la necesidad de gestión manual de llaves.
- **DelegatingOAuth2TokenValidator**: Permite componer validadores de `iss` y `aud` de forma declarativa sobre el decoder existente.
- **ApplicationDefault credentials**: `FirebaseApp` se inicializa con `GoogleCredentials.getApplicationDefault()`, lo que permite usar una variable de entorno `GOOGLE_APPLICATION_CREDENTIALS` apuntando a un service account JSON, compatible con entornos locales, CI y cloud.
- **Interfaz + implementación para RoleManagementService**: Sigue el patrón existente del proyecto (`ProductService`/`ProductServiceImpl`) y facilita el mocking en tests.
- **FirebaseUserNotFoundException**: Extiende `RuntimeException` y se integra en `GlobalExceptionHandler` igual que `ProductNotFoundException`, retornando 404.

## Components and Interfaces

### FirebaseConfig

Nuevo bean de configuración en `com.example.products.config`.

```java
@Configuration
public class FirebaseConfig {
    @Value("${firebase.project-id}")
    private String projectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build();
            return FirebaseApp.initializeApp(options);
        }
        return FirebaseApp.getInstance();
    }
}
```

### SecurityConfig (modificado)

Se elimina la inyección de `RSAPublicKey` y el método `jwtDecoder(RSAPublicKey)`. Se reemplaza por:

```java
@Bean
public JwtDecoder jwtDecoder(@Value("${firebase.project-id}") String projectId) {
    String jwksUri = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
    String issuer  = "https://securetoken.google.com/" + projectId;

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();

    OAuth2TokenValidator<Jwt> issuerValidator   = new JwtIssuerValidator(issuer);
    OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<>(
        JwtClaimNames.AUD, (aud) -> aud != null && ((List<?>) aud).contains(projectId));
    OAuth2TokenValidator<Jwt> combined = new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefault(), issuerValidator, audienceValidator);

    decoder.setJwtValidator(combined);
    return decoder;
}
```

`JwtAuthenticationConverter` no cambia: sigue leyendo el claim `roles` y prefijando con `ROLE_`.

### RoleManagementController

`com.example.products.controller.RoleManagementController`

- `POST /admin/api/users/{uid}/roles`
- Recibe `@RequestBody @Valid RoleRequest`
- Delega a `RoleManagementService`
- Retorna `ResponseEntity<Void>` con HTTP 200

### RoleManagementService / RoleManagementServiceImpl

`com.example.products.service.RoleManagementService` (interfaz)  
`com.example.products.service.RoleManagementServiceImpl` (implementación)

```java
public interface RoleManagementService {
    void assignRoles(String uid, List<String> roles);
}
```

`RoleManagementServiceImpl` usa `FirebaseAuth.getInstance()` para llamar a `setCustomUserClaims(uid, Map.of("roles", roles))`. Si el uid no existe, Firebase lanza `FirebaseAuthException` con código `USER_NOT_FOUND`, que se traduce a `FirebaseUserNotFoundException`.

### FirebaseUserNotFoundException

`com.example.products.exception.FirebaseUserNotFoundException`

```java
public class FirebaseUserNotFoundException extends RuntimeException {
    public FirebaseUserNotFoundException(String uid) {
        super("Firebase user not found: " + uid);
    }
}
```

Se registra en `GlobalExceptionHandler` con el mismo patrón que `ProductNotFoundException` → HTTP 404.

### RoleRequest (DTO)

`com.example.products.model.RoleRequest`

```java
@Data
public class RoleRequest {
    @NotNull
    @NotEmpty
    private List<@NotBlank String> roles;
}
```

## Data Models

No se introducen nuevas entidades JPA ni cambios de esquema de base de datos. Los datos relevantes son:

### Configuración (application.yaml)

| Propiedad | Descripción | Ejemplo |
|---|---|---|
| `firebase.project-id` | Firebase project ID | `my-firebase-project` |

Se elimina:
```yaml
spring.security.oauth2.resourceserver.jwt.public-key-location
```

Se añade:
```yaml
firebase:
  project-id: ${FIREBASE_PROJECT_ID}
```

### JWT Claims esperados

| Claim | Tipo | Descripción |
|---|---|---|
| `iss` | String | `https://securetoken.google.com/{project-id}` |
| `aud` | String/Array | Firebase project-id |
| `roles` | List\<String\> | Roles del usuario (e.g. `["ADMIN"]`) |
| `sub` | String | UID del usuario Firebase |

### Custom Claims escritos por RoleManagementService

```json
{ "roles": ["ADMIN", "USER"] }
```


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Tokens con firma inválida son rechazados

*Para cualquier* token JWT cuya firma no corresponda a ninguna clave del JWKS de Firebase, el `JwtDecoder` debe lanzar una excepción de autenticación y rechazar el token.

**Validates: Requirements 1.1**

---

### Property 2: Tokens con issuer incorrecto son rechazados

*Para cualquier* token JWT cuyo claim `iss` no sea exactamente `https://securetoken.google.com/{project-id}`, el `JwtDecoder` debe rechazarlo con error de validación. Para cualquier token con el `iss` correcto (y firma válida), debe aceptarlo.

**Validates: Requirements 2.1, 2.3**

---

### Property 3: Tokens con audience incorrecto son rechazados

*Para cualquier* token JWT cuyo claim `aud` no contenga el Firebase project-id configurado, el `JwtDecoder` debe rechazarlo con error de validación. Para cualquier token con el `aud` correcto (y firma válida), debe aceptarlo.

**Validates: Requirements 2.2, 2.4**

---

### Property 4: Extracción de roles con prefijo ROLE_

*Para cualquier* JWT con un claim `roles` que contenga una lista de strings, el `JwtAuthenticationConverter` debe retornar una colección de `GrantedAuthority` donde cada elemento es `ROLE_` concatenado con el valor original. Si el claim `roles` está ausente o es nulo, debe retornar una colección vacía sin lanzar excepción.

**Validates: Requirements 4.1, 4.2, 4.3**

---

### Property 5: Delegación correcta de roles al service

*Para cualquier* uid válido y lista de roles no vacía recibida en el body del request, el `RoleManagementController` debe invocar `RoleManagementService.assignRoles(uid, roles)` con exactamente los mismos valores recibidos, y el service debe llamar a Firebase Admin SDK con el mapa `{"roles": roles}`.

**Validates: Requirements 6.3, 6.4**

---

### Property 6: UID inexistente resulta en HTTP 404

*Para cualquier* uid que no corresponda a un usuario existente en Firebase, la llamada a `POST /admin/api/users/{uid}/roles` debe retornar HTTP 404 con un cuerpo `ErrorResponse` que contenga un mensaje descriptivo.

**Validates: Requirements 6.6**

---

### Property 7: Error del Firebase Admin SDK resulta en HTTP 500

*Para cualquier* error inesperado retornado por el Firebase Admin SDK al intentar establecer custom claims (distinto de USER_NOT_FOUND), la llamada al endpoint debe retornar HTTP 500 con un cuerpo `ErrorResponse`.

**Validates: Requirements 6.7**

---

## Error Handling

| Escenario | Excepción | HTTP | Manejado en |
|---|---|---|---|
| Token con firma inválida | `JwtException` (Spring Security) | 401 | Spring Security filter chain |
| Token con `iss` inválido | `JwtValidationException` | 401 | Spring Security filter chain |
| Token con `aud` inválido | `JwtValidationException` | 401 | Spring Security filter chain |
| Token ausente en request protegido | — | 401 | Spring Security filter chain |
| Acceso a `/admin/api/**` sin `ROLE_ADMIN` | `AuthorizationDeniedException` | 403 | `GlobalExceptionHandler` (ya existente) |
| UID no encontrado en Firebase | `FirebaseUserNotFoundException` | 404 | `GlobalExceptionHandler` (nuevo handler) |
| Error del Firebase Admin SDK | `FirebaseAuthException` envuelta en `RuntimeException` | 500 | `GlobalExceptionHandler` (handler genérico existente) |
| `firebase.project-id` no configurado | `BeanCreationException` en arranque | — | Fallo de arranque de la aplicación |

`FirebaseUserNotFoundException` se integra en `GlobalExceptionHandler` con el mismo patrón que `ProductNotFoundException`:

```java
@ExceptionHandler(FirebaseUserNotFoundException.class)
public ResponseEntity<ErrorResponse> handleFirebaseUserNotFound(FirebaseUserNotFoundException ex) {
    ErrorResponse body = ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
}
```

## Testing Strategy

### Enfoque dual: unit tests + property-based tests

Se usa **jqwik** (ya presente en `pom.xml`) como librería de property-based testing, con mínimo 100 iteraciones por propiedad.

### Unit Tests (JUnit 5 + Mockito)

Ubicación: `src/test/java/com/example/products/`

- `SecurityConfigTest`: verifica que el bean `JwtDecoder` usa JWKS URI (no llave pública), y que el issuer construido contiene el project-id configurado. Verifica que `firebase.project-id` ausente falla el arranque.
- `RoleManagementControllerTest` (MockMvc): verifica que `POST /admin/api/users/{uid}/roles` sin `ROLE_ADMIN` retorna 403; con `ROLE_ADMIN` y body válido retorna 200; con uid inexistente retorna 404.
- `RoleManagementServiceImplTest` (Mockito): verifica que `assignRoles` llama a `FirebaseAuth` con el mapa correcto; que `FirebaseAuthException` con `USER_NOT_FOUND` lanza `FirebaseUserNotFoundException`; que otros errores del SDK se propagan como `RuntimeException`.
- `GlobalExceptionHandlerTest`: verifica que `FirebaseUserNotFoundException` retorna 404 con `ErrorResponse`.

### Property-Based Tests (jqwik)

Ubicación: `src/test/java/com/example/products/`

Cada test usa la anotación `@Property(tries = 100)` y referencia la propiedad del diseño en un comentario:
`// Feature: firebase-auth-integration, Property N: <texto>`

**Property 4 — Extracción de roles con prefijo ROLE_**
```java
// Feature: firebase-auth-integration, Property 4: Extracción de roles con prefijo ROLE_
@Property(tries = 100)
void rolesClaimIsPrefixedWithROLE_(@ForAll List<@AlphaChars @StringLength(min=1, max=20) String> roles) {
    Jwt jwt = buildJwtWithRoles(roles);
    Collection<GrantedAuthority> authorities = converter.convert(jwt);
    assertThat(authorities).hasSize(roles.size());
    assertThat(authorities).allMatch(a -> a.getAuthority().startsWith("ROLE_"));
}
```

**Property 5 — Delegación correcta al service**
```java
// Feature: firebase-auth-integration, Property 5: Delegación correcta de roles al service
@Property(tries = 100)
void controllerDelegatesExactRolesToService(
        @ForAll @StringLength(min=1, max=28) String uid,
        @ForAll @Size(min=1, max=5) List<@AlphaChars @StringLength(min=1, max=20) String> roles) {
    // Verifica que service.assignRoles es llamado con uid y roles exactos del request
}
```

**Property 6 — UID inexistente resulta en 404**
```java
// Feature: firebase-auth-integration, Property 6: UID inexistente resulta en HTTP 404
@Property(tries = 100)
void unknownUidReturns404(@ForAll @StringLength(min=1, max=28) String uid) {
    // Mockea FirebaseAuth para lanzar USER_NOT_FOUND para cualquier uid
    // Verifica que el endpoint retorna 404 con ErrorResponse
}
```

**Property 7 — Error del SDK resulta en 500**
```java
// Feature: firebase-auth-integration, Property 7: Error del SDK resulta en HTTP 500
@Property(tries = 100)
void sdkErrorReturns500(@ForAll @StringLength(min=1, max=28) String uid) {
    // Mockea FirebaseAuth para lanzar error genérico
    // Verifica que el endpoint retorna 500 con ErrorResponse
}
```

### Integración

Los tests de integración existentes (`SecurityConfigIT`, `ProductsApplicationIT`) deben actualizarse para usar tokens JWT firmados con una clave de test mockeada en lugar de `public.pem`. Se puede usar `@MockBean` para el `JwtDecoder` en tests que no validan la lógica de autenticación, o configurar un WireMock del JWKS endpoint para tests end-to-end.
