package com.springmsa.userservice.internal;

import com.springmsa.userservice.domain.User;
import com.springmsa.userservice.internal.dto.UserResponse;
import com.springmsa.userservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class InternalUserQueryService {

    private final UserRepository userRepository;

    public InternalUserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse findByLoginId(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return UserResponse.from(user);
    }
}
