package com.springmsa.memberstockservice.market.cache;

import com.springmsa.memberstockservice.market.dto.CandleResponse;

import java.util.List;

public record CandleCachePayload(List<CandleResponse> candles) {
}
