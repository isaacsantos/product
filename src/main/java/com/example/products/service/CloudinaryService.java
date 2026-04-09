package com.example.products.service;

import com.example.products.model.CloudinaryUploadResult;
import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {
    CloudinaryUploadResult upload(MultipartFile file);
    void delete(String publicId);
}
