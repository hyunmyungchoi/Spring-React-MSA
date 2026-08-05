export function toKoreanStockMessage(error: unknown, fallback: string) {
    if (!(error instanceof Error)) return fallback;

    const message = error.message.toLowerCase();
    if (message.includes("duplicate") || message.includes("already exists")) return "이미 등록한 종목입니다.";
    if (message.includes("not found")) return "요청한 종목을 찾을 수 없습니다.";
    if (message.includes("unauthorized") || message.includes("session")) return "로그인이 필요합니다.";
    if (message.includes("token") || message.includes("toss")) {
        return "Toss 시세 연결이 잠시 불안정합니다. 잠시 후 다시 시도해 주세요.";
    }
    return fallback;
}
