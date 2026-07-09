package com.springmsa.memberbff.community.client;

import com.springmsa.memberbff.community.dto.CommunityPostRequest;
import com.springmsa.memberbff.community.dto.CommunityPostResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

@FeignClient(name = "member-community-api-client", url = "${bff.api.community-api-base-url}")
public interface CommunityApiClient {

    @GetMapping("/api/community/me")
    Map<String, Object> me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetMapping("/api/community/posts")
    List<CommunityPostResponse> findPosts(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PostMapping("/api/community/posts")
    CommunityPostResponse createPost(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody CommunityPostRequest request);

    @PutMapping("/api/community/posts/{postId}")
    CommunityPostResponse updatePost(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long postId, @RequestBody CommunityPostRequest request);

    @DeleteMapping("/api/community/posts/{postId}")
    void deletePost(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long postId);
}
