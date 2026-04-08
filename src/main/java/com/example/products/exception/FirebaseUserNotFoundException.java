package com.example.products.exception;

public class FirebaseUserNotFoundException extends RuntimeException {
    public FirebaseUserNotFoundException(String uid) {
        super("Firebase user not found: " + uid);
    }
}
