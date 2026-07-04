export type ChatMessage = {
    streamId: string;
    roomId: string;
    senderUserId?: number | null;
    senderLoginId?: string | null;
    senderName?: string | null;
    content: string;
    sentAt: string;
};

export type ChatServerMessage = {
    type: "CONNECTED" | "HISTORY" | "CHAT_MESSAGE" | "ERROR";
    roomId: string;
    message?: ChatMessage | null;
    messages?: ChatMessage[];
    detail?: string | null;
    occurredAt: string;
};

