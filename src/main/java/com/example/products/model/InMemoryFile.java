package com.example.products.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InMemoryFile {
    private final String originalFilename;
    private final String contentType;
    private final byte[] bytes;
}
