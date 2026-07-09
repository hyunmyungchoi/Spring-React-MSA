package com.springmsa.membercommunityservice.community.service;

import com.springmsa.membercommunityservice.community.dto.CommunityPostRequest;
import com.springmsa.membercommunityservice.community.dto.CommunityPostResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CommunityPostService {

    private final AtomicLong sequence = new AtomicLong(0);
    private final ConcurrentHashMap<Long, CommunityPostResponse> posts = new ConcurrentHashMap<>();

    public List<CommunityPostResponse> findAll() {
        return posts.values().stream()
                .sorted(Comparator.comparing(CommunityPostResponse::id))
                .toList();
    }

    public CommunityPostResponse create(CommunityPostRequest request, String author) {
        Long id = sequence.incrementAndGet();
        Instant now = Instant.now();
        CommunityPostResponse response = new CommunityPostResponse(
                id,
                request.title(),
                request.content(),
                author,
                now,
                now
        );
        posts.put(id, response);
        return response;
    }

    public CommunityPostResponse update(Long postId, CommunityPostRequest request) {
        CommunityPostResponse current = posts.get(postId);

        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Community post not found");
        }

        CommunityPostResponse updated = new CommunityPostResponse(
                current.id(),
                request.title(),
                request.content(),
                current.author(),
                current.createdAt(),
                Instant.now()
        );
        posts.put(postId, updated);
        return updated;
    }

    public void delete(Long postId) {
        if (posts.remove(postId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Community post not found");
        }
    }
}
