package com.springmsa.adminbff.client;

import com.springmsa.adminbff.client.dto.AdminUserCreateRequest;
import com.springmsa.adminbff.client.dto.AdminUserCreateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "admin-user-internal-client", url = "${admin-bff.api.user-internal-base-url}")
public interface AdminUserInternalClient {

    @PostMapping("/internal/users")
    AdminUserCreateResponse create(@RequestBody AdminUserCreateRequest request);
}
