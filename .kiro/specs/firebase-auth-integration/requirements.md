# Requirements Document

## Introduction

Esta feature reemplaza el mecanismo de validación JWT actual (llave pública RSA local `public.pem`) por validación de tokens JWT emitidos por Firebase Authentication. El backend debe verificar los tokens contra el JWKS público de Firebase, validar el issuer y audience correctos, y mantener la extracción de roles para el control de acceso existente.

## Glossary

- **SecurityConfig**: Clase de configuración Spring Security que define el `SecurityFilterChain`, `JwtDecoder` y `JwtAuthenticationConverter`.
- **JwtDecoder**: Componente Spring Security responsable de decodificar y validar tokens JWT.
- **JWKS_Endpoint**: URL pública de Firebase que expone las claves públicas para verificar firmas JWT: `https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`.
- **Firebase_Issuer**: Valor del claim `iss` en tokens Firebase: `https://securetoken.google.com/{project-id}`.
- **Firebase_Audience**: Valor del claim `aud` en tokens Firebase, igual al Firebase project-id.
- **Project_ID**: Identificador del proyecto Firebase, configurable vía propiedades de la aplicación.
- **JwtAuthenticationConverter**: Componente que extrae las autoridades (roles) del JWT validado.
- **Token_Firebase**: JWT emitido por Firebase Authentication, firmado con claves RSA rotadas periódicamente.

## Requirements

### Requirement 1: Reemplazar validación JWT local por JWKS de Firebase

**User Story:** Como operador del sistema, quiero que el backend valide tokens JWT usando el JWKS público de Firebase, para que los tokens emitidos por Firebase Authentication sean aceptados sin necesidad de gestionar llaves locales.

#### Acceptance Criteria

1. WHEN el backend recibe una solicitud con un token JWT en el header `Authorization: Bearer`, THE SecurityConfig SHALL validar la firma del token usando las claves públicas obtenidas del JWKS_Endpoint de Firebase.
2. THE JwtDecoder SHALL obtener las claves públicas desde `https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`.
3. WHEN las claves del JWKS_Endpoint son rotadas por Firebase, THE JwtDecoder SHALL refrescar las claves automáticamente sin requerir reinicio de la aplicación.
4. THE SecurityConfig SHALL eliminar toda referencia a `public.pem` y a `NimbusJwtDecoder.withPublicKey()`.

### Requirement 2: Validar issuer y audience del token Firebase

**User Story:** Como operador del sistema, quiero que el backend valide el issuer y audience del token, para que solo tokens emitidos para el proyecto Firebase correcto sean aceptados.

#### Acceptance Criteria

1. THE JwtDecoder SHALL validar que el claim `iss` del token sea igual a `https://securetoken.google.com/{project-id}`.
2. THE JwtDecoder SHALL validar que el claim `aud` del token contenga el valor del Firebase Project_ID configurado.
3. IF el claim `iss` del token no coincide con el Firebase_Issuer esperado, THEN THE JwtDecoder SHALL rechazar el token con un error de autenticación.
4. IF el claim `aud` del token no contiene el Firebase_Audience esperado, THEN THE JwtDecoder SHALL rechazar el token con un error de autenticación.
5. THE SecurityConfig SHALL leer el Project_ID desde la propiedad `firebase.project-id` en `application.yaml`.

### Requirement 3: Configuración externalizada del Project ID de Firebase

**User Story:** Como desarrollador, quiero que el Firebase project-id sea configurable vía propiedades de la aplicación, para que el mismo artefacto pueda desplegarse en distintos entornos sin recompilar.

#### Acceptance Criteria

1. THE SecurityConfig SHALL leer el valor de `firebase.project-id` usando `@Value` o `@ConfigurationProperties`.
2. IF la propiedad `firebase.project-id` no está definida, THEN THE SecurityConfig SHALL fallar en el arranque de la aplicación con un mensaje de error descriptivo.
3. THE SecurityConfig SHALL construir el Firebase_Issuer como `https://securetoken.google.com/` concatenado con el valor de `firebase.project-id`.

### Requirement 4: Mantener extracción de roles desde el claim del JWT

**User Story:** Como desarrollador, quiero que el sistema siga extrayendo roles desde el claim `roles` del JWT de Firebase, para que el control de acceso basado en roles existente continúe funcionando sin cambios.

#### Acceptance Criteria

1. THE JwtAuthenticationConverter SHALL extraer los roles desde el claim `roles` del token JWT validado.
2. WHEN el claim `roles` contiene el valor `ADMIN`, THE JwtAuthenticationConverter SHALL asignar la autoridad `ROLE_ADMIN` al contexto de seguridad.
3. IF el claim `roles` está ausente o es nulo en el token, THEN THE JwtAuthenticationConverter SHALL retornar una colección vacía de autoridades sin lanzar excepción.
4. THE SecurityConfig SHALL mantener la regla de acceso que requiere `ROLE_ADMIN` para rutas bajo `/admin/api/**`.

### Requirement 5: Eliminar artefactos de la configuración JWT local

**User Story:** Como desarrollador, quiero eliminar los archivos y configuraciones relacionados con la llave RSA local, para que el proyecto no mantenga artefactos obsoletos que puedan causar confusión.

#### Acceptance Criteria

1. THE SecurityConfig SHALL eliminar la inyección de `RSAPublicKey` vía `@Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}")`.
2. THE application.yaml SHALL eliminar la propiedad `spring.security.oauth2.resourceserver.jwt.public-key-location`.
3. WHERE los archivos `public.pem` y `private.pem` ya no sean referenciados por ningún componente de la aplicación, THE proyecto SHALL poder compilar y arrancar sin dichos archivos presentes.

### Requirement 6: Endpoint de gestión de roles de usuario vía Firebase Admin SDK

**User Story:** Como administrador del sistema, quiero asignar roles a usuarios de Firebase mediante un endpoint protegido, para que pueda gestionar el control de acceso sin modificar directamente Firebase Console.

#### Acceptance Criteria

1. THE RoleManagementController SHALL exponer el endpoint `POST /admin/api/users/{uid}/roles`.
2. THE SecurityConfig SHALL requerir la autoridad `ROLE_ADMIN` para acceder a `POST /admin/api/users/{uid}/roles`.
3. WHEN el endpoint recibe una solicitud válida, THE RoleManagementController SHALL leer la lista de roles del body del request y delegarla al RoleManagementService.
4. WHEN el RoleManagementService procesa la solicitud, THE RoleManagementService SHALL invocar Firebase Admin SDK para establecer los custom claims `{ "roles": [...] }` en el usuario identificado por el `uid` proporcionado.
5. WHEN Firebase Admin SDK establece los custom claims exitosamente, THE RoleManagementController SHALL retornar HTTP 200.
6. IF el `uid` proporcionado no corresponde a ningún usuario en Firebase, THEN THE RoleManagementService SHALL lanzar una excepción que resulte en HTTP 404 con un mensaje de error descriptivo.
7. IF Firebase Admin SDK retorna un error al intentar establecer los custom claims, THEN THE RoleManagementService SHALL lanzar una excepción que resulte en HTTP 500 con un mensaje de error descriptivo.
8. THE pom.xml SHALL incluir la dependencia `com.google.firebase:firebase-admin` para proveer acceso al Firebase Admin SDK.
9. THE FirebaseApp SHALL ser inicializado como un bean de Spring usando las credenciales de la cuenta de servicio configuradas en la aplicación.
