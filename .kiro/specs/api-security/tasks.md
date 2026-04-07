# Implementation Plan: API Security (JWT / RSA Resource Server)

## Overview

Agregar seguridad JWT al servicio `products` usando Spring Security como OAuth2 Resource Server. Los endpoints GET son públicos; POST, PUT y DELETE requieren un JWT válido firmado con RSA. CORS se configura de forma externalizada y CSRF se desactiva.

## Tasks

- [x] 1. Agregar dependencia Maven y generar par de llaves RSA
  - Agregar `spring-boot-starter-oauth2-resource-server` en `pom.xml` (scope compile)
  - Agregar dependencia de test `net.jqwik:jqwik:1.9.3` en `pom.xml`
  - Crear el directorio `products/src/main/resources/certs/`
  - Generar `private.pem` (PKCS#8, 2048 bits) y `public.pem` (X.509) con `openssl`
  - Agregar `**/certs/private.pem` al `.gitignore`
  - _Requirements: 4.1, 4.2, 4.4, 7.1_

- [x] 2. Implementar configuración de propiedades CORS y seguridad
  - [x] 2.1 Crear `CorsProperties.java` en `com.example.products.config`
    - Anotar con `@ConfigurationProperties(prefix = "security.cors")`
    - Campo `List<String> allowedOrigins` con getter/setter
    - Habilitar con `@EnableConfigurationProperties` o `@ConfigurationPropertiesScan`
    - _Requirements: 5.5_
  - [x] 2.2 Actualizar `application.yaml` con las secciones de seguridad
    - Agregar `spring.security.oauth2.resourceserver.jwt.public-key-location: classpath:certs/public.pem`
    - Agregar `security.cors.allowed-origins` con orígenes de ejemplo
    - _Requirements: 3.5, 5.5_

- [x] 3. Implementar `SecurityConfig`
  - [x] 3.1 Crear `SecurityConfig.java` en `com.example.products.config`
    - Anotar con `@Configuration` y `@EnableWebSecurity`
    - Bean `SecurityFilterChain`: CORS, CSRF desactivado, sesión `STATELESS`, reglas `permitAll()` para GET y `authenticated()` para el resto, `oauth2ResourceServer` con JWT
    - Bean `JwtDecoder`: `NimbusJwtDecoder.withPublicKey(RSAPublicKey)` leyendo de `${spring.security.oauth2.resourceserver.jwt.public-key-location}`
    - Bean `CorsConfigurationSource`: métodos `GET,POST,PUT,DELETE,OPTIONS`, headers `Authorization,Content-Type`, exponer `Authorization`, registrar en `/api/**`
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.5, 5.1, 5.2, 5.3, 5.4, 6.1, 6.2, 7.2_

- [x] 4. Checkpoint — verificar arranque de la aplicación
  - Compilar con `./mvnw compile` y confirmar que no hay errores de contexto Spring.
  - Asegurarse de que todos los tests existentes siguen pasando, preguntar al usuario si surgen dudas.

- [x] 5. Escribir tests de integración de seguridad (MockMvc)
  - [x] 5.1 Crear `SecurityConfigIT.java` en `src/test/java/com/example/products`
    - Usar `@SpringBootTest` + `MockMvc` (o `@WebMvcTest` con `JwtDecoder` mockeado)
    - Test: contexto arranca correctamente con `public.pem` presente
    - Test: GET `/api/products` sin token → 200
    - Test: POST `/api/products` sin token → 401 con header `WWW-Authenticate: Bearer`
    - Test: POST `/api/products` con JWT válido → no 401/403
    - Test: POST `/api/products` con JWT expirado → 401
    - Test: CORS preflight OPTIONS `/api/products` → 200 sin token
    - Test: CORS headers presentes en respuesta (`Access-Control-Allow-Methods`, etc.)
    - Test: no se crea sesión HTTP (`Set-Cookie` ausente)
    - Test: CSRF desactivado (POST con JWT válido sin CSRF token → no 403)
    - _Requirements: 1.1, 1.2, 2.4, 3.2, 3.3, 3.4, 5.1, 5.2, 5.3, 5.4, 6.1, 6.2, 7.2, 8.1, 8.2_
  - [ ]* 5.2 Escribir property test — Property 1: GET endpoints son siempre públicos
    - Clase `ApiSecurityPropertyTest` con `@ExtendWith(JqwikExtension.class)` + MockMvc
    - Generar tokens aleatorios (válidos, inválidos, ausentes) con `@ForAll`
    - Verificar que GET `/api/products` retorna 200/404, nunca 401/403
    - Mínimo 100 iteraciones (`@Property(tries = 100)`)
    - Comentario: `// Feature: api-security, Property 1: GET endpoints son siempre públicos`
    - _Requirements: 1.1, 1.2, 1.3_
  - [ ]* 5.3 Escribir property test — Property 2: JWT válido permite operaciones de escritura
    - Generar JWTs válidos con claims aleatorios firmados con la clave privada RSA de test
    - Verificar que POST/PUT/DELETE retornan distinto de 401/403
    - Comentario: `// Feature: api-security, Property 2: JWT válido permite operaciones de escritura`
    - _Requirements: 2.1, 2.2, 2.3_
  - [ ]* 5.4 Escribir property test — Property 3: Ausencia de token en escritura retorna 401 con WWW-Authenticate
    - Generar peticiones POST/PUT/DELETE sin header `Authorization`
    - Verificar 401 y presencia de `WWW-Authenticate: Bearer`
    - Comentario: `// Feature: api-security, Property 3: Ausencia de token en escritura retorna 401 con WWW-Authenticate`
    - _Requirements: 2.4, 2.5, 2.6, 8.1_
  - [ ]* 5.5 Escribir property test — Property 4: JWT inválido en escritura retorna 401
    - Generar JWTs con firma incorrecta, estructura malformada o `exp` expirado
    - Verificar 401 con `WWW-Authenticate: Bearer` que contenga descripción del error
    - Comentario: `// Feature: api-security, Property 4: JWT inválido en escritura retorna 401`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 8.2_
  - [ ]* 5.6 Escribir property test — Property 5: Preflight OPTIONS retorna 200 sin autenticación
    - Generar paths aleatorios bajo `/api/**`
    - Verificar que OPTIONS retorna 200 sin token
    - Comentario: `// Feature: api-security, Property 5: Preflight OPTIONS retorna 200 sin autenticación`
    - _Requirements: 5.4_

- [x] 6. Checkpoint final — Ensure all tests pass
  - Ejecutar `./mvnw test` (requiere Docker para Testcontainers).
  - Verificar que todos los tests pasan, preguntar al usuario si surgen dudas.

## Notes

- Las sub-tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido.
- Cada tarea referencia requisitos específicos para trazabilidad.
- Los property tests usan jqwik (JUnit 5) con mínimo 100 iteraciones.
- `private.pem` nunca debe commitearse; solo `public.pem` se versiona.
- Los tests de integración pueden usar `@WebMvcTest` con `JwtDecoder` mockeado para evitar dependencia de `public.pem` real en CI.
