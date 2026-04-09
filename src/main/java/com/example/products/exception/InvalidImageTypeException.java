package com.example.products.exception;

public class InvalidImageTypeException extends RuntimeException {
    public InvalidImageTypeException(String contentType) {
        super("Unsupported image type: " + contentType);
    }
}
