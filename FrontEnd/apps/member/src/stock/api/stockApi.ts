import type { UserServiceMeResponse } from "../../types/userSession";
import { memberRequest } from "../../api/memberApiClient";

export type StockWatchItem = {
    id: number;
    symbol: string;
    memo: string;
    owner: string;
    createdAt: string;
    updatedAt: string;
};

export type StockWatchItemRequest = {
    symbol: string;
    memo: string;
};

// Loads the current user profile from the stock service.
export function fetchStockMe(signal?: AbortSignal): Promise<UserServiceMeResponse> {
    return memberRequest<UserServiceMeResponse>({ url: "/bff/stock/me", signal });
}

export function fetchStockWatchItems(signal?: AbortSignal): Promise<StockWatchItem[]> {
    return memberRequest<StockWatchItem[]>({ url: "/bff/stock/watch-items", signal });
}

export function createStockWatchItem(request: StockWatchItemRequest): Promise<StockWatchItem> {
    return memberRequest<StockWatchItem>({
        url: "/bff/stock/watch-items",
        method: "POST",
        data: request,
    });
}

export function updateStockWatchItem(itemId: number, request: StockWatchItemRequest): Promise<StockWatchItem> {
    return memberRequest<StockWatchItem>({
        url: `/bff/stock/watch-items/${itemId}`,
        method: "PUT",
        data: request,
    });
}

export function deleteStockWatchItem(itemId: number): Promise<void> {
    return memberRequest<void>({
        url: `/bff/stock/watch-items/${itemId}`,
        method: "DELETE",
    });
}
