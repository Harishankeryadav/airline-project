package com.airline.auth.service;

import com.airline.auth.dto.AuthResponse;
import com.airline.auth.dto.SigninRequest;
import com.airline.auth.dto.SignupRequest;
import com.airline.auth.dto.UserResponse;
import com.airline.auth.entity.Role;
import com.airline.auth.entity.RoleName;
import com.airline.auth.entity.User;
import com.airline.auth.exception.DuplicateResourceException;
import com.airline.auth.exception.UnauthorizedException;
import com.airline.auth.repository.RoleRepository;
import com.airline.auth.repository.UserRepository;
import com.airline.auth.security.AuthenticatedUser;
import com.airline.auth.security.JwtProperties;
import com.airline.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost: fast tests
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties("test-secret-test-secret-test-secret-1234", 60, "test-issuer"));
        authService = new AuthService(userRepository, roleRepository, encoder, jwtService);
    }

    private User existingUser(String email, String rawPassword, boolean enabled) {
        User user = new User();
        user.setId(7L);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setEnabled(enabled);
        user.getRoles().add(new Role(RoleName.CUSTOMER));
        return user;
    }

    @Test
    void signupNormalisesEmailHashesPasswordAndAssignsCustomerRole() {
        when(userRepository.existsByEmailIgnoreCase("ana@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(new Role(RoleName.CUSTOMER)));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        UserResponse response = authService.signup(new SignupRequest("  Ana@Example.com ", "password123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("ana@example.com", saved.getValue().getEmail());
        assertNotEquals("password123", saved.getValue().getPasswordHash());
        assertTrue(encoder.matches("password123", saved.getValue().getPasswordHash()));
        assertEquals(List.of("CUSTOMER"), response.roles());
        assertEquals(7L, response.id());
    }

    @Test
    void signupWithExistingEmailIsAConflictNotACrash() {
        when(userRepository.existsByEmailIgnoreCase("ana@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> authService.signup(new SignupRequest("ana@example.com", "password123")));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void signinWithCorrectPasswordReturnsAVerifiableToken() {
        when(userRepository.findByEmailIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(existingUser("ana@example.com", "password123", true)));

        AuthResponse response = authService.signin(new SigninRequest(" ANA@example.com", "password123"));

        assertEquals("Bearer", response.tokenType());
        assertEquals(3600, response.expiresInSeconds());
        AuthenticatedUser identity = jwtService.parse(response.token()).orElseThrow();
        assertEquals(7L, identity.id());
        assertEquals(List.of("CUSTOMER"), identity.roles());
    }

    @Test
    void signinWithUnknownEmailIs401() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> authService.signin(new SigninRequest("nobody@example.com", "password123")));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    void signinWithWrongPasswordGivesTheSameMessageAsUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(existingUser("ana@example.com", "password123", true)));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> authService.signin(new SigninRequest("ana@example.com", "wrong-password")));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    void disabledAccountCannotSignIn() {
        when(userRepository.findByEmailIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(existingUser("ana@example.com", "password123", false)));

        assertThrows(UnauthorizedException.class,
                () -> authService.signin(new SigninRequest("ana@example.com", "password123")));
    }
}
