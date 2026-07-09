package com.springmsa.adminbff.user.controller;

import com.springmsa.adminbff.user.dto.AdminCurrentUserResponse;
import com.springmsa.adminbff.user.dto.AdminUserResponse;
import com.springmsa.adminbff.user.service.AdminBffUserService;
import com.springmsa.common.web.response.MsaResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AdminBffUserController {

    private final AdminBffUserService adminBffUserService;

    /**
     * Returns the current admin session user through the user-service API.
     *
     * @param authentication current Spring Security authentication
     * @param request current HTTP request
     * @param response current HTTP response
     * @return current admin user response
     */
    @GetMapping("/user/me")
    public ResponseEntity<MsaResponse<AdminCurrentUserResponse>> me(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(
                MsaResponse.ok(adminBffUserService.getCurrentUser(authentication, request, response))
        );
    }

    /**
     * Lists users visible to an authenticated admin.
     *
     * @param authentication current Spring Security authentication
     * @param request current HTTP request
     * @param response current HTTP response
     * @return admin user list response
     */
    @GetMapping("/user/admin/users")
    public ResponseEntity<MsaResponse<List<AdminUserResponse>>> users(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(
                MsaResponse.ok(adminBffUserService.getAdminUsers(authentication, request, response))
        );
    }

    /**
     * Loads one admin-visible user by id.
     *
     * @param userId user id to load
     * @param authentication current Spring Security authentication
     * @param request current HTTP request
     * @param response current HTTP response
     * @return admin user detail response
     */
    @GetMapping("/user/admin/users/{userId}")
    public ResponseEntity<MsaResponse<AdminUserResponse>> user(@PathVariable Long userId, Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(
                MsaResponse.ok(adminBffUserService.getAdminUser(userId, authentication, request, response))
        );
    }
}
