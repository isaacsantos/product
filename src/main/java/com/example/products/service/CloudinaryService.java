package com.example.products.service;

import com.example.products.model.CloudinaryUploadResult;
import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {
    CloudinaryUploadResult upload(MultipartFile file);
    CloudinaryUploadResult upload(byte[] bytes, String contentType);
    void delete(String publicId);
}
