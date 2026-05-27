package com.springmsa.userservice.api;

import com.springmsa.userservice.api.dto.AuthUserResponse;
import com.springmsa.userservice.domain.User;
import com.springmsa.userservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    private final UserRepository userRepository;

    public InternalAuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users/{loginId}")
    public AuthUserResponse findAuthUserByLoginId(@PathVariable String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return new AuthUserResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getEmail(),
                user.getUsername(),
                user.getPhoneNumber(),
                user.getWhatsappNumber(),
                user.getPassword(),
                user.isEnabled(),
                user.getRoles()
        );
    }

    @GetMapping("/users/whatsapp/{whatsappNumber}")
    public AuthUserResponse getUserByWhatsappNumber(@PathVariable String whatsappNumber) {
        User user = userRepository.findByWhatsappNumber(whatsappNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found by WhatsApp number"));

        return new AuthUserResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getEmail(),
                user.getUsername(),
                user.getPhoneNumber(),
                user.getWhatsappNumber(),
                user.getPassword(),
                user.isEnabled(),
                user.getRoles()
        );
    }

    @GetMapping("/users/email/{email}")
    public AuthUserResponse getUserByEmail(@PathVariable String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found by email"));

        return new AuthUserResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getEmail(),
                user.getUsername(),
                user.getPhoneNumber(),
                user.getWhatsappNumber(),
                user.getPassword(),
                user.isEnabled(),
                user.getRoles()
        );
    }
}