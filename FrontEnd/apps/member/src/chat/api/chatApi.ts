import { memberRequest } from "../../common/api/memberApiClient";
import type { ChatMessage } from "../types/chatMessage";

export function fetchChatMessages(roomId: string, signal?: AbortSignal): Promise<ChatMessage[]> {
    return memberRequest<ChatMessage[]>({
        url: `/bff/chat/rooms/${encodeURIComponent(roomId)}/messages`,
        params: {
            limit: 50,
        },
        signal,
    });
}

export function chatWebSocketUrl(roomId: string) {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}/bff/chat/ws?roomId=${encodeURIComponent(roomId)}`;
}

