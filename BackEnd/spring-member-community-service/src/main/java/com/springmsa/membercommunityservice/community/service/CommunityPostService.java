package com.springmsa.membercommunityservice.community.service;

import com.springmsa.membercommunityservice.community.dto.CommunityPostRequest;
import com.springmsa.membercommunityservice.community.dto.CommunityPostResponse;
import com.springmsa.membercommunityservice.community.domain.CommunityPost;
import com.springmsa.membercommunityservice.community.repository.CommunityPostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CommunityPostService {

    private final CommunityPostRepository repository;

    public CommunityPostService(CommunityPostRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CommunityPostResponse> findAll(String ownerSub) {
        return repository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(post -> toResponse(post, ownerSub))
                .toList();
    }

    @Transactional
    public CommunityPostResponse create(CommunityPostRequest request, String ownerSub, String author) {
        CommunityPost post = CommunityPost.create(ownerSub, author, request.title(), request.content());
        return toResponse(repository.save(post), ownerSub);
    }

    @Transactional
    public CommunityPostResponse update(Long postId, CommunityPostRequest request, String ownerSub) {
        CommunityPost post = ownedPost(postId, ownerSub);
        post.update(request.title(), request.content());
        return toResponse(post, ownerSub);
    }

    @Transactional
    public void delete(Long postId, String ownerSub) {
        repository.delete(ownedPost(postId, ownerSub));
    }

    private CommunityPost ownedPost(Long postId, String ownerSub) {
        return repository.findByIdAndOwnerSub(postId, ownerSub)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Community post not found"));
    }

    private CommunityPostResponse toResponse(CommunityPost post, String ownerSub) {
        return new CommunityPostResponse(
                post.getId(), post.getTitle(), post.getContent(), post.getAuthor(),
                post.getOwnerSub().equals(ownerSub), post.getCreatedAt(), post.getUpdatedAt()
        );
    }
}
