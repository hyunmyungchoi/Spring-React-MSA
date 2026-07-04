package com.springmsa.memberbff.chat;

import com.springmsa.memberbff.auth.BffAuthenticationService;
import com.springmsa.memberbff.auth.dto.SessionUserResponse;
import com.springmsa.memberbff.chat.dto.ChatClientMessage;
import com.springmsa.memberbff.chat.dto.ChatServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String MESSAGE_TYPE_CHAT = "CHAT_MESSAGE";

    private final ObjectMapper objectMapper;
    private final BffAuthenticationService bffAuthenticationService;
    private final ChatMessageService chatMessageService;
    private final ChatWebSocketSessionRegistry chatWebSocketSessionRegistry;
    private final ChatStreamBroadcastScheduler chatStreamBroadcastScheduler;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Authentication authentication = resolveAuthentication(session.getPrincipal());

        if (!bffAuthenticationService.isAuthenticated(authentication)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Member session is required"));
            return;
        }

        String roomId = resolveRoomId(session);
        SessionUserResponse user = bffAuthenticationService.getSessionUser(authentication);

        chatWebSocketSessionRegistry.add(roomId, session, new ChatConnection(roomId, user));
        chatStreamBroadcastScheduler.watchRoom(roomId);

        chatWebSocketSessionRegistry.send(session, ChatServerMessage.connected(roomId));
        chatWebSocketSessionRegistry.send(session, ChatServerMessage.history(
                roomId,
                chatMessageService.findRecentMessages(roomId, null)
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChatConnection connection = chatWebSocketSessionRegistry.findConnection(session)
                .orElseThrow(() -> new IllegalStateException("Chat websocket session is not registered"));

        try {
            ChatClientMessage clientMessage = objectMapper.readValue(message.getPayload(), ChatClientMessage.class);

            if (!MESSAGE_TYPE_CHAT.equals(clientMessage.type())) {
                chatWebSocketSessionRegistry.send(session, ChatServerMessage.error(connection.roomId(), "Unsupported chat message type"));
                return;
            }

            chatMessageService.appendMessage(connection.roomId(), connection.user(), clientMessage.content());

        } catch (ResponseStatusException e) {
            chatWebSocketSessionRegistry.send(session, ChatServerMessage.error(connection.roomId(), errorMessage(e)));

        } catch (RuntimeException e) {
            log.warn("Failed to handle chat websocket message. sessionId={}", session.getId(), e);
            chatWebSocketSessionRegistry.send(session, ChatServerMessage.error(connection.roomId(), "Chat message failed"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        chatWebSocketSessionRegistry.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Chat websocket transport error. sessionId={}", session.getId(), exception);
        chatWebSocketSessionRegistry.remove(session);
        closeQuietly(session);
    }

    private Authentication resolveAuthentication(Principal principal) {
        return principal instanceof Authentication authentication ? authentication : null;
    }

    private String resolveRoomId(WebSocketSession session) throws IOException {
        String roomId = null;

        if (session.getUri() != null) {
            roomId = UriComponentsBuilder.fromUri(session.getUri())
                    .build()
                    .getQueryParams()
                    .getFirst("roomId");
        }

        try {
            return chatMessageService.resolveRoomId(roomId);

        } catch (ResponseStatusException e) {
            session.close(CloseStatus.BAD_DATA.withReason(errorMessage(e)));
            throw e;
        }
    }

    private String errorMessage(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());

        if (e.getReason() != null) {
            return e.getReason();
        }

        return status == null ? "Chat request failed" : status.getReasonPhrase();
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);

        } catch (IOException ignored) {
        }
    }
}
