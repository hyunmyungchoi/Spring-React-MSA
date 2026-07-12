import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchMarketWorkspace } from "../api/stockApi";
import type { MarketWorkspace } from "../types/marketData";

const POLL_INTERVAL_MS = 2_000;

export function useMarketWorkspace(symbols: string[]) {
    const symbolsKey = useMemo(() => normalizeSymbols(symbols).join(","), [symbols]);
    const [workspace, setWorkspace] = useState<MarketWorkspace | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [refreshToken, setRefreshToken] = useState(0);
    const [visible, setVisible] = useState(() => isVisibleDocument());

    useEffect(() => {
        const handleVisibilityChange = () => {
            setVisible(isVisibleDocument());
        };

        document.addEventListener("visibilitychange", handleVisibilityChange);
        return () => document.removeEventListener("visibilitychange", handleVisibilityChange);
    }, []);

    useEffect(() => {
        if (!visible || !symbolsKey) {
            return undefined;
        }

        let cancelled = false;
        let timerId: number | null = null;
        let controller: AbortController | null = null;

        const load = async () => {
            controller = new AbortController();
            setLoading(true);

            try {
                const nextWorkspace = await fetchMarketWorkspace(symbolsKey.split(","), controller.signal);

                if (!cancelled) {
                    setWorkspace(nextWorkspace);
                    setError(null);
                }
            } catch (loadError) {
                if (!cancelled && !controller.signal.aborted) {
                    setError(loadError instanceof Error ? loadError.message : "시세 정보를 불러오지 못했습니다.");
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                    timerId = window.setTimeout(load, POLL_INTERVAL_MS);
                }
            }
        };

        void load();

        return () => {
            cancelled = true;

            if (timerId !== null) {
                window.clearTimeout(timerId);
            }

            controller?.abort();
        };
    }, [refreshToken, symbolsKey, visible]);

    const refresh = useCallback(() => {
        setRefreshToken((current) => current + 1);
    }, []);

    return {
        workspace,
        loading: visible && loading,
        error,
        paused: !visible,
        refresh,
    };
}

function normalizeSymbols(symbols: string[]) {
    const normalized = new Set<string>();

    for (const symbol of symbols) {
        const value = symbol.trim().toUpperCase();

        if (value) {
            normalized.add(value);
        }
    }

    return Array.from(normalized);
}

function isVisibleDocument() {
    return typeof document === "undefined" || document.visibilityState === "visible";
}
