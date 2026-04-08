package com.example.products.service;

import com.example.products.exception.FirebaseUserNotFoundException;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceImplTest {

    @Mock
    private FirebaseAuth firebaseAuth;

    private RoleManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleManagementServiceImpl(firebaseAuth);
    }

    @Test
    void assignRoles_callsFirebaseWithCorrectClaims() throws Exception {
        String uid = "user123";
        List<String> roles = List.of("ADMIN", "USER");

        service.assignRoles(uid, roles);

        verify(firebaseAuth).setCustomUserClaims(uid, Map.of("roles", roles));
    }

    @Test
    void assignRoles_userNotFound_throwsFirebaseUserNotFoundException() throws Exception {
        String uid = "unknown-uid";
        FirebaseAuthException ex = mock(FirebaseAuthException.class);
        when(ex.getAuthErrorCode()).thenReturn(AuthErrorCode.USER_NOT_FOUND);
        doThrow(ex).when(firebaseAuth).setCustomUserClaims(eq(uid), any());

        assertThatThrownBy(() -> service.assignRoles(uid, List.of("ADMIN")))
                .isInstanceOf(FirebaseUserNotFoundException.class)
                .hasMessageContaining(uid);
    }

    @Test
    void assignRoles_otherFirebaseError_throwsRuntimeException() throws Exception {
        String uid = "user123";
        FirebaseAuthException ex = mock(FirebaseAuthException.class);
        when(ex.getAuthErrorCode()).thenReturn(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        when(ex.getMessage()).thenReturn("some error");
        doThrow(ex).when(firebaseAuth).setCustomUserClaims(eq(uid), any());

        assertThatThrownBy(() -> service.assignRoles(uid, List.of("ADMIN")))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(FirebaseUserNotFoundException.class);
    }
}
