package com.springmsa.userservice.api;

import com.springmsa.userservice.admin.UserAdminQueryService;
import com.springmsa.userservice.admin.dto.AdminUserResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@NullMarked
@RestController
@RequestMapping("/api/user/admin")
public class UserAdminApiController {

    private final UserAdminQueryService userAdminQueryService;

    public UserAdminApiController(UserAdminQueryService userAdminQueryService) {
        this.userAdminQueryService = userAdminQueryService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return userAdminQueryService.findUsers();
    }

    @GetMapping("/users/{userId}")
    public AdminUserResponse user(@PathVariable Long userId) {
        return userAdminQueryService.findUser(userId);
    }
}