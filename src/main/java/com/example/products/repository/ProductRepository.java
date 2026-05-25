package com.example.products.repository;

import com.example.products.model.ConditionType;
import com.example.products.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT DISTINCT p FROM Product p JOIN p.tags t WHERE t.id IN :tagIds")
    Page<Product> findByTagIds(@Param("tagIds") Collection<Long> tagIds, Pageable pageable);

    Page<Product> findByActiveTrue(Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p JOIN p.tags t WHERE t.id IN :tagIds AND p.active = true")
    Page<Product> findActiveByTagIds(@Param("tagIds") Collection<Long> tagIds, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Product> findByNameContainingIgnoreCase(@Param("search") String search, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Product> findByActiveTrueAndNameContainingIgnoreCase(@Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p JOIN p.tags t WHERE t.id IN :tagIds AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Product> findByTagIdsAndNameContainingIgnoreCase(@Param("tagIds") Collection<Long> tagIds, @Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p JOIN p.tags t WHERE t.id IN :tagIds AND p.active = true AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Product> findActiveByTagIdsAndNameContainingIgnoreCase(@Param("tagIds") Collection<Long> tagIds, @Param("search") String search, Pageable pageable);

    // --- Condition filter queries (admin — all products) ---

    Page<Product> findByConditionType(ConditionType conditionType, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.conditionType = :condition AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Product> findByConditionTypeAndNameContainingIgnoreCase(@Param("condition") ConditionType conditionType, @Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p JOIN p.tags t WHERE t.id IN :tagIds AND p.conditionType = :condition")
    Page<Product> findByTagIdsAndConditionType(@Param("tagIds") Collection<Long> tagIds, @Param("condition") ConditionType conditionType, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p JOIN p.tags t WHERE t.id IN :tagIds AND p.conditionType = :condition AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Product> findByTagIdsAndConditionTypeAndNameContainingIgnoreCase(@Param("tagIds") Collection<Long> tagIds, @Param("condition") ConditionType conditionType, @Param("search") String search, Pageable pageable);

    // --- Condition filter queries (public — active only) ---

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.conditionType = :condition")
    Page<Product> findByActiveTrueAndConditionType(@Param("condition") ConditionType conditionType, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.conditionType = :condition AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Product> findByActiveTrueAndConditionTypeAndNameContainingIgnoreCase(@Param("condition") ConditionType conditionType, @Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p JOIN p.tags t WHERE t.id IN :tagIds AND p.active = true AND p.conditionType = :condition")
    Page<Product> findActiveByTagIdsAndConditionType(@Param("tagIds") Collection<Long> tagIds, @Param("condition") ConditionType conditionType, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p JOIN p.tags t WHERE t.id IN :tagIds AND p.active = true AND p.conditionType = :condition AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Product> findActiveByTagIdsAndConditionTypeAndNameContainingIgnoreCase(@Param("tagIds") Collection<Long> tagIds, @Param("condition") ConditionType conditionType, @Param("search") String search, Pageable pageable);
}
