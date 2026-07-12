package com.springmsa.memberstockservice.toss.market.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TossCandlePageResponse(
        List<TossCandle> candles,
        OffsetDateTime nextBefore
) {
}
