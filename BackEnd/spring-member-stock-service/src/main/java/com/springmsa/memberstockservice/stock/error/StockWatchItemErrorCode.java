package com.springmsa.memberstockservice.stock.error;

import com.springmsa.common.web.error.MsaErrorCode;
import org.springframework.http.HttpStatus;

public enum StockWatchItemErrorCode implements MsaErrorCode {
    WATCH_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "WATCH_ITEM_NOT_FOUND", "Stock watch item not found"),
    WATCH_ITEM_DUPLICATE(HttpStatus.CONFLICT, "WATCH_ITEM_DUPLICATE", "Stock watch item already exists");

    private final HttpStatus status;
    private final String code;
    private final String message;

    StockWatchItemErrorCode(HttpStatus status, String code, String message) {
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
