import { describe, expect, it } from "vitest";
import type { Candle } from "../types/marketData";
import { calculateMarketChange, chronologicalCloses } from "./marketMath";

const candle = (timestamp: string, openPrice: string, closePrice: string): Candle => ({
    timestamp,
    openPrice,
    highPrice: closePrice,
    lowPrice: openPrice,
    closePrice,
    volume: "1000",
    currency: "KRW",
    fetchedAt: "2026-07-12T10:15:30Z",
    dataStatus: "FRESH",
});

describe("marketMath", () => {
    it("calculates the current change against the previous daily close", () => {
        const candles = [
            candle("2026-07-12T09:00:00+09:00", "71600", "72000"),
            candle("2026-07-11T09:00:00+09:00", "71000", "71600"),
        ];

        expect(calculateMarketChange("72000", candles)).toEqual({
            amount: 400,
            rate: 400 / 71600,
            direction: "up",
        });
    });

    it("orders Toss newest-first candles chronologically for chart rendering", () => {
        const candles = [
            candle("2026-07-12T09:00:00+09:00", "71600", "72000"),
            candle("2026-07-11T09:00:00+09:00", "71000", "71600"),
        ];

        expect(chronologicalCloses(candles).map((point) => point.value)).toEqual([71600, 72000]);
    });
});

