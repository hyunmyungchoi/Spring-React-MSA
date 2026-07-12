package com.springmsa.memberstockservice.toss.market;

import com.springmsa.memberstockservice.toss.market.dto.TossApiResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossCandlePageResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossPriceResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossStockInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "toss-market-data-feign-client", url = "${toss.api.base-url}")
public interface TossMarketDataFeignClient {

    @GetMapping("/api/v1/prices")
    TossApiResponse<List<TossPriceResponse>> getPrices(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam("symbols") String symbols
    );

    @GetMapping("/api/v1/stocks")
    TossApiResponse<List<TossStockInfo>> getStocks(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam("symbols") String symbols
    );

    @GetMapping("/api/v1/candles")
    TossApiResponse<TossCandlePageResponse> getCandles(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam("symbol") String symbol,
            @RequestParam("interval") String interval,
            @RequestParam("count") int count
    );
}
