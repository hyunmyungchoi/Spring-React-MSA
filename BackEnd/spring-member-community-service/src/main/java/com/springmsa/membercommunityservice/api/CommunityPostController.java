package com.springmsa.membercommunityservice.api;

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
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/community/posts")
public class CommunityPostController {

    private final AtomicLong sequence = new AtomicLong(0);
    private final ConcurrentHashMap<Long, CommunityPostResponse> posts = new ConcurrentHashMap<>();

    @GetMapping
    public List<CommunityPostResponse> findAll() {
        return posts.values().stream()
                .sorted(Comparator.comparing(CommunityPostResponse::id))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityPostResponse create(@RequestBody CommunityPostRequest request, Authentication authentication) {
        Long id = sequence.incrementAndGet();
        Instant now = Instant.now();
        CommunityPostResponse response = new CommunityPostResponse(
                id,
                request.title(),
                request.content(),
                authentication.getName(),
                now,
                now
        );
        posts.put(id, response);
        return response;
    }

    @PutMapping("/{postId}")
    public CommunityPostResponse update(@PathVariable Long postId, @RequestBody CommunityPostRequest request) {
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

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long postId) {
        if (posts.remove(postId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Community post not found");
        }
    }
}
