package com.prosto.analytics.service;

import com.prosto.analytics.dto.AuthResponseDto;
import com.prosto.analytics.model.User;
import com.prosto.analytics.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, jwtService, passwordEncoder);
    }

    @Test
    @DisplayName("register — success")
    void registerSuccess() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtService.generateToken("new@test.com")).thenReturn("jwt-token");

        AuthResponseDto result = authService.register("new@test.com", "password123");

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.email()).isEqualTo("new@test.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register — duplicate email throws IllegalStateException")
    void registerDuplicate() {
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("existing@test.com", "pass"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login — success")
    void loginSuccess() {
        User user = new User();
        user.setEmail("user@test.com");
        user.setPasswordHash("hashed");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
        when(jwtService.generateToken("user@test.com")).thenReturn("jwt-token");

        AuthResponseDto result = authService.login("user@test.com", "password");

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.email()).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("login — unknown email throws SecurityException")
    void loginUnknownEmail() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@test.com", "pass"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("login — wrong password throws SecurityException")
    void loginWrongPassword() {
        User user = new User();
        user.setEmail("user@test.com");
        user.setPasswordHash("hashed");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("user@test.com", "wrong"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid credentials");
    }
}
