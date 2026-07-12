package com.springmsa.memberstockservice.toss.market;

import com.springmsa.memberstockservice.market.domain.CandleInterval;
import com.springmsa.memberstockservice.toss.market.dto.TossCandle;
import com.springmsa.memberstockservice.toss.market.dto.TossPriceResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossStockInfo;

import java.util.List;
import java.util.Set;

public interface TossMarketDataClient {

    List<TossPriceResponse> getPrices(Set<String> symbols);

    List<TossStockInfo> getStocks(Set<String> symbols);

    List<TossCandle> getCandles(String symbol, CandleInterval interval, int count);
}
