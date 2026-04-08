# Requirements Document

## Introduction

Esta feature agrega un módulo de gestión de usuarios que permite crear usuarios directamente en Firebase Authentication desde la API del backend. El módulo expone un endpoint protegido bajo `/admin/api/users` que acepta los datos del nuevo usuario, delega la creación al Firebase Admin SDK, y retorna la información del usuario creado. Se integra con la arquitectura en capas existente (controller → service) y con el mecanismo de autenticación Firebase JWT ya configurado.

## Glossary

- **UserManagementController**: Controlador REST en `com.example.products.controller` que expone los endpoints de gestión de usuarios.
- **UserManagementService**: Interfaz de servicio en `com.example.products.service` que define las operaciones de gestión de usuarios.
- **UserManagementServiceImpl**: Implementación de `UserManagementService` que usa el Firebase Admin SDK para operar sobre Firebase Authentication.
- **FirebaseAuth**: Bean de Spring que provee acceso al Firebase Admin SDK para operaciones de autenticación.
- **CreateUserRequest**: DTO de entrada con los datos necesarios para crear un usuario en Firebase, incluyendo un campo opcional de roles.
- **UserResponse**: DTO de salida con la información del usuario creado retornada al cliente, incluyendo los roles asignados si los hay.
- **Firebase_UID**: Identificador único generado por Firebase Authentication para cada usuario creado.
- **FirebaseUserAlreadyExistsException**: Excepción lanzada cuando se intenta crear un usuario con un email que ya existe en Firebase Authentication.
- **RoleManagementService**: Servicio existente que expone el método `assignRoles(uid, roles)` para asignar custom claims en Firebase Authentication.
- **Custom_Claims**: Atributos adicionales asignados a un usuario en Firebase Authentication mediante el Firebase Admin SDK, usados para representar roles de autorización.

## Requirements

### Requirement 1: Crear usuario en Firebase Authentication

**User Story:** Como administrador del sistema, quiero crear usuarios directamente en Firebase Authentication desde la API, para que pueda gestionar el acceso de nuevos usuarios sin necesidad de acceder a Firebase Console.

#### Acceptance Criteria

1. THE UserManagementController SHALL exponer el endpoint `POST /admin/api/users`.
2. WHEN el endpoint recibe una solicitud con datos válidos de usuario, THE UserManagementController SHALL delegar la creación al UserManagementService y retornar HTTP 201 con el `UserResponse` del usuario creado.
3. WHEN el UserManagementService procesa la solicitud, THE UserManagementServiceImpl SHALL invocar el Firebase Admin SDK para crear el usuario con los datos proporcionados.
4. WHEN Firebase Authentication crea el usuario exitosamente, THE UserManagementServiceImpl SHALL retornar un `UserResponse` que contenga el `Firebase_UID`, el email y el displayName del usuario creado.
5. THE SecurityConfig SHALL requerir la autoridad `ROLE_ADMIN` para acceder a `POST /admin/api/users`.

### Requirement 6: Asignación de roles en la creación del usuario

**User Story:** Como administrador del sistema, quiero poder asignar roles al usuario en el mismo request de creación, para que no tenga que hacer una llamada adicional a `POST /admin/api/users/{uid}/roles` cuando los roles ya son conocidos de antemano.

#### Acceptance Criteria

1. THE CreateUserRequest SHALL aceptar un campo opcional `roles` de tipo `List<String>`, donde cada elemento representa un nombre de rol (ej: `"ROLE_ADMIN"`, `"ROLE_USER"`).
2. WHEN el `CreateUserRequest` incluye un campo `roles` no vacío, THE UserManagementServiceImpl SHALL invocar `RoleManagementService.assignRoles(uid, roles)` inmediatamente después de que Firebase Authentication cree el usuario exitosamente.
3. WHEN el `CreateUserRequest` no incluye el campo `roles` o el campo es `null`, THE UserManagementServiceImpl SHALL crear el usuario en Firebase Authentication sin asignar Custom_Claims.
4. WHEN el `CreateUserRequest` incluye un campo `roles` vacío (`[]`), THE UserManagementServiceImpl SHALL crear el usuario en Firebase Authentication sin asignar Custom_Claims.
5. WHEN los roles son asignados exitosamente durante la creación, THE UserManagementServiceImpl SHALL retornar un `UserResponse` que incluya la lista de roles asignados.
6. WHEN el `CreateUserRequest` no incluye roles, THE UserManagementServiceImpl SHALL retornar un `UserResponse` con el campo `roles` como lista vacía.
7. IF la asignación de Custom_Claims falla después de que el usuario fue creado en Firebase Authentication, THEN THE UserManagementServiceImpl SHALL propagar la excepción resultando en HTTP 500, y el usuario creado permanecerá en Firebase Authentication sin roles asignados.

