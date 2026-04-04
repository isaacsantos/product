package com.example.products.exception;

public class ProductImageNotFoundException extends RuntimeException {

    public ProductImageNotFoundException(Long imageId) {
        super("Image not found: " + imageId);
    }
}
