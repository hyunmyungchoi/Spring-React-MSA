package com.springmsa.userservice.user.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.userservice.user.dto.AdminUserResponse;
import com.springmsa.userservice.user.service.UserAdminQueryService;
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
    public MsaResponse<List<AdminUserResponse>> users() {
        return MsaResponse.ok(userAdminQueryService.findUsers());
    }

    @GetMapping("/users/{userId}")
    public MsaResponse<AdminUserResponse> user(@PathVariable Long userId) {
        return MsaResponse.ok(userAdminQueryService.findUser(userId));
    }
}
