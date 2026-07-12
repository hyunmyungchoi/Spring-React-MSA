package com.springmsa.memberstockservice.watchlist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(
        name = "stock_watch_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_watch_owner_symbol",
                columnNames = {"owner_sub", "symbol"}
        )
)
public class StockWatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_sub", nullable = false, length = 100)
    private String ownerSub;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String memo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockWatchItem() {
    }

    public static StockWatchItem create(String ownerSub, String symbol, String memo) {
        StockWatchItem item = new StockWatchItem();
        item.ownerSub = ownerSub;
        item.symbol = symbol.trim().toUpperCase(Locale.ROOT);
        item.memo = memo.trim();
        item.createdAt = Instant.now();
        item.updatedAt = item.createdAt;
        return item;
    }

    public Long getId() {
        return id;
    }

    public String getOwnerSub() {
        return ownerSub;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getMemo() {
        return memo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
