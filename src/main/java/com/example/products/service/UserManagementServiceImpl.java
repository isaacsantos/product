package com.example.products.service;

import com.example.products.exception.FirebaseUserAlreadyExistsException;
import com.example.products.model.CreateUserRequest;
import com.example.products.model.UserResponse;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private final FirebaseAuth firebaseAuth;
    private final RoleManagementService roleManagementService;

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                .setEmail(request.getEmail());

        if (request.getDisplayName() != null) {
            createRequest.setDisplayName(request.getDisplayName());
        }

        UserRecord record;
        try {
            record = firebaseAuth.createUser(createRequest);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                throw new FirebaseUserAlreadyExistsException(request.getEmail());
            }
            log.error("Firebase error creating user with email {}: {}", request.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Firebase error: " + e.getMessage(), e);
        }

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            roleManagementService.assignRoles(record.getUid(), request.getRoles());
        }

        return UserResponse.builder()
                .uid(record.getUid())
                .email(record.getEmail())
                .displayName(record.getDisplayName())
                .roles(request.getRoles() != null && !request.getRoles().isEmpty() ? request.getRoles() : List.of())
                .build();
    }
}
