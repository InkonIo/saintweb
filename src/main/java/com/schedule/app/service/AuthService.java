package com.schedule.app.service;

import com.schedule.app.dto.request.AuthRequest;
import com.schedule.app.dto.response.AuthResponse;
import com.schedule.app.entity.User;
import com.schedule.app.exception.BusinessException;
import com.schedule.app.repository.UserRepository;
import com.schedule.app.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse.TokenResponse register(AuthRequest.Register request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email already in use: " + request.email());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        userRepository.save(user);
        String token = jwtService.generateToken(user);

        return new AuthResponse.TokenResponse(token, user.getUsername(), user.getEmail(), user.getRole());
    }

    public AuthResponse.TokenResponse login(AuthRequest.Login request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("User not found"));

        String token = jwtService.generateToken(user);
        return new AuthResponse.TokenResponse(token, user.getUsername(), user.getEmail(), user.getRole());
    }
}
