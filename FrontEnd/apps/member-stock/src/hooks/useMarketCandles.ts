import { useCallback, useEffect, useState } from "react";
import { fetchMarketCandles } from "../api/stockApi";
import { toKoreanStockMessage } from "../messages/stockErrorMessages";
import type { Candle } from "../types/marketData";

const CANDLE_POLL_INTERVAL_MS = 30_000;

export function useMarketCandles(symbol: string) {
    const [candles, setCandles] = useState<Candle[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);

    const refresh = useCallback(() => setRefreshKey((value) => value + 1), []);

    useEffect(() => {
        const controller = new AbortController();
        let firstLoad = true;

        const load = async () => {
            if (document.visibilityState === "hidden") {
                return;
            }

            if (firstLoad) {
                setLoading(true);
            }

            try {
                const nextCandles = await fetchMarketCandles(symbol, controller.signal);
                setCandles(nextCandles);
                setError(null);
            } catch (cause) {
                if (!controller.signal.aborted) {
                    setError(toKoreanStockMessage(cause, "차트 데이터를 불러오지 못했습니다."));
                }
            } finally {
                if (!controller.signal.aborted) {
                    setLoading(false);
                    firstLoad = false;
                }
            }
        };

        void load();
        const intervalId = window.setInterval(() => void load(), CANDLE_POLL_INTERVAL_MS);

        return () => {
            controller.abort();
            window.clearInterval(intervalId);
        };
    }, [refreshKey, symbol]);

    return { candles, loading, error, refresh };
}

