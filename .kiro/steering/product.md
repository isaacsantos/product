# Product

`products` is a Spring Boot application serving as a product management service. It exposes a RESTful API under `/api/products` for full CRUD operations on `Product` resources, backed by a PostgreSQL database.

- Group: `com.example`
- Artifact: `products`
- Version: `0.0.1-SNAPSHOT`
- Spring app name: `products`

## API Endpoints

| Method | Path                  | Description              | Status |
|--------|-----------------------|--------------------------|--------|
| POST   | `/api/products`       | Create a product         | 201    |
| GET    | `/api/products`       | List all products        | 200    |
| GET    | `/api/products/{id}`  | Get product by id        | 200    |
| PUT    | `/api/products/{id}`  | Update a product         | 200    |
| DELETE | `/api/products/{id}`  | Delete a product         | 204    |

## Product Fields

| Field         | Type       | Constraints                  |
|---------------|------------|------------------------------|
| `id`          | Long       | Auto-generated, primary key  |
| `name`        | String     | Non-null, non-blank          |
| `description` | String     | Nullable                     |
| `price`       | BigDecimal | Non-null, minimum `0.01`     |

## Error Handling

All errors return a consistent `ErrorResponse` body with `status`, `message`, and `timestamp`:
- `404 Not Found` — product id does not exist
- `400 Bad Request` — validation failure (blank name, null/non-positive price)
- `500 Internal Server Error` — unexpected runtime exception
