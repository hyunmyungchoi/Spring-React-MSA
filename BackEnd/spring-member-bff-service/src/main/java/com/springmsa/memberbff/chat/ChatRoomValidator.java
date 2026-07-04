package com.springmsa.memberbff.chat;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
public class ChatRoomValidator {

    public static final String DEFAULT_ROOM_ID = "global";

    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("[a-zA-Z0-9:_-]{1,80}");

    public String resolve(String roomId) {
        String resolvedRoomId = StringUtils.hasText(roomId) ? roomId.trim() : DEFAULT_ROOM_ID;

        if (!ROOM_ID_PATTERN.matcher(resolvedRoomId).matches()) {
            throw new IllegalArgumentException("Invalid chat room id");
        }

        return resolvedRoomId;
    }
}

