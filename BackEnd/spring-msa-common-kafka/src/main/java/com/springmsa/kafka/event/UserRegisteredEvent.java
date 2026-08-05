package com.springmsa.kafka.event;

import java.util.Set;

public record UserRegisteredEvent(
        Long userId,
        String loginId,
        String email,
        String username,
        Set<String> roles
) {
}
