package com.example.products;

import com.example.products.exception.FirebaseUserAlreadyExistsException;
import com.example.products.model.CreateUserRequest;
import com.example.products.model.UserResponse;
import com.example.products.service.RoleManagementService;
import com.example.products.service.UserManagementServiceImpl;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private RoleManagementService roleManagementService;

    private UserManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserManagementServiceImpl(firebaseAuth, roleManagementService);
    }

    private UserRecord mockUserRecord(String uid, String email, String displayName) {
        UserRecord record = mock(UserRecord.class);
        when(record.getUid()).thenReturn(uid);
        when(record.getEmail()).thenReturn(email);
        when(record.getDisplayName()).thenReturn(displayName);
        return record;
    }

    @Test
    void createUser_withNonEmptyRoles_invokesAssignRoles() throws FirebaseAuthException {
        UserRecord record = mockUserRecord("uid1", "test@test.com", null);
        when(firebaseAuth.createUser(any())).thenReturn(record);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@test.com");
        request.setRoles(List.of("ADMIN"));

        service.createUser(request);

        verify(roleManagementService).assignRoles("uid1", List.of("ADMIN"));
    }

    @Test
    void createUser_withNullRoles_doesNotInvokeAssignRoles() throws FirebaseAuthException {
        UserRecord record = mockUserRecord("uid1", "test@test.com", null);
        when(firebaseAuth.createUser(any())).thenReturn(record);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@test.com");
        request.setRoles(null);

        service.createUser(request);

        verify(roleManagementService, never()).assignRoles(any(), any());
    }

    @Test
    void createUser_withEmptyRoles_doesNotInvokeAssignRoles() throws FirebaseAuthException {
        UserRecord record = mockUserRecord("uid1", "test@test.com", null);
        when(firebaseAuth.createUser(any())).thenReturn(record);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@test.com");
        request.setRoles(List.of());

        service.createUser(request);

        verify(roleManagementService, never()).assignRoles(any(), any());
    }

    @Test
    void createUser_emailAlreadyExists_throwsFirebaseUserAlreadyExistsException() throws FirebaseAuthException {
        FirebaseAuthException ex = mock(FirebaseAuthException.class);
        when(ex.getAuthErrorCode()).thenReturn(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        when(firebaseAuth.createUser(any())).thenThrow(ex);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@test.com");

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(FirebaseUserAlreadyExistsException.class);
    }

    @Test
    void createUser_otherFirebaseError_throwsRuntimeException() throws FirebaseAuthException {
        FirebaseAuthException ex = mock(FirebaseAuthException.class);
        when(ex.getAuthErrorCode()).thenReturn(AuthErrorCode.CERTIFICATE_FETCH_FAILED);
        when(firebaseAuth.createUser(any())).thenThrow(ex);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@test.com");

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(FirebaseUserAlreadyExistsException.class);
    }

    @Test
    void createUser_withNullDisplayName_returnsUserResponseWithNullDisplayName() throws FirebaseAuthException {
        UserRecord record = mockUserRecord("uid1", "test@test.com", null);
        when(firebaseAuth.createUser(any())).thenReturn(record);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@test.com");

        UserResponse response = service.createUser(request);

        assertThat(response.getDisplayName()).isNull();
    }
}
