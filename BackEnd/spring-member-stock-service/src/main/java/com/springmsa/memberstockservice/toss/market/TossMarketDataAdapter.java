package com.springmsa.memberstockservice.toss.market;

import com.springmsa.memberstockservice.market.domain.Candle;
import com.springmsa.memberstockservice.market.domain.CandleInterval;
import com.springmsa.memberstockservice.market.domain.MarketQuote;
import com.springmsa.memberstockservice.market.domain.StockSummary;
import com.springmsa.memberstockservice.toss.market.dto.TossCandle;
import com.springmsa.memberstockservice.toss.market.dto.TossPriceResponse;
import com.springmsa.memberstockservice.toss.market.dto.TossStockInfo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Component
public class TossMarketDataAdapter {

    private final TossMarketDataClient client;

    public TossMarketDataAdapter(TossMarketDataClient client) {
        this.client = client;
    }

    public List<MarketQuote> getPrices(Set<String> symbols) {
        return client.getPrices(symbols).stream()
                .map(price -> new MarketQuote(
                        price.symbol(),
                        decimal(price.lastPrice()),
                        price.currency(),
                        timestamp(price.timestamp())
                ))
                .toList();
    }

    public List<StockSummary> getStocks(Set<String> symbols) {
        return client.getStocks(symbols).stream()
                .map(stock -> new StockSummary(
                        stock.symbol(),
                        stock.name(),
                        stock.englishName(),
                        stock.market(),
                        stock.currency(),
                        stock.status()
                ))
                .toList();
    }

    public List<Candle> getCandles(String symbol, CandleInterval interval, int count) {
        return client.getCandles(symbol, interval, count).stream()
                .map(candle -> new Candle(
                        timestamp(candle.timestamp()),
                        decimal(candle.openPrice()),
                        decimal(candle.highPrice()),
                        decimal(candle.lowPrice()),
                        decimal(candle.closePrice()),
                        decimal(candle.volume()),
                        candle.currency()
                ))
                .toList();
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private OffsetDateTime timestamp(String value) {
        if (value == null) {
            return null;
        }

        return OffsetDateTime.parse(value);
    }
}
