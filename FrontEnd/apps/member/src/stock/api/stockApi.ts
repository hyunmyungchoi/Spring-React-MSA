import { memberRequest } from "../../common/api/memberApiClient";
import type { UserServiceMeResponse } from "../../common/types/userSession";
import type { StockWatchItem, StockWatchItemPayload } from "../types/stockWatchItem";

// Loads the current user profile from the stock service.
export function fetchStockMe(signal?: AbortSignal): Promise<UserServiceMeResponse> {
    return memberRequest<UserServiceMeResponse>({ url: "/bff/stock/me", signal });
}

export function fetchStockWatchItems(signal?: AbortSignal): Promise<StockWatchItem[]> {
    return memberRequest<StockWatchItem[]>({ url: "/bff/stock/watch-items", signal });
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