### Requirement 2: Validación de datos de entrada para creación de usuario

**User Story:** Como desarrollador, quiero que los datos de entrada para crear un usuario sean validados antes de invocar Firebase, para que se retornen errores descriptivos ante datos inválidos sin consumir llamadas innecesarias al SDK.

#### Acceptance Criteria

1. THE CreateUserRequest SHALL requerir un campo `email` con formato de email válido y longitud máxima de 254 caracteres.
2. THE CreateUserRequest SHALL aceptar un campo opcional `displayName` con longitud máxima de 256 caracteres.
3. IF el `CreateUserRequest` contiene datos inválidos, THEN THE UserManagementController SHALL retornar HTTP 400 con un `ErrorResponse` que describa los campos inválidos.
4. THE UserManagementController SHALL aplicar `@Valid` sobre el `CreateUserRequest` para activar la validación de Bean Validation antes de invocar el servicio.

### Requirement 3: Manejo de conflicto por email duplicado

**User Story:** Como administrador del sistema, quiero recibir un error claro cuando intento crear un usuario con un email que ya existe en Firebase, para que pueda identificar y resolver el conflicto sin ambigüedad.

#### Acceptance Criteria

1. IF el email del `CreateUserRequest` ya está registrado en Firebase Authentication, THEN THE UserManagementServiceImpl SHALL lanzar una `FirebaseUserAlreadyExistsException`.
2. WHEN el `GlobalExceptionHandler` recibe una `FirebaseUserAlreadyExistsException`, THE GlobalExceptionHandler SHALL retornar HTTP 409 con un `ErrorResponse` que contenga un mensaje descriptivo indicando que el email ya está en uso.
3. THE FirebaseUserAlreadyExistsException SHALL ser registrada en el `GlobalExceptionHandler` con el mismo patrón que las excepciones existentes (`ProductNotFoundException`, `FirebaseUserNotFoundException`).

### Requirement 4: Manejo de errores del Firebase Admin SDK

**User Story:** Como operador del sistema, quiero que los errores inesperados del Firebase Admin SDK sean manejados de forma consistente, para que el cliente reciba siempre una respuesta estructurada en lugar de un error genérico.

#### Acceptance Criteria

1. IF el Firebase Admin SDK retorna un error al crear el usuario distinto de email duplicado, THEN THE UserManagementServiceImpl SHALL propagar la excepción de forma que resulte en HTTP 500.
2. WHEN ocurre un error inesperado durante la creación del usuario, THE GlobalExceptionHandler SHALL retornar HTTP 500 con un `ErrorResponse` con mensaje `"An unexpected error occurred"`.
3. THE UserManagementServiceImpl SHALL registrar en el log el error recibido del Firebase Admin SDK antes de propagar la excepción.

### Requirement 5: Estructura de respuesta del usuario creado

**User Story:** Como desarrollador cliente de la API, quiero que la respuesta de creación de usuario contenga los datos esenciales del usuario creado, para que pueda usar el `Firebase_UID` en operaciones posteriores como la asignación de roles.

#### Acceptance Criteria

1. THE UserResponse SHALL contener los campos `uid` (String), `email` (String), `displayName` (String, nullable) y `roles` (List<String>, nunca null, vacía si no se asignaron roles).
2. WHEN el usuario es creado sin `displayName`, THE UserManagementServiceImpl SHALL retornar un `UserResponse` con `displayName` igual a `null`.
3. THE UserManagementController SHALL retornar el `UserResponse` en el body de la respuesta HTTP 201 con `Content-Type: application/json`.
4. THE UserResponse SHALL ser un DTO de solo lectura que no exponga ningún dato sensible del usuario.
