# Implementation Plan: Tags

## Overview

Implement the Tags feature: CRUD for `Tag` entities and a many-to-many association with `Product`. Tasks follow the same layered order used across the project: schema → model → repository → service → controller → error handling.

## Tasks

- [x] 1. Create Liquibase changesets for tags schema
  - Create `004-create-tags-table.yaml` defining the `tags` table: `id` (BIGSERIAL PK), `name` (VARCHAR(100) NOT NULL UNIQUE)
  - Create `005-create-product-tag-table.yaml` defining the `product_tag` join table: `product_id` (BIGINT FK → products), `tag_id` (BIGINT FK → tags), composite PK on both columns
  - Include both files in `db.changelog-master.yaml`
  - _Requirements: 9.1, 9.2, 9.3_

- [x] 2. Implement Tag entity and DTOs
  - [x] 2.1 Create `model/Tag.java` — `@Entity @Table(name="tags")` with `@Id @GeneratedValue(IDENTITY)`, `@Column(nullable=false, unique=true, length=100)` on `name`, `@ManyToMany(mappedBy="tags")` inverse side with `@ToString.Exclude @EqualsAndHashCode.Exclude`
    - _Requirements: 8.1_
  - [x] 2.2 Create `model/TagRequest.java` — inbound DTO with `@NotBlank @Size(max=100)` on `name`
    - _Requirements: 8.2_
  - [x] 2.3 Create `model/TagResponse.java` — outbound DTO with `id` and `name`
    - _Requirements: 8.3_

- [x] 3. Extend Product entity and ProductResponse
  - Add `@ManyToMany @JoinTable(name="product_tag", ...)` relationship to `Product.java` owning the join table
  - Add `List<TagResponse> tags` field (default empty list) to `ProductResponse.java`
  - _Requirements: 7.1, 7.2_

- [x] 4. Implement TagRepository
  - Create `repository/TagRepository.java` extending `JpaRepository<Tag, Long>`
  - Add `findByNameIgnoreCase(String name)` and `existsByNameIgnoreCase(String name)` query methods
  - _Requirements: 8.1_

- [x] 5. Implement TagNotFoundException
  - Create `exception/TagNotFoundException.java` extending `RuntimeException`, message: `"Tag not found with id: " + id`
  - Register handler in `GlobalExceptionHandler` → 404 with `ErrorResponse`
  - _Requirements: 3.2, 4.2, 5.2, 6.4_

- [x] 6. Implement TagService and TagServiceImpl
  - [x] 6.1 Create `service/TagService.java` interface with `create`, `findAll`, `findById`, `update`, `delete`
  - [x] 6.2 Create `service/TagServiceImpl.java` implementing `TagService`, annotated `@Service`
    - `create`: map `TagRequest` → `Tag`, save, return `TagResponse`
    - `findAll`: return all tags as `TagResponse` list
    - `findById`: find or throw `TagNotFoundException`, return `TagResponse`
    - `update`: find or throw, set name, save, return `TagResponse`
    - `delete`: check exists or throw, delete by id
  - _Requirements: 1.1, 2.1, 3.1, 4.1, 5.1_

- [x] 7. Extend ProductService with setTags
  - Add `setTags(Long productId, Set<Long> tagIds): ProductResponse` to `ProductService` interface
  - Implement in `ProductServiceImpl`: inject `TagRepository`, annotate method `@Transactional`, resolve each tag id (throw `TagNotFoundException` on miss), replace product tag set, save and return `ProductResponse`
  - Update `toResponse(Product)` to map `product.getTags()` into `List<TagResponse>`
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 7.1, 7.2_

- [x] 8. Implement TagController
  - Create `controller/TagController.java` annotated `@RestController @RequestMapping("/api/tags")`
  - `POST /` → `create(@Valid @RequestBody TagRequest)` → 201 Created — `@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")`
  - `GET /` → `findAll()` → 200 OK
  - `GET /{id}` → `findById(@PathVariable Long id)` → 200 OK
  - `PUT /{id}` → `update(...)` → 200 OK — `@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")`
  - `DELETE /{id}` → `delete(...)` → 204 No Content — `@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")`
  - _Requirements: 1.1, 1.2, 2.1, 3.1, 3.2, 4.1, 4.2, 5.1, 5.2_

- [x] 9. Add setTags endpoint to ProductController
  - Add `PUT /{id}/tags` → `setTags(@PathVariable Long id, @RequestBody Set<Long> tagIds)` → 200 OK
  - Protect with `@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")`
  - _Requirements: 6.1, 6.2, 6.3, 6.4_

## Notes

- `setTags` is a full replacement operation — passing an empty array removes all tags from the product
- `Tag.products` uses `@ToString.Exclude` and `@EqualsAndHashCode.Exclude` to avoid circular references with Lombok
- `Product` owns the `@ManyToMany` relationship via the `product_tag` join table
- All write endpoints follow the same `ADMIN`/`MANAGER` role guard used across the application
