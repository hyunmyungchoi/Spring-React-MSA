package com.springmsa.memberbff.chat.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "chat_rooms",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_rooms_room_id", columnNames = "room_id")
)
public class ChatRoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long id;

    @Column(name = "room_id", nullable = false, unique = true, length = 80)
    private String roomId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatRoomEntity() {
    }

    public static ChatRoomEntity create(String roomId, Instant createdAt) {
        ChatRoomEntity room = new ChatRoomEntity();
        room.roomId = roomId;
        room.createdAt = createdAt;
        return room;
    }

    public Long getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
