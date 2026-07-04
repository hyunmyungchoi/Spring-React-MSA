package com.springmsa.memberbff.chat.dto;

public record ChatClientMessage(
        String type,
        String content
) {
}

