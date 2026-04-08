# Implementation Plan: user-management

## Overview

Implementación del endpoint `POST /admin/api/users` para crear usuarios en Firebase Authentication desde el backend, con soporte de asignación de roles en el mismo request. Sigue la arquitectura en capas existente (`controller → service`) y reutiliza `RoleManagementService` y `FirebaseAuth` ya configurados.

## Tasks

- [x] 1. Crear excepción FirebaseUserAlreadyExistsException
  - Crear `src/main/java/com/example/products/exception/FirebaseUserAlreadyExistsException.java`
  - Extiende `RuntimeException`, constructor recibe `String email` y pasa `"Email already in use: " + email` al super
  - _Requirements: 3.1_

- [x] 2. Registrar handler en GlobalExceptionHandler
  - Agregar método `handleFirebaseUserAlreadyExists(FirebaseUserAlreadyExistsException ex)` anotado con `@ExceptionHandler`
  - Retorna `ResponseEntity<ErrorResponse>` con HTTP 409, siguiendo el mismo patrón que los handlers existentes
  - _Requirements: 3.2, 3.3_

- [x] 3. Crear DTOs CreateUserRequest y UserResponse
  - [x] 3.1 Crear `src/main/java/com/example/products/model/CreateUserRequest.java`
    - Campo `email`: `@NotBlank @Email @Size(max = 254)`
    - Campo `displayName`: `@Size(max = 256)`, nullable, sin `@NotBlank`
    - Campo `roles`: `List<String>`, nullable, sin validación de contenido
    - Usar Lombok `@Data`
    - _Requirements: 2.1, 2.2, 6.1_
  - [x] 3.2 Crear `src/main/java/com/example/products/model/UserResponse.java`
    - Campos: `String uid`, `String email`, `String displayName` (nullable), `List<String> roles` (nunca null)
    - Usar Lombok `@Data @Builder`
    - _Requirements: 5.1, 5.4_

- [x] 4. Crear UserManagementService e implementación
  - [x] 4.1 Crear interfaz `src/main/java/com/example/products/service/UserManagementService.java`
    - Método `UserResponse createUser(CreateUserRequest request)`
    - _Requirements: 1.2_
  - [x] 4.2 Crear `src/main/java/com/example/products/service/UserManagementServiceImpl.java`
    - Anotada con `@Service`, inyecta `FirebaseAuth` y `RoleManagementService` por constructor
    - Construye `UserRecord.CreateRequest` con `email` y `displayName` (si no es null)
    - Llama a `firebaseAuth.createUser(createRequest)`
    - Captura `FirebaseAuthException`: si `AuthErrorCode.EMAIL_ALREADY_EXISTS` lanza `FirebaseUserAlreadyExistsException(email)`, de lo contrario loguea y relanza como `RuntimeException`
    - Si `roles` es no-nulo y no-vacío, invoca `roleManagementService.assignRoles(uid, roles)`
    - Construye y retorna `UserResponse` con `roles` como `List.of()` si no se asignaron
    - _Requirements: 1.3, 1.4, 3.1, 4.1, 4.3, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_
  - [x] 4.3 Escribir unit tests para UserManagementServiceImpl
    - Clase `src/test/java/com/example/products/UserManagementServiceImplTest.java` con `@ExtendWith(MockitoExtension.class)`
    - Test: SDK crea usuario → `assignRoles` es invocado cuando `roles` no es vacío
    - Test: SDK crea usuario → `assignRoles` NO es invocado cuando `roles` es null
    - Test: SDK crea usuario → `assignRoles` NO es invocado cuando `roles` es `[]`
    - Test: SDK lanza `EMAIL_ALREADY_EXISTS` → se lanza `FirebaseUserAlreadyExistsException`
    - Test: SDK lanza otro error → se lanza `RuntimeException`
    - Test: `displayName` null → `UserResponse.displayName` es null
    - _Requirements: 1.3, 1.4, 3.1, 4.1, 4.3, 6.2, 6.3, 6.4, 6.5, 6.6_

