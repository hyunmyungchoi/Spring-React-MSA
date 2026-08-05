import type { Candle } from "../types/marketData";

export type MarketChange = {
    amount: number;
    rate: number;
    direction: "up" | "down" | "flat";
};

export function calculateMarketChange(lastPrice: string, candles: Candle[]): MarketChange | null {
    const price = Number(lastPrice);
    const latestFirst = [...candles].sort((left, right) => Date.parse(right.timestamp) - Date.parse(left.timestamp));
    const basePrice = Number(latestFirst[1]?.closePrice ?? latestFirst[0]?.openPrice);

    if (!Number.isFinite(price) || !Number.isFinite(basePrice) || basePrice === 0) {
        return null;
    }

    const amount = price - basePrice;
    return {
        amount,
        rate: amount / basePrice,
        direction: amount > 0 ? "up" : amount < 0 ? "down" : "flat",
    };
}

export function chronologicalCloses(candles: Candle[]) {
    return candles
        .map((candle) => ({ timestamp: candle.timestamp, value: Number(candle.closePrice) }))
        .filter((point) => Number.isFinite(point.value))
        .sort((left, right) => Date.parse(left.timestamp) - Date.parse(right.timestamp));
}

