package com.example.products.exception;

public class FirebaseUserAlreadyExistsException extends RuntimeException {
    public FirebaseUserAlreadyExistsException(String email) {
        super("Email already in use: " + email);
    }
}
