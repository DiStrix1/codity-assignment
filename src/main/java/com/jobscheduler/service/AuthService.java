package com.jobscheduler.service;

import com.jobscheduler.api.dto.AuthDto;
import com.jobscheduler.api.exception.ConflictException;
import com.jobscheduler.domain.entity.Organization;
import com.jobscheduler.domain.entity.User;
import com.jobscheduler.domain.repository.OrganizationRepository;
import com.jobscheduler.domain.repository.UserRepository;
import com.jobscheduler.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }

        // Create or find organization
        Organization org;
        if (request.getOrganizationSlug() != null && !request.getOrganizationSlug().isBlank()) {
            org = organizationRepository.findBySlug(request.getOrganizationSlug())
                    .orElseGet(() -> {
                        Organization newOrg = Organization.builder()
                                .name(request.getOrganizationName() != null ? request.getOrganizationName() : request.getOrganizationSlug())
                                .slug(request.getOrganizationSlug())
                                .build();
                        return organizationRepository.save(newOrg);
                    });
        } else {
            // Use default organization
            org = organizationRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                    .orElseThrow(() -> new RuntimeException("Default organization not found"));
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role("MEMBER")
                .organization(org)
                .build();
        user = userRepository.save(user);

        String token = tokenProvider.generateToken(user.getEmail());

        return AuthDto.AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .organizationId(org.getId().toString())
                .organizationName(org.getName())
                .build();
    }

    @Transactional
    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String token = tokenProvider.generateToken(authentication);
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        return AuthDto.AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .organizationId(user.getOrganization().getId().toString())
                .organizationName(user.getOrganization().getName())
                .build();
    }
}
