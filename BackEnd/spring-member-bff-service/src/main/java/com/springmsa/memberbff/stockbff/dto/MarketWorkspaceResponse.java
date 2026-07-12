package com.springmsa.memberbff.stockbff.dto;

import java.util.List;

public record MarketWorkspaceResponse(
        List<StockSummaryResponse> stocks,
        List<MarketQuoteResponse> prices,
        List<StockWatchItemResponse> watchItems,
        List<PartialFailure> failures
) {
    public MarketWorkspaceResponse {
        stocks = stocks == null ? List.of() : List.copyOf(stocks);
        prices = prices == null ? List.of() : List.copyOf(prices);
        watchItems = watchItems == null ? List.of() : List.copyOf(watchItems);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
