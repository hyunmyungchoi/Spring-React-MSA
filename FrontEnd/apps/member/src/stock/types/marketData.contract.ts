import { fetchMarketWorkspace } from "../api/stockApi";
import { useMarketWorkspace } from "../hooks/useMarketWorkspace";
import { toKoreanStockMessage } from "../messages/stockErrorMessages";
import type {
    DataStatus,
    MarketQuote,
    MarketWorkspace,
    PartialFailure,
    StockSummary,
} from "./marketData";
import type { StockWatchItem } from "./stockWatchItem";

export const statusContract: DataStatus = "FRESH";

export const quoteContract: MarketQuote = {
    symbol: "005930",
    lastPrice: "72000",
    currency: "KRW",
    timestamp: null,
    fetchedAt: "2026-07-12T10:15:30Z",
    dataStatus: statusContract,
};

export const stockContract: StockSummary = {
    symbol: "005930",
    name: "Samsung Electronics",
    englishName: "Samsung Electronics",
    market: "KOSPI",
    currency: "KRW",
    status: "ACTIVE",
    fetchedAt: "2026-07-12T10:15:30Z",
    dataStatus: "FRESH",
};

export const failureContract: PartialFailure = {
    component: "prices",
    code: "TOSS_MARKET_UNAVAILABLE",
    message: "Price source unavailable",
    traceId: "trace-1",
};

export const workspaceContract: MarketWorkspace = {
    stocks: [stockContract],
    prices: [quoteContract],
    watchItems: [] satisfies StockWatchItem[],
    failures: [failureContract],
};

export const fetchWorkspaceContract: (
    symbols: string[],
    signal?: AbortSignal
) => Promise<MarketWorkspace> = fetchMarketWorkspace;

export const useWorkspaceContract: (symbols: string[]) => {
    workspace: MarketWorkspace | null;
    loading: boolean;
    error: string | null;
    paused: boolean;
    refresh: () => void;
} = useMarketWorkspace;

export const crudFallbackMessageContract: string = toKoreanStockMessage(
    new Error("Stock watch item creation failed"),
    "관심 종목 저장에 실패했습니다."
);
