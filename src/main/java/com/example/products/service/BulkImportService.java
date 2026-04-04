package com.example.products.service;

import com.example.products.model.ImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface BulkImportService {
    ImportResult importProducts(MultipartFile file) throws IOException;
}
