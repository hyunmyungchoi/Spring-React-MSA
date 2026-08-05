package com.springmsa.membercommunityservice.community.service;

import com.springmsa.membercommunityservice.community.domain.CommunityPost;
import com.springmsa.membercommunityservice.community.dto.CommunityPostRequest;
import com.springmsa.membercommunityservice.community.repository.CommunityPostRepository;
import com.springmsa.membercommunityservice.outbox.OutboxEventWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityPostServiceTest {

    @Mock
    CommunityPostRepository repository;

    @Mock
    OutboxEventWriter outboxEventWriter;

    CommunityPostService service;

    @BeforeEach
    void setUp() {
        service = new CommunityPostService(repository, outboxEventWriter);
    }

    @Test
    void marksOnlyTheOwnersPostsAsEditable() {
        CommunityPost mine = CommunityPost.create("owner-1", "member1", "Mine", "Content");
        CommunityPost other = CommunityPost.create("owner-2", "member2", "Other", "Content");
        when(repository.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(mine, other));

        assertThat(service.findAll("owner-1"))
                .extracting(response -> response.ownedByCurrentUser())
                .containsExactly(true, false);
    }

    @Test
    void updatesOnlyARecordOwnedByTheAuthenticatedSubject() {
        CommunityPost post = CommunityPost.create("owner-1", "member1", "Before", "Before");
        when(repository.findByIdAndOwnerSub(1L, "owner-1")).thenReturn(Optional.of(post));

        var response = service.update(1L, new CommunityPostRequest("After", "Updated"), "owner-1");

        assertThat(response.title()).isEqualTo("After");
        assertThat(response.content()).isEqualTo("Updated");
        assertThat(response.ownedByCurrentUser()).isTrue();
    }

    @Test
    void hidesForeignPostExistenceDuringMutation() {
        when(repository.findByIdAndOwnerSub(1L, "owner-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, "owner-2"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deletesAnOwnedPost() {
        CommunityPost post = CommunityPost.create("owner-1", "member1", "Title", "Content");
        when(repository.findByIdAndOwnerSub(1L, "owner-1")).thenReturn(Optional.of(post));

        service.delete(1L, "owner-1");

        verify(repository).delete(post);
    }
}
