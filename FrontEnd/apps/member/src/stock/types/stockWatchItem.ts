export type StockWatchItem = {
    id: number;
    symbol: string;
    memo: string;
    owner: string;
    createdAt: string;
    updatedAt: string;
};

export type StockWatchItemPayload = {
    symbol: string;
    memo: string;
};
