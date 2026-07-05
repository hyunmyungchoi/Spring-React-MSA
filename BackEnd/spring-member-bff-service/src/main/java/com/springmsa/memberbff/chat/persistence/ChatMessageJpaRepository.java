package com.springmsa.memberbff.chat.persistence;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageJpaRepository extends JpaRepository<@NonNull ChatMessageEntity, @NonNull Long> {

    @Query("""
            select message
            from ChatMessageEntity message
            join fetch message.room room
            where room.roomId = :roomId
            order by message.id desc
            """)
    List<ChatMessageEntity> findRecentMessages(@Param("roomId") String roomId, Pageable pageable);
}
