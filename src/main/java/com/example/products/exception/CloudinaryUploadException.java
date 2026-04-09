package com.example.products.exception;

public class CloudinaryUploadException extends RuntimeException {
    public CloudinaryUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
