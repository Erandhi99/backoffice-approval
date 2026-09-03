package com.senfin.backoffice_approval.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.senfin.backoffice_approval.dto.AuthResponse;
import com.senfin.backoffice_approval.dto.LoginRequest;
import com.senfin.backoffice_approval.dto.RegisterRequest;
import com.senfin.backoffice_approval.entity.User;
import com.senfin.backoffice_approval.repository.UserRepository;
import com.senfin.backoffice_approval.security.CustomUserDetailsService;
import com.senfin.backoffice_approval.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already taken: " + request.username());
        }

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .email(request.email())
                .role(request.role())
                .enabled(true)
                .build();
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        // Delegates credential checking to Spring Security's AuthenticationManager,
        // which uses our DaoAuthenticationProvider + BCrypt under the hood.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalStateException("User vanished after authentication"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }
}