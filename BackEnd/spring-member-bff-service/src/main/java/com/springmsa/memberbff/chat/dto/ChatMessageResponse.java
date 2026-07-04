package com.springmsa.memberbff.chat.dto;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@NullMarked
public record ChatMessageResponse(
        String streamId,
        String roomId,
        @Nullable Long senderUserId,
        @Nullable String senderLoginId,
        @Nullable String senderName,
        String content,
        Instant sentAt
) {
}

