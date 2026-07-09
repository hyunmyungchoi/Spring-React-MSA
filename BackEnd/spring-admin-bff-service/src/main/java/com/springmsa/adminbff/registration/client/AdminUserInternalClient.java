package com.springmsa.adminbff.registration.client;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.adminbff.registration.client.dto.AdminUserCreateRequest;
import com.springmsa.adminbff.registration.client.dto.AdminUserCreateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "admin-user-internal-client",
        url = "${admin-bff.api.user-internal-base-url}",
        configuration = AdminUserInternalClientConfig.class
)
public interface AdminUserInternalClient {

    @PostMapping("/internal/users")
    MsaResponse<AdminUserCreateResponse> create(@RequestBody AdminUserCreateRequest request);
}
