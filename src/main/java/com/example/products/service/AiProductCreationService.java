package com.example.products.service;

import com.example.products.model.InMemoryFile;

import java.util.List;

public interface AiProductCreationService {

    /**
     * Creates a SINGLE product from the given images. All images are treated as
     * depicting the same product. The product is created inactive with an
     * AI-estimated MXN price pre-filled for admin review.
     */
    void createProductFromImages(List<InMemoryFile> files);
}
