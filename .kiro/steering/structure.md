# Project Structure

```
products/
├── src/
│   ├── main/
│   │   ├── java/com/example/products/
│   │   │   ├── ProductsApplication.java          # Spring Boot entry point
│   │   │   ├── controller/
│   │   │   │   └── ProductController.java        # REST controller — /api/products
│   │   │   ├── service/
│   │   │   │   ├── ProductService.java           # Service interface
│   │   │   │   └── ProductServiceImpl.java       # Service implementation
│   │   │   ├── repository/
│   │   │   │   └── ProductRepository.java        # JpaRepository<Product, Long>
│   │   │   ├── model/
│   │   │   │   ├── Product.java                  # JPA entity
│   │   │   │   ├── ProductRequest.java           # Inbound DTO (validated)
│   │   │   │   ├── ProductResponse.java          # Outbound DTO
│   │   │   │   └── ErrorResponse.java            # Error response body
│   │   │   └── exception/
│   │   │       ├── ProductNotFoundException.java # 404 exception
│   │   │       └── GlobalExceptionHandler.java   # @RestControllerAdvice
│   │   └── resources/
│   │       ├── application.yaml                  # App configuration
│   │       └── db/changelog/
│   │           ├── db.changelog-master.yaml      # Liquibase master changelog
│   │           └── 001-create-products-table.yaml
│   └── test/
│       └── java/com/example/products/
│           ├── AbstractIntegrationTest.java      # Testcontainers base class
│           ├── ProductsApplicationTests.java     # Context load test
│           └── service/
│               └── ProductServiceImplTest.java   # Unit tests (Mockito)
├── pom.xml
└── mvnw / mvnw.cmd
```

## Conventions
- Base package: `com.example.products`
- Layered architecture: `controller` → `service` → `repository` → JPA entity
- Controllers delegate all logic to services — no direct repository access in controllers
- DTOs: `ProductRequest` (inbound, validated) and `ProductResponse` (outbound) — never expose the entity directly
- Exceptions are handled centrally in `GlobalExceptionHandler`
- Test classes mirror the main source tree under `src/test/java`
- Integration tests extend `AbstractIntegrationTest` to get a Testcontainers-managed PostgreSQL instance
- Configuration goes in `src/main/resources/application.yaml`
