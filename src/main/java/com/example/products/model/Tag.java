package com.example.products.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @ManyToMany(mappedBy = "tags")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Product> products = new HashSet<>();
}
