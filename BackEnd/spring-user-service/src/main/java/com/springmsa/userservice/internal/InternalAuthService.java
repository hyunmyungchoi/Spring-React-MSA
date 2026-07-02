package com.springmsa.userservice.internal;

import com.springmsa.userservice.api.dto.AuthUserResponse;
import com.springmsa.userservice.domain.User;
import com.springmsa.userservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class InternalAuthService {

    private final UserRepository userRepository;

    public InternalAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthUserResponse findAuthUserByLoginId(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return toAuthUserResponse(user);
    }

    public AuthUserResponse findAuthUserByWhatsappNumber(String whatsappNumber) {
        User user = userRepository.findByWhatsappNumber(whatsappNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found by WhatsApp number"
                ));

        return toAuthUserResponse(user);
    }

    public AuthUserResponse findAuthUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found by email"));

        return toAuthUserResponse(user);
    }

    private AuthUserResponse toAuthUserResponse(User user) {
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
