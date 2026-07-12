export function toKoreanStockMessage(error: unknown, fallback: string) {
    if (!(error instanceof Error)) {
        return fallback;
    }

    const message = error.message.toLowerCase();

    if (message.includes("duplicate") || message.includes("already exists")) {
        return "이미 등록된 종목입니다.";
    }

    if (message.includes("not found")) {
        return "대상 종목을 찾을 수 없습니다.";
    }

    if (message.includes("unauthorized") || message.includes("session")) {
        return "로그인이 필요합니다.";
    }

    return fallback;
}
