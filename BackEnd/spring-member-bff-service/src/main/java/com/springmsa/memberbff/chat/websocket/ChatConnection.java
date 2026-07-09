package com.springmsa.memberbff.chat.websocket;

import com.springmsa.memberbff.auth.dto.SessionUserResponse;

public record ChatConnection(
        String roomId,
        SessionUserResponse user
) {
}
