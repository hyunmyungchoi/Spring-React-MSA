package com.springmsa.membercommunityservice.community.repository;

import com.springmsa.membercommunityservice.community.domain.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    List<CommunityPost> findTop100ByOrderByCreatedAtDesc();

    Optional<CommunityPost> findByIdAndOwnerSub(Long id, String ownerSub);
}
