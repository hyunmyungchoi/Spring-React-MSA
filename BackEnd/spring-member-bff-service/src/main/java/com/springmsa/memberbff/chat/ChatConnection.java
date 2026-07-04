package com.springmsa.memberbff.chat;

import com.springmsa.memberbff.auth.dto.SessionUserResponse;

public record ChatConnection(
        String roomId,
        SessionUserResponse user
) {
}

