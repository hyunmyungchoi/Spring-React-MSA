package com.springmsa.userservice.user.service;

import com.springmsa.userservice.user.domain.User;
import com.springmsa.userservice.user.dto.CreateUserRequest;
import com.springmsa.userservice.user.dto.CreateUserResponse;
import com.springmsa.userservice.user.repository.UserRepository;
import com.springmsa.userservice.outbox.OutboxEventWriter;
import com.springmsa.kafka.event.MsaEventEnvelope;
import com.springmsa.kafka.event.UserRegisteredEvent;
import com.springmsa.kafka.topic.MsaKafkaTopics;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.time.Instant;

@Service
public class InternalUserCreateService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ROLE_USER");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventWriter outboxEventWriter;

    public InternalUserCreateService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            OutboxEventWriter outboxEventWriter
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional
    public CreateUserResponse create(CreateUserRequest request) {
        String loginId = normalizeRequired(request.loginId());
        String email = normalizeEmail(request.email());
        String username = normalizeRequired(request.username());
        String phoneNumber = normalizeOptional(request.phoneNumber());
        String whatsappNumber = normalizeOptional(request.whatsappNumber());
        Set<String> roles = normalizeRoles(request.roles());

        if (userRepository.existsByLoginId(loginId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Login ID already exists");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        User savedUser = userRepository.save(User.create(
                loginId,
                email,
                passwordEncoder.encode(request.password()),
                username,
                phoneNumber,
                whatsappNumber,
                roles
        ));

        Instant occurredAt = Instant.now();
        MsaEventEnvelope<UserRegisteredEvent> event = MsaEventEnvelope.create(
                "user.registered", 1, "spring-user-service", occurredAt,
                new UserRegisteredEvent(
                        savedUser.getUserId(), savedUser.getLoginId(), savedUser.getEmail(),
                        savedUser.getUsername(), Set.copyOf(savedUser.getRoles())
                )
        );
        outboxEventWriter.append(
                "User", savedUser.getUserId().toString(), MsaKafkaTopics.USER_REGISTERED_V1,
                savedUser.getLoginId(), event
        );

        return new CreateUserResponse(
                savedUser.getUserId(),
                savedUser.getLoginId(),
                savedUser.getEmail(),
                savedUser.getUsername(),
                savedUser.isEnabled(),
                savedUser.getRoles()
        );
    }

    private String normalizeRequired(String value) {
        return value.trim();
    }

    private String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private Set<String> normalizeRoles(Set<String> requestedRoles) {
        Set<String> roles = new LinkedHashSet<>();
        roles.add("ROLE_USER");

        if (requestedRoles != null) {
            for (String requestedRole : requestedRoles) {
                if (!ALLOWED_ROLES.contains(requestedRole)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported role: " + requestedRole);
                }

                roles.add(requestedRole);
            }
        }

        return roles;
    }
}
