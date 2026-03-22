package com.prosto.analytics.controller;

import com.prosto.analytics.dto.AuthResponseDto;
import com.prosto.analytics.service.AuthService;
import com.prosto.analytics.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /register — 201 with valid data")
    void registerSuccess() throws Exception {
        when(authService.register("user@test.com", "password123"))
                .thenReturn(new AuthResponseDto("jwt-token", "user@test.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@test.com","password":"password123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    @DisplayName("POST /register — 400 with blank email")
    void registerBlankEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"password123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register — 400 with invalid email format")
    void registerInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"password123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register — 400 with short password")
    void registerShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@test.com","password":"123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register — 409 when email taken")
    void registerConflict() throws Exception {
        when(authService.register("taken@test.com", "password123"))
                .thenThrow(new IllegalStateException("Email already taken"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"taken@test.com","password":"password123"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already taken"));
    }

    @Test
    @DisplayName("POST /login — 200 with valid credentials")
    void loginSuccess() throws Exception {
        when(authService.login("user@test.com", "password123"))
                .thenReturn(new AuthResponseDto("jwt-token", "user@test.com"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@test.com","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    @DisplayName("POST /login — 400 with blank password")
    void loginBlankPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@test.com","password":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /login — 403 with wrong credentials")
    void loginWrongCredentials() throws Exception {
        when(authService.login("user@test.com", "wrong"))
                .thenThrow(new SecurityException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@test.com","password":"wrong"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    @DisplayName("POST /login — 400 with missing body")
    void loginMissingBody() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
