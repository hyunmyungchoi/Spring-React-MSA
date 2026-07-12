package com.springmsa.memberstockservice.market.error;

import com.springmsa.common.web.error.MsaErrorCode;
import org.springframework.http.HttpStatus;

public enum MarketDataErrorCode implements MsaErrorCode {
    INVALID_MARKET_SYMBOL(HttpStatus.BAD_REQUEST, "INVALID_MARKET_SYMBOL", "Invalid market symbol"),
    TOO_MANY_MARKET_SYMBOLS(HttpStatus.BAD_REQUEST, "TOO_MANY_MARKET_SYMBOLS", "Too many market symbols"),
    INVALID_CANDLE_INTERVAL(HttpStatus.BAD_REQUEST, "INVALID_CANDLE_INTERVAL", "Invalid candle interval"),
    INVALID_CANDLE_COUNT(HttpStatus.BAD_REQUEST, "INVALID_CANDLE_COUNT", "Invalid candle count");

    private final HttpStatus status;
    private final String code;
    private final String message;

    MarketDataErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
