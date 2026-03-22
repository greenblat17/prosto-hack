package com.prosto.analytics.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                "test-secret-key-for-jwt-minimum-256-bits-for-hmac-sha");
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);
    }

    @Test
    @DisplayName("generate and validate — round trip")
    void generateAndValidate() {
        String email = "user@example.com";
        String token = jwtService.generateToken(email);

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isEqualTo(email);
    }

    @Test
    @DisplayName("different emails produce different tokens")
    void differentTokens() {
        String t1 = jwtService.generateToken("a@test.com");
        String t2 = jwtService.generateToken("b@test.com");

        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    @DisplayName("invalid token throws")
    void invalidToken() {
        assertThatThrownBy(() -> jwtService.validateToken("garbage.token.here"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("tampered token is rejected")
    void tamperedToken() {
        String token = jwtService.generateToken("user@test.com");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtService.validateToken(tampered))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("expired token is rejected")
    void expiredToken() {
        JwtService shortLived = new JwtService();
        ReflectionTestUtils.setField(shortLived, "secret",
                "test-secret-key-for-jwt-minimum-256-bits-for-hmac-sha");
        ReflectionTestUtils.setField(shortLived, "expiration", -1000L);

        String token = shortLived.generateToken("user@test.com");

        assertThatThrownBy(() -> shortLived.validateToken(token))
                .isInstanceOf(Exception.class);
    }
}
