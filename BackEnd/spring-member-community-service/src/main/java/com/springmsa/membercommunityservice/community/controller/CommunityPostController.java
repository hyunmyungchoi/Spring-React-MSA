package com.springmsa.membercommunityservice.community.controller;

import com.springmsa.membercommunityservice.community.dto.CommunityPostRequest;
import com.springmsa.membercommunityservice.community.dto.CommunityPostResponse;
import com.springmsa.membercommunityservice.community.service.CommunityPostService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/community/posts")
public class CommunityPostController {

    private final CommunityPostService communityPostService;

    public CommunityPostController(CommunityPostService communityPostService) {
        this.communityPostService = communityPostService;
    }

    @GetMapping
    public List<CommunityPostResponse> findAll() {
        return communityPostService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityPostResponse create(@RequestBody CommunityPostRequest request, Authentication authentication) {
        return communityPostService.create(request, authentication.getName());
    }

    @PutMapping("/{postId}")
    public CommunityPostResponse update(@PathVariable Long postId, @RequestBody CommunityPostRequest request) {
        return communityPostService.update(postId, request);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long postId) {
        communityPostService.delete(postId);
    }
}
