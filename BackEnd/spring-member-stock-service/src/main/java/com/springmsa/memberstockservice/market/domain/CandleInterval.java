package com.springmsa.memberstockservice.market.domain;

public enum CandleInterval {
    MINUTE_1("1m"),
    DAY_1("1d");

    private final String apiValue;

    CandleInterval(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }
}
