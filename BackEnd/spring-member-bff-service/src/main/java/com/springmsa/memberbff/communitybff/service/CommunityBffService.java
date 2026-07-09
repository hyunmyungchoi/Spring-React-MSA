package com.springmsa.memberbff.communitybff.service;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberbff.auth.service.BffOAuth2ClientService;
import com.springmsa.memberbff.common.error.DownstreamFeignExceptionMapper;
import com.springmsa.memberbff.communitybff.client.CommunityServiceClient;
import com.springmsa.memberbff.communitybff.dto.CommunityPostRequest;
import com.springmsa.memberbff.communitybff.dto.CommunityPostResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommunityBffService {

    private static final String COMMUNITY_SERVICE_REQUEST_FAILED = "COMMUNITY_SERVICE_REQUEST_FAILED";

    private final BffOAuth2ClientService bffOAuth2ClientService;
    private final CommunityServiceClient communityServiceClient;

    public Map<String, Object> getCurrentCommunityUser(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        try {
            return communityServiceClient.me(bearerToken(authentication, request, response));
        } catch (FeignException exception) {
            throw communityFailure(exception, "Community user request failed");
        }
    }

    public List<CommunityPostResponse> findPosts(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        try {
            return communityServiceClient.findPosts(bearerToken(authentication, request, response));
        } catch (FeignException exception) {
            throw communityFailure(exception, "Community posts request failed");
        }
    }

    public CommunityPostResponse createPost(Authentication authentication, HttpServletRequest request, HttpServletResponse response, CommunityPostRequest postRequest) {
        try {
            return communityServiceClient.createPost(bearerToken(authentication, request, response), postRequest);
        } catch (FeignException exception) {
            throw communityFailure(exception, "Community post creation failed");
        }
    }

    public CommunityPostResponse updatePost(Authentication authentication, HttpServletRequest request, HttpServletResponse response, Long postId, CommunityPostRequest postRequest) {
        try {
            return communityServiceClient.updatePost(bearerToken(authentication, request, response), postId, postRequest);
        } catch (FeignException exception) {
            throw communityFailure(exception, "Community post update failed");
        }
    }

    public void deletePost(Authentication authentication, HttpServletRequest request, HttpServletResponse response, Long postId) {
        try {
            communityServiceClient.deletePost(bearerToken(authentication, request, response), postId);
        } catch (FeignException exception) {
            throw communityFailure(exception, "Community post deletion failed");
        }
    }

    private String bearerToken(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return "Bearer " + bffOAuth2ClientService.getAccessToken(authentication, request, response);
    }

    private ApiException communityFailure(FeignException exception, String fallbackMessage) {
        return DownstreamFeignExceptionMapper.toApiException(
                exception,
                COMMUNITY_SERVICE_REQUEST_FAILED,
                fallbackMessage,
                "Community service is unavailable"
        );
    }
}
