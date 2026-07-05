package com.springmsa.memberbff.chat.event;

import com.springmsa.memberbff.chat.dto.ChatMessageResponse;

public record ChatMessageSavedEvent(
        ChatMessageResponse message
) {
}
