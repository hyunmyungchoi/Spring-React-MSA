package com.springmsa.memberbff.chat.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@NullMarked
public record ChatServerMessage(
        String type,
        String roomId,
        @Nullable ChatMessageResponse message,
        List<ChatMessageResponse> messages,
        @Nullable String detail,
        Instant occurredAt
) {

    public static ChatServerMessage connected(String roomId) {
        return new ChatServerMessage("CONNECTED", roomId, null, List.of(), null, Instant.now());
    }

    public static ChatServerMessage history(String roomId, List<ChatMessageResponse> messages) {
        return new ChatServerMessage("HISTORY", roomId, null, messages, null, Instant.now());
    }

    public static ChatServerMessage chat(String roomId, ChatMessageResponse message) {
        return new ChatServerMessage("CHAT_MESSAGE", roomId, message, List.of(), null, Instant.now());
    }

    public static ChatServerMessage error(String roomId, String detail) {
        return new ChatServerMessage("ERROR", roomId, null, List.of(), detail, Instant.now());
    }
}

