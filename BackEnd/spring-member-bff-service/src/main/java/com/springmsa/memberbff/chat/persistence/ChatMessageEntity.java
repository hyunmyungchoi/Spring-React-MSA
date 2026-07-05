package com.springmsa.memberbff.chat.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_messages_room_message", columnList = "chat_room_id, chat_message_id"),
                @Index(name = "idx_chat_messages_room_sent_at", columnList = "chat_room_id, sent_at")
        }
)
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "chat_room_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chat_messages_room")
    )
    private ChatRoomEntity room;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Column(name = "sender_login_id", length = 100)
    private String senderLoginId;

    @Column(name = "sender_name", length = 100)
    private String senderName;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected ChatMessageEntity() {
    }

    public static ChatMessageEntity create(
            ChatRoomEntity room,
            Long senderUserId,
            String senderLoginId,
            String senderName,
            String content,
            Instant sentAt
    ) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.room = room;
        message.senderUserId = senderUserId;
        message.senderLoginId = senderLoginId;
        message.senderName = senderName;
        message.content = content;
        message.sentAt = sentAt;
        return message;
    }

    public Long getId() {
        return id;
    }

    public ChatRoomEntity getRoom() {
        return room;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public String getSenderLoginId() {
        return senderLoginId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getContent() {
        return content;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
