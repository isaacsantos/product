# Implementation Plan: firebase-auth-integration

## Overview

Migración del mecanismo de autenticación JWT desde llave RSA local hacia Firebase JWKS, más un nuevo endpoint de gestión de roles vía Firebase Admin SDK.

## Tasks

- [x] 1. Agregar dependencia firebase-admin al pom.xml
  - Agregar `com.google.firebase:firebase-admin` en `pom.xml` (scope compile, sin version fija — usar la más reciente estable, e.g. 9.x)
  - _Requirements: 6.8_

- [x] 2. Actualizar application.yaml
  - Eliminar la propiedad `spring.security.oauth2.resourceserver.jwt.public-key-location`
  - Agregar la propiedad `firebase.project-id: ${FIREBASE_PROJECT_ID}`
  - _Requirements: 3.1, 5.2_

- [x] 3. Crear FirebaseConfig.java
  - Crear `src/main/java/com/example/products/config/FirebaseConfig.java`
  - Bean `@Configuration` con método `@Bean firebaseApp()` que inicializa `FirebaseApp` con `GoogleCredentials.getApplicationDefault()` y el `projectId` leído de `@Value("${firebase.project-id}")`
  - Usar guard `FirebaseApp.getApps().isEmpty()` para evitar doble inicialización
  - _Requirements: 6.9_

- [x] 4. Modificar SecurityConfig.java — reemplazar JwtDecoder
  - Eliminar la inyección de `RSAPublicKey` y el import de `java.security.interfaces.RSAPublicKey`
  - Reemplazar el método `jwtDecoder(RSAPublicKey)` por `jwtDecoder(@Value("${firebase.project-id}") String projectId)` usando `NimbusJwtDecoder.withJwkSetUri(jwksUri)`
  - Componer `DelegatingOAuth2TokenValidator` con `JwtValidators.createDefault()`, `JwtIssuerValidator` y `JwtClaimValidator` para `aud`
  - El `JwtAuthenticationConverter` existente no cambia
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 2.5, 3.3, 4.1, 4.2, 4.3, 5.1, 5.4_

- [x] 5. Crear FirebaseUserNotFoundException.java
  - Crear `src/main/java/com/example/products/exception/FirebaseUserNotFoundException.java`
  - Extiende `RuntimeException`, constructor recibe `String uid` y pasa `"Firebase user not found: " + uid` al super
  - _Requirements: 6.6_

- [x] 6. Agregar handler en GlobalExceptionHandler.java
  - Agregar método `handleFirebaseUserNotFound(FirebaseUserNotFoundException ex)` anotado con `@ExceptionHandler`
  - Retorna `ResponseEntity<ErrorResponse>` con HTTP 404, siguiendo el mismo patrón que `handleProductNotFound`
  - _Requirements: 6.6_

- [x] 7. Crear RoleRequest.java
  - Crear `src/main/java/com/example/products/model/RoleRequest.java`
  - DTO con campo `List<@NotBlank String> roles` anotado con `@NotNull` y `@NotEmpty`
  - Usar Lombok `@Data`
  - _Requirements: 6.3_

- [x] 8. Crear RoleManagementService e implementación
  - [x] 8.1 Crear interfaz `src/main/java/com/example/products/service/RoleManagementService.java`
    - Método `void assignRoles(String uid, List<String> roles)`
    - _Requirements: 6.4_
  - [x] 8.2 Crear `src/main/java/com/example/products/service/RoleManagementServiceImpl.java`
    - Anotada con `@Service`, inyecta `FirebaseApp` (o usa `FirebaseAuth.getInstance()`)
    - Llama a `FirebaseAuth.getInstance().setCustomUserClaims(uid, Map.of("roles", roles))`
    - Captura `FirebaseAuthException`: si el código es `USER_NOT_FOUND` lanza `FirebaseUserNotFoundException(uid)`, de lo contrario relanza como `RuntimeException`
    - _Requirements: 6.4, 6.6, 6.7_
  - [x] 8.3 Escribir unit tests para RoleManagementServiceImpl
    - Clase `src/test/java/com/example/products/service/RoleManagementServiceImplTest.java` con `@ExtendWith(MockitoExtension.class)`
    - Test: `assignRoles` llama a `FirebaseAuth` con el mapa `{"roles": roles}` correcto
    - Test: `FirebaseAuthException` con código `USER_NOT_FOUND` lanza `FirebaseUserNotFoundException`
    - Test: otros errores del SDK se propagan como `RuntimeException`
    - _Requirements: 6.4, 6.6, 6.7_