- [x] 5. Crear UserManagementController
  - Crear `src/main/java/com/example/products/controller/UserManagementController.java`
  - `@RestController @RequestMapping("/admin/api/users")`
  - Endpoint `POST /` con `@PreAuthorize("hasRole('ADMIN')")` y `@Valid @RequestBody CreateUserRequest`
  - Delega a `UserManagementService.createUser(request)`
  - Retorna `ResponseEntity.status(HttpStatus.CREATED).body(userResponse)`
  - _Requirements: 1.1, 1.2, 1.5, 2.3, 2.4, 5.3_
  - [x] 5.1 Escribir unit tests para UserManagementController (MockMvc)
    - Clase `src/test/java/com/example/products/UserManagementControllerTest.java` con `@WebMvcTest(UserManagementController.class)`
    - Test: creación exitosa sin roles → HTTP 201, `UserResponse` con `roles = []`
    - Test: creación exitosa con roles → HTTP 201, `UserResponse` con roles asignados
    - Test: email inválido → HTTP 400
    - Test: email duplicado (service lanza `FirebaseUserAlreadyExistsException`) → HTTP 409
    - Test: error inesperado del SDK → HTTP 500
    - Test: sin token → HTTP 401
    - Test: token sin `ROLE_ADMIN` → HTTP 403
    - Test: `displayName` null → `UserResponse.displayName` es null
    - _Requirements: 1.1, 1.2, 1.5, 2.3, 3.2, 4.2, 5.2, 5.3_

- [x] 6. Checkpoint — compilación y tests unitarios
  - Verificar que el proyecto compila sin errores (`./mvnw compile`)
  - Asegurar que todos los tests unitarios pasan, preguntar al usuario si hay dudas

- [x] 7. Property-based tests para UserManagementController
  - [x] 7.1 Property 1 — Creación válida retorna respuesta completa
    - Crear `src/test/java/com/example/products/UserManagementPropertyTest.java`
    - Para cualquier `CreateUserRequest` con email válido, cuando el service retorna un `UserResponse`, el endpoint retorna HTTP 201 con `uid` no-nulo, mismo `email`, y `roles` no-nulo
    - Comentario: `// Feature: user-management, Property 1: Creación válida retorna respuesta completa`
    - _Requirements: 1.2, 1.4, 5.1, 5.3_
  - [x] 7.2 Property 2 — Email inválido es rechazado con 400
    - Para cualquier string que no sea email válido o supere 254 caracteres, el endpoint retorna HTTP 400 con `ErrorResponse` que identifica el campo `email`
    - Comentario: `// Feature: user-management, Property 2: Email inválido es rechazado con 400`
    - _Requirements: 2.1, 2.3_
  - [x] 7.3 Property 3 — displayName excesivo es rechazado con 400
    - Para cualquier `displayName` con más de 256 caracteres, el endpoint retorna HTTP 400
    - Comentario: `// Feature: user-management, Property 3: displayName excesivo es rechazado con 400`
    - _Requirements: 2.2_
  - [x] 7.4 Property 4 — Roles del request se reflejan en la respuesta
    - Para cualquier lista `roles` no-nula y no-vacía, cuando el service retorna exitosamente, `UserResponse.roles` es igual a la lista enviada
    - Comentario: `// Feature: user-management, Property 4: Roles del request se reflejan en la respuesta`
    - _Requirements: 6.2, 6.5_
  - [x] 7.5 Property 5 — Ausencia de roles produce lista vacía en respuesta
    - Para `roles` null o `[]`, `UserResponse.roles` es lista vacía (no null) y `assignRoles` no es invocado
    - Comentario: `// Feature: user-management, Property 5: Ausencia de roles produce lista vacía en respuesta`
    - _Requirements: 6.3, 6.4, 6.6_
  - [x] 7.6 Property 6 — Email duplicado produce HTTP 409
    - Para cualquier email que ya exista (service lanza `FirebaseUserAlreadyExistsException`), el endpoint retorna HTTP 409 con `ErrorResponse` con mensaje descriptivo
    - Comentario: `// Feature: user-management, Property 6: Email duplicado produce HTTP 409`
    - _Requirements: 3.1, 3.2_

- [x] 8. Checkpoint final — todos los tests pasan
  - Ejecutar `./mvnw test -Dtest='!*IT,!*IntegrationTest'` para confirmar que los tests unitarios y de propiedades pasan
  - Preguntar al usuario si hay dudas antes de cerrar

## Notes

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia los requisitos específicos para trazabilidad
- No se requieren cambios en `SecurityConfig` ni en `FirebaseConfig` — el endpoint `/admin/api/users` ya está cubierto por la regla `hasAnyRole("ADMIN", "MANAGER")` existente; `@PreAuthorize("hasRole('ADMIN')")` en el controller restringe adicionalmente a solo `ADMIN`
- `FirebaseAuth` y `RoleManagementService` ya existen como beans — solo se inyectan en `UserManagementServiceImpl`
