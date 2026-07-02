package com.springmsa.memberbff.community;

import com.springmsa.memberbff.auth.BffOAuth2ClientService;
import com.springmsa.memberbff.client.CommunityApiClient;
import com.springmsa.memberbff.community.dto.CommunityPostRequest;
import com.springmsa.memberbff.community.dto.CommunityPostResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final BffOAuth2ClientService bffOAuth2ClientService;
    private final CommunityApiClient communityApiClient;

    public Map<String, Object> getCurrentCommunityUser(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return communityApiClient.me(bearerToken(authentication, request, response));
    }

    public List<CommunityPostResponse> findPosts(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return communityApiClient.findPosts(bearerToken(authentication, request, response));
    }

    public CommunityPostResponse createPost(Authentication authentication, HttpServletRequest request, HttpServletResponse response, CommunityPostRequest postRequest) {
        return communityApiClient.createPost(bearerToken(authentication, request, response), postRequest);
    }

    public CommunityPostResponse updatePost(Authentication authentication, HttpServletRequest request, HttpServletResponse response, Long postId, CommunityPostRequest postRequest) {
        return communityApiClient.updatePost(bearerToken(authentication, request, response), postId, postRequest);
    }

    public void deletePost(Authentication authentication, HttpServletRequest request, HttpServletResponse response, Long postId) {
        communityApiClient.deletePost(bearerToken(authentication, request, response), postId);
    }

    private String bearerToken(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return "Bearer " + bffOAuth2ClientService.getAccessToken(authentication, request, response);
    }
}
