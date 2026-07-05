package com.springmsa.memberbff.chat.persistence;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomJpaRepository extends JpaRepository<@NonNull ChatRoomEntity, @NonNull Long> {

    Optional<ChatRoomEntity> findByRoomId(String roomId);
}
