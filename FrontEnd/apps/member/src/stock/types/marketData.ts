import type { StockWatchItem } from "./stockWatchItem";

export type DataStatus = "FRESH" | "STALE";

export type StockSummary = {
    symbol: string;
    name: string;
    englishName: string;
    market: string;
    currency: string;
    status: string;
    fetchedAt: string;
    dataStatus: DataStatus;
};

export type MarketQuote = {
    symbol: string;
    lastPrice: string;
    currency: string;
    timestamp: string | null;
    fetchedAt: string;
    dataStatus: DataStatus;
};

export type PartialFailure = {
    component: string;
    code: string;
    message: string;
    traceId?: string | null;
};

export type MarketWorkspace = {
    stocks: StockSummary[];
    prices: MarketQuote[];
    watchItems: StockWatchItem[];
    failures: PartialFailure[];
};
