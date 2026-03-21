package com.prosto.analytics.service;

import com.prosto.analytics.dto.AuthResponseDto;
import com.prosto.analytics.model.User;
import com.prosto.analytics.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponseDto register(String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already taken");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        String token = jwtService.generateToken(email);
        return new AuthResponseDto(token, email);
    }

    public AuthResponseDto login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }
        String token = jwtService.generateToken(email);
        return new AuthResponseDto(token, email);
    }
}
