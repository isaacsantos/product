package com.example.products.service;

import com.example.products.model.InMemoryFile;

import java.util.List;

public interface AiProductCreationService {
    void createProductsFromImages(List<InMemoryFile> files);
}
