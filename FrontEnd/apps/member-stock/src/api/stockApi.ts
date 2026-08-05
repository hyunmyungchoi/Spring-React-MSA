import { memberRequest } from "@springmsa/member-common/api/memberApiClient";
import type { UserServiceMeResponse } from "@springmsa/member-common/types/userSession";
import type { Candle, MarketWorkspace } from "../types/marketData";
import type { StockWatchItem, StockWatchItemPayload } from "../types/stockWatchItem";

// Loads the current user profile from the stock service.
export function fetchStockMe(signal?: AbortSignal): Promise<UserServiceMeResponse> {
    return memberRequest<UserServiceMeResponse>({ url: "/bff/stock/me", signal });
}

export function fetchStockWatchItems(signal?: AbortSignal): Promise<StockWatchItem[]> {
    return memberRequest<StockWatchItem[]>({ url: "/bff/stock/watch-items", signal });
}

export function fetchMarketWorkspace(symbols: string[], signal?: AbortSignal): Promise<MarketWorkspace> {
    return memberRequest<MarketWorkspace>({
        url: "/bff/stock/market/workspace",
        params: {
            symbols: normalizeSymbols(symbols).join(","),
        },
        signal,
    });
}

export function fetchMarketCandles(symbol: string, signal?: AbortSignal): Promise<Candle[]> {
    return memberRequest<Candle[]>({
        url: `/bff/stock/market/candles/${encodeURIComponent(symbol.trim().toUpperCase())}`,
        params: { interval: "1d", count: 30 },
        signal,
    });
}

export function createStockWatchItem(payload: StockWatchItemPayload): Promise<StockWatchItem> {
    return memberRequest<StockWatchItem>({
        url: "/bff/stock/watch-items",
        method: "POST",
        data: payload,
    });
}

export function updateStockWatchItem(itemId: number, payload: StockWatchItemPayload): Promise<StockWatchItem> {
    return memberRequest<StockWatchItem>({
        url: `/bff/stock/watch-items/${itemId}`,
        method: "PUT",
        data: payload,
    });
}

export function deleteStockWatchItem(itemId: number): Promise<void> {
    return memberRequest<void>({
        url: `/bff/stock/watch-items/${itemId}`,
        method: "DELETE",
    });
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

