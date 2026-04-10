package com.example.products.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AiProductCreationService {
    void createProductsFromImages(List<MultipartFile> files);
}
