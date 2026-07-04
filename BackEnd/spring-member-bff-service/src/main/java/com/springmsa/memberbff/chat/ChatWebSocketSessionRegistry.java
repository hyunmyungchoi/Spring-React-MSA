package com.springmsa.memberbff.chat;

import com.springmsa.memberbff.chat.dto.ChatServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketSessionRegistry {

    private final ObjectMapper objectMapper;

    private final ConcurrentMap<String, Set<WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ChatConnection> connectionsBySession = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> sendLocksBySession = new ConcurrentHashMap<>();

    public void add(String roomId, WebSocketSession session, ChatConnection connection) {
        sessionsByRoom.computeIfAbsent(roomId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        connectionsBySession.put(session.getId(), connection);
        sendLocksBySession.putIfAbsent(session.getId(), new Object());
    }

    public void remove(WebSocketSession session) {
        ChatConnection connection = connectionsBySession.remove(session.getId());
        sendLocksBySession.remove(session.getId());

        if (connection == null) {
            return;
        }

        Set<WebSocketSession> roomSessions = sessionsByRoom.get(connection.roomId());

        if (roomSessions == null) {
            return;
        }

        roomSessions.remove(session);

        if (roomSessions.isEmpty()) {
            sessionsByRoom.remove(connection.roomId(), roomSessions);
        }
    }

    public Optional<ChatConnection> findConnection(WebSocketSession session) {
        return Optional.ofNullable(connectionsBySession.get(session.getId()));
    }

    public Set<String> activeRoomIds() {
        return Set.copyOf(sessionsByRoom.keySet());
    }

    public void send(WebSocketSession session, ChatServerMessage message) {
        try {
            send(session, objectMapper.writeValueAsString(message));

        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize chat websocket message", e);
        }
    }

    public void broadcast(String roomId, ChatServerMessage message) {
        Set<WebSocketSession> roomSessions = sessionsByRoom.get(roomId);

        if (roomSessions == null || roomSessions.isEmpty()) {
            return;
        }

        String payload;

        try {
            payload = objectMapper.writeValueAsString(message);

        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize chat websocket message", e);
        }

        for (WebSocketSession session : roomSessions) {
            send(session, payload);
        }
    }

    private void send(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            remove(session);
            return;
        }

        Object sendLock = sendLocksBySession.computeIfAbsent(session.getId(), ignored -> new Object());

        synchronized (sendLock) {
            try {
                session.sendMessage(new TextMessage(payload));

            } catch (IOException e) {
                log.warn("Failed to send chat websocket message. sessionId={}", session.getId(), e);
                closeQuietly(session);
                remove(session);
            }
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);

        } catch (IOException ignored) {
        }
    }
}
