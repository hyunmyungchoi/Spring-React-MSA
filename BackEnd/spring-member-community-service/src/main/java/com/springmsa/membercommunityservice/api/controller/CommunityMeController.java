package com.springmsa.membercommunityservice.api;

import com.springmsa.membercommunityservice.service.CommunityMeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CommunityMeController {

    private final CommunityMeService communityMeService;

    public CommunityMeController(CommunityMeService communityMeService) {
        this.communityMeService = communityMeService;
    }

    @GetMapping("/health")
    public String health() {
        return "spring-member-community-service is running";
    }

    @GetMapping("/api/community/me")
    public Map<String, Object> me(Authentication authentication) {
        return communityMeService.me(authentication);
    }
}
