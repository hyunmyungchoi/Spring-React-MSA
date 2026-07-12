package com.springmsa.memberstockservice.market.controller;

import com.springmsa.memberstockservice.common.error.ApiExceptionHandler;
import com.springmsa.memberstockservice.market.domain.CandleInterval;
import com.springmsa.memberstockservice.market.dto.CandleResponse;
import com.springmsa.memberstockservice.market.dto.DataStatus;
import com.springmsa.memberstockservice.market.dto.MarketQuoteResponse;
import com.springmsa.memberstockservice.market.dto.StockSummaryResponse;
import com.springmsa.memberstockservice.market.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketDataControllerTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-07-12T10:15:30Z");

    private final MarketDataService service = mock(MarketDataService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MarketDataController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void pricesNormalizeSymbolsBeforeCallingService() throws Exception {
        when(service.getPrices(new LinkedHashSet<>(List.of("005930", "AAPL"))))
                .thenReturn(List.of(new MarketQuoteResponse(
                        "005930",
                        "72000",
                        "KRW",
                        "2026-07-12T19:15+09:00",
                        FETCHED_AT,
                        DataStatus.FRESH
                )));

        mockMvc.perform(get("/api/stock/market/prices")
                        .param("symbols", "005930,aapl,005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("005930"))
                .andExpect(jsonPath("$[0].lastPrice").value("72000"))
                .andExpect(jsonPath("$[0].dataStatus").value("FRESH"))
                .andExpect(jsonPath("$[0].fetchedAt").value("2026-07-12T10:15:30Z"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> symbolsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(service).getPrices(symbolsCaptor.capture());
        assertThat(symbolsCaptor.getValue()).containsExactly("005930", "AAPL");
    }

    @Test
    void stocksRejectInvalidSymbols() throws Exception {
        mockMvc.perform(get("/api/stock/market/stocks")
                        .param("symbols", "005930,INVALID_SYMBOL_TOO_LONG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MARKET_SYMBOL"));
    }

    @Test
    void stocksRejectTrailingEmptySymbolToken() throws Exception {
        mockMvc.perform(get("/api/stock/market/stocks")
                        .param("symbols", "AAPL,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MARKET_SYMBOL"));
    }

    @Test
    void stocksRejectMoreThanTwoHundredSymbols() throws Exception {
        StringBuilder symbols = new StringBuilder();
        for (int index = 0; index < 201; index++) {
            if (index > 0) {
                symbols.append(',');
            }
            symbols.append('A').append(index);
        }

        mockMvc.perform(get("/api/stock/market/stocks")
                        .param("symbols", symbols.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_MARKET_SYMBOLS"));
    }

    @Test
    void candlesValidateAndMapPathContract() throws Exception {
        when(service.getCandles("005930", CandleInterval.MINUTE_1, 100))
                .thenReturn(List.of(new CandleResponse(
                        "2026-07-12T09:00+09:00",
                        "71900",
                        "72300",
                        "71800",
                        "72100",
                        "2500",
                        "KRW",
                        FETCHED_AT,
                        DataStatus.FRESH
                )));

        mockMvc.perform(get("/api/stock/market/candles/{symbol}", "005930")
                        .param("interval", "1m")
                        .param("count", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].closePrice").value("72100"))
                .andExpect(jsonPath("$[0].dataStatus").value("FRESH"));

        verify(service).getCandles("005930", CandleInterval.MINUTE_1, 100);
    }

    @Test
    void candlesRejectInvalidInterval() throws Exception {
        mockMvc.perform(get("/api/stock/market/candles/{symbol}", "005930")
                        .param("interval", "5m")
                        .param("count", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CANDLE_INTERVAL"));
    }

    @Test
    void candlesRejectOutOfRangeCount() throws Exception {
        mockMvc.perform(get("/api/stock/market/candles/{symbol}", "005930")
                        .param("interval", "1m")
                        .param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CANDLE_COUNT"));
    }
}
