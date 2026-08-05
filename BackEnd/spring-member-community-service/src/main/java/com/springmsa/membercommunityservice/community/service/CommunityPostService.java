package com.springmsa.membercommunityservice.community.service;

import com.springmsa.membercommunityservice.community.dto.CommunityPostRequest;
import com.springmsa.membercommunityservice.community.dto.CommunityPostResponse;
import com.springmsa.membercommunityservice.community.domain.CommunityPost;
import com.springmsa.membercommunityservice.community.repository.CommunityPostRepository;
import com.springmsa.membercommunityservice.outbox.OutboxEventWriter;
import com.springmsa.kafka.event.CommunityPostCreatedEvent;
import com.springmsa.kafka.event.MsaEventEnvelope;
import com.springmsa.kafka.topic.MsaKafkaTopics;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CommunityPostService {

    private final CommunityPostRepository repository;
    private final OutboxEventWriter outboxEventWriter;

    public CommunityPostService(CommunityPostRepository repository, OutboxEventWriter outboxEventWriter) {
        this.repository = repository;
        this.outboxEventWriter = outboxEventWriter;
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
        CommunityPost savedPost = repository.save(post);
        MsaEventEnvelope<CommunityPostCreatedEvent> event = MsaEventEnvelope.create(
                "community.post-created", 1, "spring-member-community-service", savedPost.getCreatedAt(),
                new CommunityPostCreatedEvent(
                        savedPost.getId(), savedPost.getOwnerSub(), savedPost.getAuthor(),
                        savedPost.getTitle(), savedPost.getCreatedAt()
                )
        );
        outboxEventWriter.append(
                "CommunityPost", savedPost.getId().toString(), MsaKafkaTopics.COMMUNITY_POST_CREATED_V1,
                savedPost.getId().toString(), event
        );
        return toResponse(savedPost, ownerSub);
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
