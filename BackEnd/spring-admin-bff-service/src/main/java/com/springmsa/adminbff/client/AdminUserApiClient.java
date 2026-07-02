package com.springmsa.adminbff.client;

import com.springmsa.adminbff.user.dto.AdminCurrentUserResponse;
import com.springmsa.adminbff.user.dto.AdminUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "admin-user-api-client", url = "${admin-bff.api.user-api-base-url}")
public interface AdminUserApiClient {

    @GetMapping("/api/user/me")
    AdminCurrentUserResponse me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetMapping("/api/user/admin/users")
    List<AdminUserResponse> users(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetMapping("/api/user/admin/users/{userId}")
    AdminUserResponse user(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long userId);
}
