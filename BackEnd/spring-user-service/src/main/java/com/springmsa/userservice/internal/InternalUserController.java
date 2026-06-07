package com.springmsa.userservice.internal;

import com.springmsa.userservice.domain.User;
import com.springmsa.userservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserRepository userRepository;

    public InternalUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/login-id/{loginId}")
    public UserResponse findByLoginId(@PathVariable String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return new UserResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getEmail(),
                user.getUsername(),
                user.isEnabled(),
                user.getRoles()
        );
    }

    public record UserResponse(
            Long userId,
            String loginId,
            String email,
            String username,
            boolean enabled,
            Set<String> roles
    ) {
    }
}