package com.example.products.service;

import com.example.products.exception.FirebaseUserNotFoundException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RoleManagementServiceImpl implements RoleManagementService {

    private final FirebaseAuth firebaseAuth;

    public RoleManagementServiceImpl(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public void assignRoles(String uid, List<String> roles) {
        try {
            firebaseAuth.setCustomUserClaims(uid, Map.of("roles", roles));
        } catch (FirebaseAuthException e) {
            if ("USER_NOT_FOUND".equals(e.getAuthErrorCode().name())) {
                throw new FirebaseUserNotFoundException(uid);
            }
            throw new RuntimeException("Firebase error: " + e.getMessage(), e);
        }
    }
}
