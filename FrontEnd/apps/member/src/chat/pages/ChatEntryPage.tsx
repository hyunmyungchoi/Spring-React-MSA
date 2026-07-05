import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { chatWebSocketUrl, fetchChatMessages } from "../api/chatApi";
import type { ChatMessage, ChatServerMessage } from "../types/chatMessage";
import { useUserMe } from "../../common/hooks/useUserMe";

const ROOM_ID = "global";
const MAX_RENDERED_MESSAGES = 200;
const HEARTBEAT_INTERVAL_MS = 25_000;
const HEARTBEAT_TIMEOUT_MS = 10_000;
const RECONNECT_INITIAL_DELAY_MS = 1_000;
const RECONNECT_MAX_DELAY_MS = 10_000;

function ChatEntryPage() {
    const { me } = useUserMe();
    const socketRef = useRef<WebSocket | null>(null);
    const heartbeatIntervalRef = useRef<number | null>(null);
    const heartbeatTimeoutRef = useRef<number | null>(null);
    const reconnectTimerRef = useRef<number | null>(null);
    const reconnectDelayRef = useRef(RECONNECT_INITIAL_DELAY_MS);
    const shouldReconnectRef = useRef(true);
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [draft, setDraft] = useState("");
    const [connected, setConnected] = useState(false);
    const [status, setStatus] = useState("Connecting...");

    useEffect(() => {
        const controller = new AbortController();

        fetchChatMessages(ROOM_ID, controller.signal)
            .then((history) => setMessages((current) => mergeMessages(current, history)))
            .catch((error) => {
                if (!controller.signal.aborted) {
                    setStatus(error instanceof Error ? error.message : "Chat history load failed");
                }
            });

        return () => {
            controller.abort();
        };
    }, []);

    useEffect(() => {
        shouldReconnectRef.current = true;

        const connect = () => {
            if (!shouldReconnectRef.current) {
                return;
            }

            setStatus("Connecting...");

            const socket = new WebSocket(chatWebSocketUrl(ROOM_ID));
            socketRef.current = socket;

            socket.onopen = () => {
                reconnectDelayRef.current = RECONNECT_INITIAL_DELAY_MS;
                setConnected(true);
                setStatus("Connected");
                startHeartbeat(socket);
            };

            socket.onmessage = (event) => {
                markHeartbeatReceived();
                handleServerMessage(event.data);
            };

            socket.onerror = () => {
                setStatus("Chat connection error");
            };

            socket.onclose = (event) => {
                if (socketRef.current === socket) {
                    socketRef.current = null;
                }

                clearHeartbeat();
                setConnected(false);

                if (!shouldReconnectRef.current) {
                    setStatus("Disconnected");
                    return;
                }

                if (event.code === 1008) {
                    setStatus(event.reason || "Member session is required");
                    return;
                }

                scheduleReconnect(connect);
            };
        };

        connect();

        return () => {
            shouldReconnectRef.current = false;
            clearReconnectTimer();
            clearHeartbeat();

            if (socketRef.current) {
                socketRef.current.close();
                socketRef.current = null;
            }
        };
    }, []);

    const startHeartbeat = (socket: WebSocket) => {
        clearHeartbeat();

        heartbeatIntervalRef.current = window.setInterval(() => {
            if (socket.readyState !== WebSocket.OPEN) {
                return;
            }

            try {
                socket.send(JSON.stringify({ type: "PING" }));
            } catch {
                socket.close();
                return;
            }

            clearHeartbeatTimeout();

            heartbeatTimeoutRef.current = window.setTimeout(() => {
                setStatus("Chat heartbeat timeout");
                socket.close();
            }, HEARTBEAT_TIMEOUT_MS);
        }, HEARTBEAT_INTERVAL_MS);
    };

    const markHeartbeatReceived = () => {
        clearHeartbeatTimeout();
    };

    const clearHeartbeat = () => {
        if (heartbeatIntervalRef.current !== null) {
            window.clearInterval(heartbeatIntervalRef.current);
            heartbeatIntervalRef.current = null;
        }

        clearHeartbeatTimeout();
    };

    const clearHeartbeatTimeout = () => {
        if (heartbeatTimeoutRef.current !== null) {
            window.clearTimeout(heartbeatTimeoutRef.current);
            heartbeatTimeoutRef.current = null;
        }
    };

    const scheduleReconnect = (connect: () => void) => {
        clearReconnectTimer();

        const delay = reconnectDelayRef.current;
        setStatus(`Reconnecting in ${Math.ceil(delay / 1000)}s...`);

        reconnectTimerRef.current = window.setTimeout(() => {
            reconnectTimerRef.current = null;
            connect();
        }, delay);

        reconnectDelayRef.current = Math.min(delay * 2, RECONNECT_MAX_DELAY_MS);
    };

    const clearReconnectTimer = () => {
        if (reconnectTimerRef.current !== null) {
            window.clearTimeout(reconnectTimerRef.current);
            reconnectTimerRef.current = null;
        }
    };

    const handleServerMessage = (payload: string) => {
        try {
            const serverMessage = JSON.parse(payload) as ChatServerMessage;

            if (serverMessage.type === "ERROR") {
                setStatus(serverMessage.detail ?? "Chat message failed");
                return;
            }

            if (serverMessage.type === "CONNECTED") {
                setStatus("Connected");
                return;
            }

            if (serverMessage.type === "PONG") {
                setStatus("Connected");
                return;
            }

            if (serverMessage.type === "HISTORY") {
                setMessages((current) => mergeMessages(current, serverMessage.messages ?? []));
                return;
            }

            if (serverMessage.type === "CHAT_MESSAGE" && serverMessage.message) {
                setMessages((current) => mergeMessages(current, [serverMessage.message as ChatMessage]));
            }
        } catch {
            setStatus("Chat message parse failed");
        }
    };

    const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const content = draft.trim();

        if (!content) {
            return;
        }

        const socket = socketRef.current;

        if (!socket || socket.readyState !== WebSocket.OPEN) {
            setStatus("Chat is not connected");
            return;
        }

        socket.send(JSON.stringify({
            type: "CHAT_MESSAGE",
            content,
        }));
        setDraft("");
    };

    return (
        <section className="info-panel chat-panel">
            <div className="chat-heading">
                <div>
                    <h2>Realtime Chat</h2>
                    <p className="entry-copy">Room: {ROOM_ID}</p>
                </div>
                <span className={connected ? "chat-status is-connected" : "chat-status"}>
                    {status}
                </span>
            </div>

            <div className="chat-window" aria-live="polite">
                {messages.length === 0 ? (
                    <p className="chat-empty">No messages yet.</p>
                ) : (
                    messages.map((message) => (
                        <article
                            className={isMine(message, me?.userId, me?.loginId) ? "chat-message is-mine" : "chat-message"}
                            key={message.streamId}
                        >
                            <div className="chat-message-meta">
                                <strong>{message.senderName ?? message.senderLoginId ?? "Member"}</strong>
                                <time dateTime={message.sentAt}>{formatTime(message.sentAt)}</time>
                            </div>
                            <p>{message.content}</p>
                        </article>
                    ))
                )}
            </div>

            <form className="chat-compose" onSubmit={handleSubmit}>
                <input
                    value={draft}
                    onChange={(event) => setDraft(event.target.value)}
                    maxLength={1000}
                    placeholder="Type a message"
                    aria-label="chat message"
                />
                <button className="primary-button" type="submit" disabled={!connected || !draft.trim()}>
                    Send
                </button>
            </form>
        </section>
    );
}

function mergeMessages(current: ChatMessage[], incoming: ChatMessage[]) {
    const byId = new Map<string, ChatMessage>();

    for (const message of current) {
        byId.set(message.streamId, message);
    }

    for (const message of incoming) {
        byId.set(message.streamId, message);
    }

    return Array.from(byId.values())
        .sort((left, right) => compareStreamIds(left.streamId, right.streamId))
        .slice(-MAX_RENDERED_MESSAGES);
}

function compareStreamIds(left: string, right: string) {
    const [leftMillis, leftSeq] = left.split("-").map(Number);
    const [rightMillis, rightSeq] = right.split("-").map(Number);

    if (leftMillis !== rightMillis) {
        return leftMillis - rightMillis;
    }

    return (leftSeq || 0) - (rightSeq || 0);
}

function isMine(message: ChatMessage, userId?: number, loginId?: string | null) {
    if (userId && message.senderUserId === userId) {
        return true;
    }

    return Boolean(loginId && message.senderLoginId === loginId);
}

function formatTime(value: string) {
    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "";
    }

    return new Intl.DateTimeFormat("ko-KR", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
    }).format(date);
}

export default ChatEntryPage;
