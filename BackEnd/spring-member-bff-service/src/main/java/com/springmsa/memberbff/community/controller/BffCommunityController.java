package com.springmsa.memberbff.community.controller;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.community.dto.CommunityPostRequest;
import com.springmsa.memberbff.community.dto.CommunityPostResponse;
import com.springmsa.memberbff.community.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class BffCommunityController {

    private final CommunityService communityService;

    @GetMapping("/community/me")
    public ResponseEntity<MsaResponse<Map<String, Object>>> me(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(MsaResponse.ok(communityService.getCurrentCommunityUser(authentication, request, response)));
    }

    @GetMapping("/community/posts")
    public ResponseEntity<MsaResponse<List<CommunityPostResponse>>> posts(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(MsaResponse.ok(communityService.findPosts(authentication, request, response)));
    }

    @PostMapping("/community/posts")
    public ResponseEntity<MsaResponse<CommunityPostResponse>> createPost(Authentication authentication, HttpServletRequest request, HttpServletResponse response, @RequestBody CommunityPostRequest postRequest) {
        return ResponseEntity.ok(MsaResponse.ok(communityService.createPost(authentication, request, response, postRequest)));
    }

    @PutMapping("/community/posts/{postId}")
    public ResponseEntity<MsaResponse<CommunityPostResponse>> updatePost(Authentication authentication, HttpServletRequest request, HttpServletResponse response, @PathVariable Long postId, @RequestBody CommunityPostRequest postRequest) {
        return ResponseEntity.ok(MsaResponse.ok(communityService.updatePost(authentication, request, response, postId, postRequest)));
    }

    @DeleteMapping("/community/posts/{postId}")
    public ResponseEntity<MsaResponse<Void>> deletePost(Authentication authentication, HttpServletRequest request, HttpServletResponse response, @PathVariable Long postId) {
        communityService.deletePost(authentication, request, response, postId);
        return ResponseEntity.ok(MsaResponse.ok(null));
    }
}
