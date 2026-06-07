package com.springmsa.userservice.admin;

import com.springmsa.userservice.admin.dto.AdminUserResponse;
import com.springmsa.userservice.repository.UserRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@NullMarked
@Service
public class UserAdminQueryService {

    private final UserRepository userRepository;

    public UserAdminQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<AdminUserResponse> findUsers() {
        return userRepository.findAll().stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    public AdminUserResponse findUser(Long userId) {
        return userRepository.findById(userId)
                .map(AdminUserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));
    }
}