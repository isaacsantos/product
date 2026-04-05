package com.example.products.repository;

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
}