- [x] 9. Crear RoleManagementController.java
  - Crear `src/main/java/com/example/products/controller/RoleManagementController.java`
  - `@RestController`, endpoint `POST /admin/api/users/{uid}/roles`
  - Recibe `@PathVariable String uid` y `@RequestBody @Valid RoleRequest`
  - Delega a `RoleManagementService.assignRoles(uid, request.getRoles())`
  - Retorna `ResponseEntity<Void>` con HTTP 200
  - _Requirements: 6.1, 6.2, 6.3, 6.5_
  - [x] 9.1 Escribir unit tests para RoleManagementController (MockMvc)
    - Clase `src/test/java/com/example/products/RoleManagementControllerTest.java`
    - Test: sin `ROLE_ADMIN` retorna 403
    - Test: con `ROLE_ADMIN` y body válido retorna 200 y delega al service
    - Test: body inválido (roles vacío) retorna 400
    - _Requirements: 6.1, 6.2, 6.3, 6.5_

- [x] 10. Checkpoint — compilación y tests unitarios
  - Verificar que el proyecto compila sin errores (`./mvnw compile`)
  - Asegurar que todos los tests unitarios pasan, preguntar al usuario si hay dudas

- [x] 11. Property-based tests para JwtAuthenticationConverter y RoleManagementController
  - [x] 11.1 Property 4 — Extracción de roles con prefijo ROLE_
    - Crear o agregar en una clase `FirebaseAuthPropertyTest.java` bajo `src/test/java/com/example/products/`
    - `@Property(tries = 100)`: para cualquier lista de strings no vacíos, el converter retorna autoridades con prefijo `ROLE_` y tamaño igual a la lista
    - Para claim `roles` ausente o nulo, retorna colección vacía sin excepción
    - Comentario: `// Feature: firebase-auth-integration, Property 4: Extracción de roles con prefijo ROLE_`
    - _Requirements: 4.1, 4.2, 4.3_
  - [x] 11.2 Property 5 — Delegación correcta de roles al service
    - `@Property(tries = 100)`: para cualquier uid y lista de roles, el controller invoca `service.assignRoles` con exactamente esos valores
    - Comentario: `// Feature: firebase-auth-integration, Property 5: Delegación correcta de roles al service`
    - _Requirements: 6.3, 6.4_
  - [x] 11.3 Property 6 — UID inexistente resulta en HTTP 404
    - `@Property(tries = 100)`: cuando el service lanza `FirebaseUserNotFoundException` para cualquier uid, el endpoint retorna 404 con `ErrorResponse`
    - Comentario: `// Feature: firebase-auth-integration, Property 6: UID inexistente resulta en HTTP 404`
    - _Requirements: 6.6_
  - [x] 11.4 Property 7 — Error del SDK resulta en HTTP 500
    - `@Property(tries = 100)`: cuando el service lanza `RuntimeException` genérica para cualquier uid, el endpoint retorna 500 con `ErrorResponse`
    - Comentario: `// Feature: firebase-auth-integration, Property 7: Error del SDK resulta en HTTP 500`
    - _Requirements: 6.7_

- [x] 12. Actualizar tests de integración existentes
  - En `AbstractIntegrationTest.java` (o en cada IT que lo necesite), reemplazar la configuración de `JwtDecoder` basada en `public.pem` por `@MockBean JwtDecoder` o por un `@TestConfiguration` que provea un decoder mockeado
  - Revisar `SecurityConfigIT.java`, `ProductsApplicationIT.java` y demás ITs que usen tokens firmados con la llave local y actualizarlos para usar `@WithMockUser` o tokens generados con el decoder mockeado
  - _Requirements: 5.3_

- [x] 13. Checkpoint final — todos los tests pasan
  - Ejecutar `./mvnw test -Dtest='!*IT,!*IntegrationTest'` para confirmar que los tests unitarios y de propiedades pasan
  - Asegurar que no quedan referencias a `public-key-location`, `RSAPublicKey` ni `NimbusJwtDecoder.withPublicKey` en el código fuente
  - Preguntar al usuario si hay dudas antes de cerrar

## Notes

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Las Properties 1, 2 y 3 del design corresponden a validación del `JwtDecoder` contra el JWKS real de Firebase; se cubren mejor con tests de integración end-to-end (fuera del alcance de este plan de tareas de código)
- `FirebaseApp` se inicializa con `ApplicationDefault` credentials: en local se requiere `GOOGLE_APPLICATION_CREDENTIALS` apuntando a un service account JSON; en producción se usa el service account del entorno
