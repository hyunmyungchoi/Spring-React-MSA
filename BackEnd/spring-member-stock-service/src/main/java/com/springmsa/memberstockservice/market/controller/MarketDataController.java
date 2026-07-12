package com.springmsa.memberstockservice.market.controller;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.market.domain.CandleInterval;
import com.springmsa.memberstockservice.market.dto.CandleResponse;
import com.springmsa.memberstockservice.market.dto.MarketQuoteResponse;
import com.springmsa.memberstockservice.market.dto.StockSummaryResponse;
import com.springmsa.memberstockservice.market.error.MarketDataErrorCode;
import com.springmsa.memberstockservice.market.service.MarketDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/stock/market")
public class MarketDataController {

    private static final int MAX_SYMBOLS = 200;
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[A-Za-z0-9.-]{1,20}");

    private final MarketDataService service;

    public MarketDataController(MarketDataService service) {
        this.service = service;
    }

    @GetMapping("/prices")
    public List<MarketQuoteResponse> prices(@RequestParam(defaultValue = "") String symbols) {
        return service.getPrices(symbolSet(symbols));
    }

    @GetMapping("/stocks")
    public List<StockSummaryResponse> stocks(@RequestParam(defaultValue = "") String symbols) {
        return service.getStocks(symbolSet(symbols));
    }

    @GetMapping("/candles/{symbol}")
    public List<CandleResponse> candles(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1m") String interval,
            @RequestParam(defaultValue = "100") String count
    ) {
        return service.getCandles(normalizeSymbol(symbol), candleInterval(interval), candleCount(count));
    }

    private LinkedHashSet<String> symbolSet(String rawSymbols) {
        LinkedHashSet<String> symbols = new LinkedHashSet<>();

        for (String rawSymbol : rawSymbols.split(",", -1)) {
            String symbol = normalizeSymbol(rawSymbol);
            symbols.add(symbol);
        }

        if (symbols.size() > MAX_SYMBOLS) {
            throw new ApiException(MarketDataErrorCode.TOO_MANY_MARKET_SYMBOLS);
        }

        return symbols;
    }

    private String normalizeSymbol(String rawSymbol) {
        String symbol = rawSymbol == null
                ? ""
                : rawSymbol.trim().toUpperCase(Locale.ROOT);

        if (!SYMBOL_PATTERN.matcher(symbol).matches()) {
            throw new ApiException(MarketDataErrorCode.INVALID_MARKET_SYMBOL);
        }

        return symbol;
    }

    private CandleInterval candleInterval(String value) {
        return switch (value) {
            case "1m" -> CandleInterval.MINUTE_1;
            case "1d" -> CandleInterval.DAY_1;
            default -> throw new ApiException(MarketDataErrorCode.INVALID_CANDLE_INTERVAL);
        };
    }

    private int candleCount(String value) {
        try {
            int count = Integer.parseInt(value);
            if (count >= 1 && count <= 200) {
                return count;
            }
        } catch (NumberFormatException ignored) {
            // fall through to the domain-specific error code
        }

        throw new ApiException(MarketDataErrorCode.INVALID_CANDLE_COUNT);
    }
}
