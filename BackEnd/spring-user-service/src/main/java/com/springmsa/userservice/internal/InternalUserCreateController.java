package com.springmsa.userservice.internal;

import com.springmsa.userservice.domain.User;
import com.springmsa.userservice.internal.dto.CreateUserRequest;
import com.springmsa.userservice.internal.dto.CreateUserResponse;
import com.springmsa.userservice.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/internal/users")
public class InternalUserCreateController {

    private static final Set<String> ALLOWED_ROLES = Set.of("ROLE_USER", "ROLE_ADMIN");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public InternalUserCreateController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse create(@Valid @RequestBody CreateUserRequest request) {
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
